package com.fusion.docfusion.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档集列表项（用于创建任务时选择文档集）
 */
@Data
public class DocumentSetListItemVO {
    private Long id;
    private String name;
    private LocalDateTime createdAt;
    private Integer documentCount;
}
