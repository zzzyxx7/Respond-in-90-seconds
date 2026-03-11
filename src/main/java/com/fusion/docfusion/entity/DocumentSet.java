package com.fusion.docfusion.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档集：比赛时一次性上传的一批文档（docx/md/xlsx/txt）
 */
@Data
public class DocumentSet {
    private Long id;
    /** 创建该文档集的用户ID，用于多用户隔离 */
    private Long ownerId;
    private String name;
    private LocalDateTime createdAt;
}
