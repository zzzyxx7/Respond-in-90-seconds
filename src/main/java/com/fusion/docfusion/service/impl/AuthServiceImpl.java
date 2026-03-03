package com.fusion.docfusion.service.impl;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.dto.LoginRequest;
import com.fusion.docfusion.dto.LoginResponse;
import com.fusion.docfusion.entity.User;
import com.fusion.docfusion.exception.BusinessException;
import com.fusion.docfusion.mapper.UserMapper;
import com.fusion.docfusion.service.AuthService;
import com.fusion.docfusion.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

