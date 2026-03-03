package com.fusion.docfusion.service;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.dto.TemplateProfileVO;

public interface TemplateProfileService {

    /**
     * 创建或更新某个模板的档案配置。
     */
    Result<TemplateProfileVO> saveOrUpdate(TemplateProfileVO vo);

    /**
     * 查询某个模板的档案配置。
     */
    Result<TemplateProfileVO> getByTemplateId(Long templateId);
}

