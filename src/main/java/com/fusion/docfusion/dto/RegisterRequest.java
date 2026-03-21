package com.fusion.docfusion.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 注册请求：创建普通用户（role 默认 USER）。
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;
}

