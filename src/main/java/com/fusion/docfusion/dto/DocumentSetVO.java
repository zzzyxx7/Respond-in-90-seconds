package com.fusion.docfusion.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class DocumentSetVO {
    private Long id;
    private String name;
    private LocalDateTime createdAt;
    private List<DocumentVO> documents;
}
