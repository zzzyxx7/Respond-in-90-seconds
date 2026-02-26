package com.fusion.docfusion.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FillTaskVO {
    private Long id;
    private Long documentSetId;
    private Long templateId;
    private String status;
    private String resultFilePath;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
}
