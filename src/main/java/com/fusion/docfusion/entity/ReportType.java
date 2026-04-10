package com.fusion.docfusion.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报表类型：描述业务上的报表类别，例如 合同收支汇总表、员工信息表 等
 */
@Data
public class ReportType {
    private Long id;
    /** 对外公开使用的报表类型ID（不可预测，防枚举） */
    private String publicId;
    private String name;
    private String description;
    private LocalDateTime createdAt;
}

