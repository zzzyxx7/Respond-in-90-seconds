package com.fusion.docfusion.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单个文档：属于某个文档集，存储路径与类型
 */
@Data
public class Document {
    private Long id;
    private Long documentSetId;
    /** 文件名，如 report.docx */
    private String fileName;
    /** 类型：docx, md, xlsx, txt */
    private String fileType;
    /** 相对或绝对存储路径 */
    private String filePath;
    /** 文件大小（字节） */
    private Long fileSize;
    private LocalDateTime createdAt;
}
