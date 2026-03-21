package com.fusion.docfusion.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务步骤展示对象：用于前端展示任务链路/进度。
 */
@Data
public class FillTaskStepVO {
    private String stepCode;
    private String stepName;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private String message;
    private String errorMessage;
}

