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
    /**
     * 用户的补充需求（可选），例如：
     * "这次只汇总近三个月的合同"、"金额按万元展示"
     */
    private String userRequirement;
}
