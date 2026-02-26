package com.fusion.docfusion.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字段定义：描述系统里可以抽取的字段（通用字段字典）
 */
@Data
public class FieldSchema {

    private Long id;

    /**
     * 字段编码，例如 student_name, amount
     */
    private String code;

    /**
     * 字段中文名，例如 学生姓名、金额
     */
    private String displayName;

    /**
     * 数据类型：string / number / date 等
     */
    private String dataType;

    /**
     * 字段说明
     */
    private String description;

    /**
     * 是否启用：1-启用，0-停用
     */
    private Boolean enabled;

    private LocalDateTime createdAt;
}

