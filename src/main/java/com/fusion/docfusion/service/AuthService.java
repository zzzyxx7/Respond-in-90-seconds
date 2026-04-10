package com.fusion.docfusion.service;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.dto.ChangePasswordRequest;
import com.fusion.docfusion.dto.LoginRequest;
import com.fusion.docfusion.dto.LoginResponse;
import com.fusion.docfusion.dto.RegisterRequest;
import com.fusion.docfusion.dto.UpdateProfileRequest;
import com.fusion.docfusion.dto.UserProfileVO;
import org.springframework.web.multipart.MultipartFile;

public interface AuthService {

    Result<LoginResponse> login(LoginRequest request);

    /**
     * 注册普通用户（role 默认为 USER）。
     */
    Result<LoginResponse> register(RegisterRequest request);

    /** 当前登录用户资料（不含密码）。 */
    Result<UserProfileVO> me();

    /**
     * 退出登录：JWT 无服务端会话，成功响应后请客户端删除本地 token；
     * 已签发的 token 在过期前仍可能被使用（若需立即使其失效需配合黑名单，当前未实现）。
     */
    Result<String> logout();

    Result<UserProfileVO> changePassword(ChangePasswordRequest request);

    /**
     * 修改个人信息（用户名/头像等非敏感字段），统一入口。
     */
    Result<UserProfileVO> updateProfile(UpdateProfileRequest request);

    /**
     * 上传头像文件，成功后会更新当前用户 avatarUrl 并返回最新资料。
     */
    Result<UserProfileVO> uploadAvatar(MultipartFile file);
}

