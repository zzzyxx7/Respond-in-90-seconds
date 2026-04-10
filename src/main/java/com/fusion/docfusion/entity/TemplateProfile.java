package com.fusion.docfusion.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模板档案：存放每个模板的配置档案（通常为 report_profile.json）
 */
@Data
public class TemplateProfile {
    private Long id;
    /** 对外公开使用的模板档案ID（不可预测，防枚举） */
    private String publicId;
    private Long templateId;
    /**
     * 模板档案内容，通常为 JSON 字符串
     */
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

