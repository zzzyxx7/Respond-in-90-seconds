package com.fusion.docfusion.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 填表模板：word 或 excel，每次填表任务对应一个模板
 */
@Data
public class Template {
    private Long id;
    private String fileName;
    /** word / excel */
    private String fileType;
    private String filePath;
    private LocalDateTime createdAt;
}
