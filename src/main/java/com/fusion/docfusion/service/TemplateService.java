package com.fusion.docfusion.service;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.dto.TemplateVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 模板：上传 word/excel 模板，落盘并入库
 */
public interface TemplateService {

    Result<TemplateVO> uploadTemplate(MultipartFile file);

    Result<List<TemplateVO>> listTemplates();

    Result<TemplateVO> getById(Long templateId);
}
