package com.fusion.docfusion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangeUsernameRequest {

    @NotBlank(message = "新用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度应在 3~50 之间")
    private String newUsername;
}
