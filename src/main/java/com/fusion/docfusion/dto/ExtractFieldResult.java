package com.fusion.docfusion.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 单个字段的抽取结果：值 + 置信度
 */
@Data
public class ExtractFieldResult {
    /**
     * 抽取到的字段值（字符串形式）
     */
    private String value;

    /**
     * 置信度 0~1，例如 0.9234
     */
    private BigDecimal confidence;
}

