package com.fusion.docfusion.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 自由模式填表请求：不依赖模板，只需要文档集 + 用户需求
 */
@Data
public class FreeFillRequest {

    /** 当前使用的文档集 ID（兼容旧参数）。建议优先使用 documentSetPublicId。 */
    private Long documentSetId;
    /** 当前使用的文档集 publicId（推荐，防枚举）。 */
    private String documentSetPublicId;

    /**
     * 用户的自然语言需求描述，例如：
     * "按合同，把合同名称、金额、签订日期汇总成一个表"
     */
    @NotBlank(message = "用户需求不能为空")
    private String userRequirement;
}

