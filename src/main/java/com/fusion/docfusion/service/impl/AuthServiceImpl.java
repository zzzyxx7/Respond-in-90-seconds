package com.fusion.docfusion.service.impl;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.dto.LoginRequest;
import com.fusion.docfusion.dto.LoginResponse;
import com.fusion.docfusion.dto.RegisterRequest;
import com.fusion.docfusion.entity.User;
import com.fusion.docfusion.exception.BusinessException;
import com.fusion.docfusion.mapper.UserMapper;
import com.fusion.docfusion.service.AuthService;
import com.fusion.docfusion.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

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
            throw new BusinessException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        LoginResponse resp = new LoginResponse();
        resp.setToken(token);
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setRole(user.getRole());
        return Result.success(resp);
    }

    @Override
    public Result<LoginResponse> register(RegisterRequest request) {
        String username = request.getUsername() == null ? null : request.getUsername().trim();
        if (username == null || username.isBlank()) {
            throw new BusinessException("用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BusinessException("密码不能为空");
        }
        if (username.length() < 3 || username.length() > 50) {
            throw new BusinessException(400, "用户名长度应在 3~50 之间");
        }
        if (request.getPassword().length() < 6 || request.getPassword().length() > 100) {
            throw new BusinessException(400, "密码长度应在 6~100 之间");
        }

        User existed = userMapper.selectByUsername(username);
        if (existed != null) {
            throw new BusinessException(400, "用户名已存在");
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
        resp.setRole(u.getRole());
        return Result.success(resp);
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

