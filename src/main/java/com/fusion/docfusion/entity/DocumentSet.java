package com.fusion.docfusion.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档集：比赛时一次性上传的一批文档（docx/md/xlsx/txt）
 */
@Data
public class DocumentSet {
    private Long id;
    private String name;
    private LocalDateTime createdAt;
}
