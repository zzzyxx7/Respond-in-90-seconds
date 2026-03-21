package com.fusion.docfusion.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class FillTaskVO {
    private Long id;
    private Long userId;
    private Long documentSetId;
    private Long templateId;
    /** TEMPLATE（模板模式）/ FREE（自由模式） */
    private String mode;
    /** 自由模式下的用户需求描述 */
    private String userRequirement;
    private String status;
    private String resultFilePath;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
    /** 任务失败时的错误信息 */
    private String errorMessage;

    /**
     * 任务链路步骤列表（按执行顺序）。
     */
    private List<FillTaskStepVO> steps;

    /**
     * 任务总耗时（毫秒）。RUNNING 时可能为空或持续增长（取决于是否有 finishedAt）。
     */
    private Long totalDurationMs;
}
