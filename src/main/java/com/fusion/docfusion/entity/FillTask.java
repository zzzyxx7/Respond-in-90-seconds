package com.fusion.docfusion.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 填表任务：用当前文档集的数据填写指定模板，生成结果文件
 * 比赛要求：单次响应 ≤90 秒，准确率 ≥80%
 */
@Data
public class FillTask {
    private Long id;
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
}
