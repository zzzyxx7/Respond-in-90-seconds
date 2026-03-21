package com.fusion.docfusion.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务步骤：用于展示任务链路与耗时。
 */
@Data
public class FillTaskStep {
    private Long id;
    private Long taskId;
    /** 步骤编码：RAG/EXTRACT/FILL/GENERATE 等 */
    private String stepCode;
    /** 步骤名称（展示用） */
    private String stepName;
    /** PENDING/RUNNING/SUCCESS/FAILED/SKIPPED */
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private String message;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

