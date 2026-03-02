package com.fusion.docfusion.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReportTypeVO {
    private Long id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
}

