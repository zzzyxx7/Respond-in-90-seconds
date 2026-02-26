package com.fusion.docfusion.controller;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.dto.TemplateVO;
import com.fusion.docfusion.service.TemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 模板上传与列表（word / excel）
 */
@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @PostMapping("/upload")
    public Result<TemplateVO> uploadTemplate(@RequestParam("file") MultipartFile file) {
        return templateService.uploadTemplate(file);
    }

    @GetMapping("/list")
    public Result<List<TemplateVO>> listTemplates() {
        return templateService.listTemplates();
    }

    @GetMapping("/{templateId}")
    public Result<TemplateVO> getById(@PathVariable Long templateId) {
        return templateService.getById(templateId);
    }
}
