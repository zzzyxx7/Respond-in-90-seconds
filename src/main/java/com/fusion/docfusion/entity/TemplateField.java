package com.fusion.docfusion.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模板字段：描述模板中某个位置要填充哪个字段
 */
@Data
public class TemplateField {

    private Long id;
    /** 对外公开使用的模板字段映射ID（不可预测，防枚举） */
    private String publicId;

    private Long templateId;

    private Long fieldSchemaId;

    /**
     * 模板中的位置，例如 A3 / B5 或 {{student_name}}
     */
    private String location;

    /**
     * 展示格式（可选），如金额两位小数、日期格式等
     */
    private String format;

    private LocalDateTime createdAt;
}

