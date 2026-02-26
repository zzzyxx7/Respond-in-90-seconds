package com.fusion.docfusion.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FillRequest {
    /** 当前使用的文档集 ID（上传文档后返回） */
    @NotNull(message = "文档集ID不能为空")
    private Long documentSetId;
    /** 要填写的模板 ID */
    @NotNull(message = "模板ID不能为空")
    private Long templateId;
}
