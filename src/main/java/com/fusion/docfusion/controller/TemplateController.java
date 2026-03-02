package com.fusion.docfusion.controller;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.dto.TemplateVO;
import com.fusion.docfusion.service.TemplateService;
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

    @GetMapping("/{templateId}")
    public Result<TemplateVO> getById(@PathVariable Long templateId) {
        log.info("查询模板详情, templateId={}", templateId);
        return templateService.getById(templateId);
    }
}
