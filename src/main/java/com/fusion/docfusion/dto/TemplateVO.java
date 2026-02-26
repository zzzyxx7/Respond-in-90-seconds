package com.fusion.docfusion.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TemplateVO {
    private Long id;
    private String fileName;
    private String fileType;
    private LocalDateTime createdAt;
}
