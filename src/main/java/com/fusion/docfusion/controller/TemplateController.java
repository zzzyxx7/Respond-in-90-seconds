package com.fusion.docfusion.controller;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.dto.TemplateProfileVO;
import com.fusion.docfusion.dto.TemplateVO;
import com.fusion.docfusion.service.TemplateService;
import com.fusion.docfusion.service.TemplateProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 模板上传与列表（word / excel）
 */
@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
@Slf4j
public class TemplateController {

    private final TemplateService templateService;
    private final TemplateProfileService templateProfileService;

    @PostMapping("/upload")
    public Result<TemplateVO> uploadTemplate(@RequestParam("file") MultipartFile file) {
        log.info("上传模板请求, filename={}, size={}", file == null ? null : file.getOriginalFilename(), file == null ? null : file.getSize());
        return templateService.uploadTemplate(file);
    }

    @GetMapping("/list")
    public Result<List<TemplateVO>> listTemplates() {
        log.info("查询模板列表");
        return templateService.listTemplates();
    }

    /**
     * 按报表类型查询模板列表
     * GET /api/templates/by-report-type?reportTypeId=1
     */
    @GetMapping("/by-report-type")
    public Result<List<TemplateVO>> listByReportType(@RequestParam("reportTypeId") Long reportTypeId) {
        log.info("按报表类型查询模板列表, reportTypeId={}", reportTypeId);
        return templateService.listByReportType(reportTypeId);
    }

    @GetMapping("/{templateId}")
    public Result<TemplateVO> getById(@PathVariable Long templateId) {
        log.info("查询模板详情, templateId={}", templateId);
        return templateService.getById(templateId);
    }

    /**
     * 保存或更新模板档案配置（如 report_profile.json）
     * POST /api/templates/{templateId}/profile
     */
    @PostMapping("/{templateId}/profile")
    public Result<TemplateProfileVO> saveProfile(@PathVariable Long templateId,
                                                 @RequestBody TemplateProfileVO vo) {
        log.info("保存模板档案配置, templateId={}", templateId);
        if (vo == null) {
            vo = new TemplateProfileVO();
        }
        vo.setTemplateId(templateId);
        return templateProfileService.saveOrUpdate(vo);
    }

    /**
     * 查询模板档案配置
     * GET /api/templates/{templateId}/profile
     */
    @GetMapping("/{templateId}/profile")
    public Result<TemplateProfileVO> getProfile(@PathVariable Long templateId) {
        log.info("查询模板档案配置, templateId={}", templateId);
        return templateProfileService.getByTemplateId(templateId);
    }
}
