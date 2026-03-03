package com.fusion.docfusion.dto;

import lombok.Data;

import java.time.LocalDateTime;

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
}
