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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        log.info("user login, username={}", request.getUsername());
        return authService.login(request);
    }

    @PostMapping("/register")
    public Result<LoginResponse> register(@RequestBody @Valid RegisterRequest request) {
        log.info("user register, username={}", request.getUsername());
        return authService.register(request);
    }

    @GetMapping("/me")
    public Result<UserProfileVO> me() {
        return authService.me();
    }

    @GetMapping("/profile")
    public Result<UserProfileVO> profile() {
        return authService.me();
    }

    @PostMapping("/logout")
    public Result<String> logout() {
        return authService.logout();
    }

    @PutMapping("/password")
    public Result<UserProfileVO> changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        return authService.changePassword(request);
    }

    @PutMapping("/profile")
    public Result<UserProfileVO> updateProfile(@RequestBody @Valid UpdateProfileRequest request) {
        return authService.updateProfile(request);
    }

    @PostMapping(value = "/avatar/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UserProfileVO> uploadAvatar(@RequestPart("file") MultipartFile file) {
        return authService.uploadAvatar(file);
    }
}
