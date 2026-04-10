package com.fusion.docfusion.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改个人信息（非敏感字段）。
 * 密码修改仍走单独接口。
 */
@Data
public class UpdateProfileRequest {

    /** 可选：新用户名（3~50），全表唯一 */
    @Size(min = 3, max = 50, message = "用户名长度应在 3~50 之间")
    private String username;

    /** 可选：头像 URL（一般由 /api/auth/avatar/upload 返回） */
    @Size(max = 512, message = "头像URL过长")
    private String avatarUrl;
}

