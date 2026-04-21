package com.fusion.docfusion.service;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.dto.HistorySyncRequest;
import com.fusion.docfusion.dto.HistorySyncResultVO;
import com.fusion.docfusion.dto.TemplateVO;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * 模板：上传 word/excel 模板，落盘并入库
 */
public interface TemplateService {

    Result<TemplateVO> uploadTemplate(MultipartFile file);

    Result<List<TemplateVO>> listTemplates();

    Result<List<TemplateVO>> listByReportType(Long reportTypeId);

    Result<TemplateVO> getByPublicId(String templatePublicId);

    Result<TemplateVO> updateTemplate(Long templateId, TemplateVO vo);

    Result<Boolean> deleteTemplate(Long templateId);

    /**
     * 登录后将匿名历史模板（本地保存的 publicId）批量同步到当前账号。
     */
    Result<HistorySyncResultVO> syncTemplateHistory(HistorySyncRequest request);

    /**
     * 下载模板原始文件（权限与 {@link #getByPublicId(String)} 一致）。
     */
    void writeTemplateFileByPublicId(String templatePublicId, HttpServletResponse response);
}
