package com.fusion.docfusion.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 填表任务：用当前文档集的数据填写指定模板，生成结果文件
 * 比赛要求：单次响应 ≤90 秒，准确率 ≥80%
 */
@Data
public class FillTask {
    private Long id;
    /** 对外公开使用的任务ID（不可预测，防枚举） */
    private String publicId;
    /** 创建任务的用户ID，可为空（未登录） */
    private Long userId;
    private Long documentSetId;
    private Long templateId;
    /**
     * 任务模式：TEMPLATE（模板模式）/ FREE（自由模式）
     */
    private String mode;
    /**
     * 自由模式下的用户需求描述
     */
    private String userRequirement;
    /** PENDING, RUNNING, SUCCESS, FAILED */
    private String status;
    /** 填写结果文件路径 */
    private String resultFilePath;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
    /**
     * 异常信息（如任务失败时记录原因）
     */
    private String errorMessage;
    /**
     * 乐观锁版本号（每次更新 +1）。
     */
    private Long version;

    /** AI 任务ID（远端），用于审计与排查 */
    private String aiRemoteTaskId;
    /** AI 供应商标识（如 openai / qwen / deepseek） */
    private String aiProvider;
    /** 实际调用模型名（如 gpt-4.1-mini） */
    private String aiModel;
    /** 输入 token 数 */
    private Long inputTokens;
    /** 输出 token 数 */
    private Long outputTokens;
    /** 总 token 数 */
    private Long totalTokens;
    /** 本次调用成本（按 aiCostCurrency） */
    private BigDecimal aiCost;
    /** 成本币种（默认 USD） */
    private String aiCostCurrency;
    /** true 表示由后端按单价估算；false 表示供应商原始返回 */
    private Boolean aiCostEstimated;
    /** 供应商返回的 usage 原始 JSON（便于审计） */
    private String aiUsageRaw;
}
