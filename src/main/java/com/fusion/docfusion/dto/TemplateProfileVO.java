package com.fusion.docfusion.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TemplateProfileVO {
    private Long templateId;
    /**
     * 模板档案内容，通常为 JSON 字符串（report_profile.json）
     */
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

