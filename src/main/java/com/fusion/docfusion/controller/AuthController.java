package com.fusion.docfusion.controller;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.dto.ChangePasswordRequest;
import com.fusion.docfusion.dto.LoginRequest;
import com.fusion.docfusion.dto.LoginResponse;
import com.fusion.docfusion.dto.RegisterRequest;
import com.fusion.docfusion.dto.UpdateProfileRequest;
import com.fusion.docfusion.dto.UserProfileVO;
import com.fusion.docfusion.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

/**
 * 认证相关：登录、注册、当前用户、退出、修改用户名/密码
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        log.info("用户登录, username={}", request.getUsername());
        return authService.login(request);
    }

    /**
     * 注册普通用户，成功后直接返回 token（便于联调）。
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public Result<LoginResponse> register(@RequestBody @Valid RegisterRequest request) {
        log.info("用户注册, username={}", request.getUsername());
        return authService.register(request);
    }

    /**
     * 当前登录用户资料（需 Header：Authorization: Bearer 后接 JWT）
     * GET /api/auth/me
     */
    @GetMapping("/me")
    public Result<UserProfileVO> me() {
        return authService.me();
    }

    /**
     * 退出登录：无服务端会话，请客户端删除本地 token。
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public Result<String> logout() {
        return authService.logout();
    }

    /**
     * 修改密码
     * PUT /api/auth/password
     */
    @PutMapping("/password")
    public Result<UserProfileVO> changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        return authService.changePassword(request);
    }

    /**
     * 修改个人信息（统一入口）
     * PUT /api/auth/profile
     */
    @PutMapping("/profile")
    public Result<UserProfileVO> updateProfile(@RequestBody @Valid UpdateProfileRequest request) {
        return authService.updateProfile(request);
    }

    /**
     * 上传头像（multipart/form-data）
     * POST /api/auth/avatar/upload
     */
    @PostMapping(value = "/avatar/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UserProfileVO> uploadAvatar(@RequestPart("file") MultipartFile file) {
        return authService.uploadAvatar(file);
    }
}

