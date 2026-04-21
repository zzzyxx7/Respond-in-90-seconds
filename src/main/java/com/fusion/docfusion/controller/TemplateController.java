package com.fusion.docfusion.controller;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.dto.HistorySyncRequest;
import com.fusion.docfusion.dto.HistorySyncResultVO;
import com.fusion.docfusion.dto.TemplateProfileVO;
import com.fusion.docfusion.dto.TemplateVO;
import com.fusion.docfusion.service.TemplateService;
import com.fusion.docfusion.service.TemplateProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
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

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<TemplateVO> uploadTemplate(@RequestPart("file") MultipartFile file) {
        log.info("上传模板请求, filename={}, size={}", file == null ? null : file.getOriginalFilename(), file == null ? null : file.getSize());
        return templateService.uploadTemplate(file);
    }

    /**
     * 登录后批量同步“匿名历史模板”到当前账号。
     * POST /api/templates/sync
     */
    @PostMapping("/sync")
    public Result<HistorySyncResultVO> syncTemplateHistory(@RequestBody(required = false) HistorySyncRequest request) {
        int count = request == null || request.getPublicIds() == null ? 0 : request.getPublicIds().size();
        log.info("同步模板历史, count={}", count);
        return templateService.syncTemplateHistory(request);
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

    /**
     * 按 publicId 查询模板详情（用于防枚举/匿名场景）。
     * GET /api/templates/public/{templatePublicId}
     */
    @GetMapping("/public/{templatePublicId}")
    public Result<TemplateVO> getByPublicId(@PathVariable String templatePublicId) {
        log.info("查询模板详情(公共ID), templatePublicId={}", templatePublicId);
        return templateService.getByPublicId(templatePublicId);
    }

    /**
     * 下载模板原始文件（用于前端预览等；权限与详情接口一致）。
     * GET /api/templates/download/public/{templatePublicId}
     */
    @GetMapping("/download/public/{templatePublicId}")
    public void downloadTemplateByPublicId(@PathVariable String templatePublicId, HttpServletResponse response) {
        log.info("下载模板文件(公共ID), templatePublicId={}", templatePublicId);
        templateService.writeTemplateFileByPublicId(templatePublicId, response);
    }

    /**
     * 更新模板基本信息（目前支持：展示名、所属报表类型）
     * PUT /api/templates/public/{templatePublicId}
     */
    @PutMapping("/public/{templatePublicId}")
    public Result<TemplateVO> updateTemplateByPublicId(@PathVariable String templatePublicId,
                                                         @RequestBody TemplateVO vo) {
        log.info("更新模板(公共ID), templatePublicId={}", templatePublicId);
        TemplateVO tpl = templateService.getByPublicId(templatePublicId).getData();
        if (tpl == null || tpl.getId() == null) {
            return templateService.getByPublicId(templatePublicId);
        }
        return templateService.updateTemplate(tpl.getId(), vo);
    }

    /**
     * 删除模板
     * DELETE /api/templates/public/{templatePublicId}
     */
    @DeleteMapping("/public/{templatePublicId}")
    public Result<Boolean> deleteTemplateByPublicId(@PathVariable String templatePublicId) {
        log.info("删除模板(公共ID), templatePublicId={}", templatePublicId);
        TemplateVO tpl = templateService.getByPublicId(templatePublicId).getData();
        if (tpl == null || tpl.getId() == null) {
            return Result.success(false);
        }
        return templateService.deleteTemplate(tpl.getId());
    }

    /**
     * 保存或更新模板档案配置
     * POST /api/templates/public/{templatePublicId}/profile
     */
    @PostMapping("/public/{templatePublicId}/profile")
    public Result<TemplateProfileVO> saveProfileByPublicId(@PathVariable String templatePublicId,
                                                           @RequestBody TemplateProfileVO vo) {
        log.info("保存模板档案配置(公共ID), templatePublicId={}", templatePublicId);
        TemplateVO tpl = templateService.getByPublicId(templatePublicId).getData();
        if (tpl == null || tpl.getId() == null) {
            return Result.success(null);
        }
        if (vo == null) {
            vo = new TemplateProfileVO();
        }
        vo.setTemplateId(tpl.getId());
        return templateProfileService.saveOrUpdate(vo);
    }

    /**
     * 查询模板档案配置
     * GET /api/templates/public/{templatePublicId}/profile
     */
    @GetMapping("/public/{templatePublicId}/profile")
    public Result<TemplateProfileVO> getProfileByPublicId(@PathVariable String templatePublicId) {
        log.info("查询模板档案配置(公共ID), templatePublicId={}", templatePublicId);
        TemplateVO tpl = templateService.getByPublicId(templatePublicId).getData();
        if (tpl == null || tpl.getId() == null) {
            return Result.success(null);
        }
        return templateProfileService.getByTemplateId(tpl.getId());
    }
}
