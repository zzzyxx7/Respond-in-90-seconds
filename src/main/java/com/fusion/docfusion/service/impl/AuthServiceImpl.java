package com.fusion.docfusion.service.impl;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.config.UploadProperties;
import com.fusion.docfusion.dto.ChangePasswordRequest;
import com.fusion.docfusion.dto.LoginRequest;
import com.fusion.docfusion.dto.LoginResponse;
import com.fusion.docfusion.dto.RegisterRequest;
import com.fusion.docfusion.dto.UpdateProfileRequest;
import com.fusion.docfusion.dto.UserProfileVO;
import com.fusion.docfusion.entity.User;
import com.fusion.docfusion.exception.BusinessException;
import com.fusion.docfusion.exception.ErrorCode;
import com.fusion.docfusion.mapper.UserMapper;
import com.fusion.docfusion.security.SecurityUtils;
import com.fusion.docfusion.service.AuthService;
import com.fusion.docfusion.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final UploadProperties uploadProperties;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private static final List<String> ALLOWED_AVATAR_EXT = List.of("png", "jpg", "jpeg", "gif", "webp");
    private static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024;

    /**
     * 启动时兜底创建一个默认管理员账号，便于本地联调/比赛演示。
     */
    @PostConstruct
    public void initDefaultAdmin() {
        ensureDefaultAdmin();
    }

    @Override
    public Result<LoginResponse> login(LoginRequest request) {
        User user = userMapper.selectByUsername(request.getUsername());
        if (user == null) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setAvatarUrl(user.getAvatarUrl());
        resp.setRole(user.getRole());
        return Result.success(resp);
    }

    @Override
    public Result<LoginResponse> register(RegisterRequest request) {
        String username = request.getUsername() == null ? null : request.getUsername().trim();
        if (username == null || username.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_USERNAME_EMPTY);
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_EMPTY);
        }
        if (username.length() < 3 || username.length() > 50) {
            throw new BusinessException(ErrorCode.AUTH_USERNAME_LENGTH_INVALID);
        }
        if (request.getPassword().length() < 6 || request.getPassword().length() > 100) {
            throw new BusinessException(ErrorCode.AUTH_PASSWORD_LENGTH_INVALID);
        }

        User existed = userMapper.selectByUsername(username);
        if (existed != null) {
            throw new BusinessException(ErrorCode.AUTH_USERNAME_EXISTS);
        }

        User u = new User();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(request.getPassword()));
        u.setRole("USER");
        u.setCreatedAt(LocalDateTime.now());
        userMapper.insert(u);

        // 注册成功后直接返回 token（更利于 Apifox 联调）
        String token = jwtUtil.generateToken(u.getId(), u.getUsername(), u.getRole());
        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setUserId(u.getId());
        resp.setUsername(u.getUsername());
        resp.setAvatarUrl(u.getAvatarUrl());
        resp.setRole(u.getRole());
        return Result.success(resp);
    }

    @Override
    public Result<UserProfileVO> me() {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_REQUIRED);
        }
        User user = userMapper.selectById(uid);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在或已删除");
        }
        return Result.success(toProfileVO(user));
    }

    @Override
    public Result<String> logout() {
        if (SecurityUtils.currentUserId() == null) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_REQUIRED);
        }
        return Result.success("请在客户端清除本地保存的 token；服务端不保留会话。");
    }

    @Override
    public Result<UserProfileVO> changePassword(ChangePasswordRequest request) {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_REQUIRED);
        }
        User user = userMapper.selectById(uid);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在或已删除");
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.AUTH_OLD_PASSWORD_WRONG);
        }
        if (request.getNewPassword().equals(request.getOldPassword())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新密码不能与原密码相同");
        }
        userMapper.updatePasswordById(uid, passwordEncoder.encode(request.getNewPassword()));
        User fresh = userMapper.selectById(uid);
        return Result.success(toProfileVO(fresh));
    }

    @Override
    public Result<UserProfileVO> updateProfile(UpdateProfileRequest request) {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_REQUIRED);
        }
        User user = userMapper.selectById(uid);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在或已删除");
        }
        if (request != null && request.getUsername() != null) {
            String newName = request.getUsername().trim();
            if (newName.isBlank()) {
                throw new BusinessException(ErrorCode.AUTH_USERNAME_EMPTY);
            }
            if (newName.length() < 3 || newName.length() > 50) {
                throw new BusinessException(ErrorCode.AUTH_USERNAME_LENGTH_INVALID);
            }
            if (!newName.equals(user.getUsername())) {
                User existed = userMapper.selectByUsername(newName);
                if (existed != null && !existed.getId().equals(uid)) {
                    throw new BusinessException(ErrorCode.AUTH_USERNAME_EXISTS);
                }
                userMapper.updateUsernameById(uid, newName);
            }
        }
        if (request != null && request.getAvatarUrl() != null) {
            String url = request.getAvatarUrl().trim();
            if (url.isBlank()) {
                url = null;
            }
            userMapper.updateAvatarUrlById(uid, url);
        }
        User fresh = userMapper.selectById(uid);
        return Result.success(toProfileVO(fresh));
    }

    @Override
    public Result<UserProfileVO> uploadAvatar(MultipartFile file) {
        Long uid = SecurityUtils.currentUserId();
        if (uid == null) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_REQUIRED);
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择头像文件");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "头像文件过大，请控制在 5MB 以内");
        }

        String originalFilename = file.getOriginalFilename();
        String ext = getExtension(originalFilename);
        if (ext == null || ext.isBlank() || !ALLOWED_AVATAR_EXT.contains(ext.toLowerCase())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持 png/jpg/jpeg/gif/webp");
        }

        Path basePath = Paths.get(uploadProperties.getAvatarsDir());
        try {
            Files.createDirectories(basePath);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "创建头像目录失败: " + e.getMessage());
        }

        String savedName = UUID.randomUUID() + "." + ext.toLowerCase();
        Path normalizedBase = basePath.normalize();
        Path target = basePath.resolve(savedName).normalize();
        if (!target.startsWith(normalizedBase)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "非法文件名");
        }

        try {
            file.transferTo(target.toFile());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存头像失败: " + e.getMessage());
        }

        String avatarUrl = "/api/files/avatars/" + savedName;
        userMapper.updateAvatarUrlById(uid, avatarUrl);
        User fresh = userMapper.selectById(uid);
        return Result.success(toProfileVO(fresh));
    }

    private static UserProfileVO toProfileVO(User u) {
        UserProfileVO vo = new UserProfileVO();
        vo.setId(u.getId());
        vo.setUsername(u.getUsername());
        vo.setAvatarUrl(u.getAvatarUrl());
        vo.setRole(u.getRole());
        vo.setCreatedAt(u.getCreatedAt());
        return vo;
    }

    private static String getExtension(String filename) {
        if (filename == null) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        String ext = filename.substring(dot + 1);
        ext = ext.replaceAll("[^a-zA-Z0-9]", "");
        return ext.isBlank() ? null : ext;
    }

    /**
     * 可选：初始化一个默认管理员账号（如不存在时）
     */
    public void ensureDefaultAdmin() {
        User admin = userMapper.selectByUsername("admin");
        if (admin == null) {
            User u = new User();
            u.setUsername("admin");
            u.setPassword(passwordEncoder.encode("admin123"));
            u.setRole("ADMIN");
            u.setCreatedAt(LocalDateTime.now());
            userMapper.insert(u);
            log.info("已创建默认管理员账号 admin/admin123，请尽快修改密码");
        }
    }
}
