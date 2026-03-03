package com.fusion.docfusion.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TemplateVO {
    private Long id;
    /** 所属报表类型ID，可为空 */
    private Long reportTypeId;
    private String fileName;
    private String fileType;
    private LocalDateTime createdAt;
}
