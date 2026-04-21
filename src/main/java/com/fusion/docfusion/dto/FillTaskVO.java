package com.fusion.docfusion.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class FillTaskVO {
    private Long id;
    /** 对外公开任务ID（不可预测） */
    private String publicId;
    private Long userId;
    private Long documentSetId;
    private Long templateId;
    /** TEMPLATE（模板模式）/ FREE（自由模式） */
    private String mode;
    /** 自由模式下的用户需求描述 */
    private String userRequirement;
    private String status;
    private String resultFilePath;
    /** 结果文件类型（excel/docx/json/unknown） */
    private String resultFileType;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
    /** 任务失败时的错误信息 */
    private String errorMessage;
    /** 更细粒度的失败阶段（例如 FILL / EXTRACT） */
    private String failureStage;
    /** 失败原因类型（例如 TIMEOUT / AI_PROCESS_EXIT） */
    private String failureReasonCode;
    /** 可读的失败建议（前端可直接展示） */
    private String failureSuggestion;
    /** 当前状态下允许的动作（如 MANUAL_RERUN / DOWNLOAD） */
    private List<String> allowedActions;

    /**
     * 任务链路步骤列表（按执行顺序）。
     */
    private List<FillTaskStepVO> steps;

    /**
     * 任务总耗时（毫秒）。RUNNING 时可能为空或持续增长（取决于是否有 finishedAt）。
     */
    private Long totalDurationMs;

    /** 远端 AI 任务ID（审计定位） */
    private String aiRemoteTaskId;
    /** AI 供应商 */
    private String aiProvider;
    /** AI 模型 */
    private String aiModel;
    /** 输入 token */
    private Long inputTokens;
    /** 输出 token */
    private Long outputTokens;
    /** 总 token */
    private Long totalTokens;
    /** 成本 */
    private BigDecimal aiCost;
    /** 成本币种 */
    private String aiCostCurrency;
    /** 是否估算成本 */
    private Boolean aiCostEstimated;
}
