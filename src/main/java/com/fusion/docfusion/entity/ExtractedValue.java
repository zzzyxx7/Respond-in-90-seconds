package com.fusion.docfusion.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 抽取结果：某个文档中抽取出的某个字段的值
 */
@Data
public class ExtractedValue {

    private Long id;

    private Long documentId;

    private Long fieldSchemaId;

    /**
     * 抽取到的字段值（字符串形式）
     */
    private String fieldValue;

    /**
     * 置信度 0~1，例如 0.9234
     */
    private BigDecimal confidence;

    private LocalDateTime createdAt;
}

