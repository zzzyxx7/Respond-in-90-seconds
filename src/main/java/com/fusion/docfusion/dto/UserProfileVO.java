package com.fusion.docfusion.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 当前用户对外展示信息（不含密码）。
 */
@Data
public class UserProfileVO {
    private Long id;
    private String username;
    private String avatarUrl;
    private String role;
    private LocalDateTime createdAt;
}
