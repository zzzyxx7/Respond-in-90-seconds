package com.fusion.docfusion.dto;

import lombok.Data;

@Data
public class FillRequest {
    /** 当前使用的文档集 ID（兼容旧参数）。建议优先使用 documentSetPublicId。 */
    private Long documentSetId;
    /** 当前使用的文档集 publicId（推荐，防枚举）。 */
    private String documentSetPublicId;
    /** 要填写的模板 ID（兼容旧参数）。建议优先使用 templatePublicId。 */
    private Long templateId;
    /** 要填写的模板 publicId（推荐，防枚举）。 */
    private String templatePublicId;
    /**
     * 用户的补充需求（可选），例如：
     * "这次只汇总近三个月的合同"、"金额按万元展示"
     */
    private String userRequirement;
}
