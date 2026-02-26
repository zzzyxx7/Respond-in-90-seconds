package com.fusion.docfusion.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocumentVO {
    private Long id;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private LocalDateTime createdAt;
}
