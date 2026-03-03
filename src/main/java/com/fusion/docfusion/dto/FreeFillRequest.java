package com.fusion.docfusion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 自由模式填表请求：不依赖模板，只需要文档集 + 用户需求
 */
@Data
public class FreeFillRequest {

    /** 当前使用的文档集 ID（上传文档后返回） */
    @NotNull(message = "文档集ID不能为空")
    private Long documentSetId;

    /**
     * 用户的自然语言需求描述，例如：
     * "按合同，把合同名称、金额、签订日期汇总成一个表"
     */
    @NotBlank(message = "用户需求不能为空")
    private String userRequirement;
}

