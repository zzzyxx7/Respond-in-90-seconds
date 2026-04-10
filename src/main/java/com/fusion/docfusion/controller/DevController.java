package com.fusion.docfusion.controller;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.entity.Document;
import com.fusion.docfusion.entity.FieldSchema;
import com.fusion.docfusion.entity.TemplateField;
import com.fusion.docfusion.entity.Template;
import com.fusion.docfusion.exception.BusinessException;
import com.fusion.docfusion.exception.ErrorCode;
import com.fusion.docfusion.mapper.DocumentMapper;
import com.fusion.docfusion.mapper.TemplateMapper;
import com.fusion.docfusion.service.ExtractionService;
import com.fusion.docfusion.service.FieldSchemaService;
import com.fusion.docfusion.service.TemplateFieldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 开发期使用的一些调试接口（不会在正式环境暴露给普通用户）。
 */
@RestController
@RequestMapping("/api/dev")
@RequiredArgsConstructor
@Slf4j
public class DevController {

    private final ExtractionService extractionService;
    private final FieldSchemaService fieldSchemaService;
    private final TemplateFieldService templateFieldService;
    private final DocumentMapper documentMapper;
    private final TemplateMapper templateMapper;

    /**
     * 对单个文档执行抽取并写入 extracted_value。
     * POST /api/dev/extract/public/{documentPublicId}?instruction=xxx
     */
    @PostMapping("/extract/public/{documentPublicId}")
    public Result<String> extractForDocumentByPublicId(@PathVariable String documentPublicId,
                                                       @RequestParam(value = "instruction", required = false) String instruction) {
        Long documentId = resolveDocumentId(documentPublicId);
        log.info("Dev: 抽取文档(公共ID), documentPublicId={}, resolvedDocumentId={}, instructionLen={}",
                documentPublicId, documentId, instruction == null ? 0 : instruction.length());
        extractionService.extractForDocument(documentId, instruction);
        return Result.success("提取任务已完成（如 AI 服务可用则结果已写入数据库）");
    }

    /**
     * 新增一个字段定义。
     * POST /api/dev/fields
     */
    @PostMapping("/fields")
    public Result<FieldSchema> createField(@RequestBody FieldSchema schema) {
        log.info("Dev: 新增字段定义, code={}, displayName={}", schema == null ? null : schema.getCode(), schema == null ? null : schema.getDisplayName());
        FieldSchema saved = fieldSchemaService.create(schema);
        return Result.success(saved);
    }

    /**
     * 查看所有字段定义。
     * GET /api/dev/fields
     */
    @GetMapping("/fields")
    public Result<List<FieldSchema>> listFields() {
        log.info("Dev: 查询字段定义列表");
        return Result.success(fieldSchemaService.listAll());
    }

    /**
     * 为某个模板配置字段映射（覆盖式）。
     * POST /api/dev/templates/public/{templatePublicId}/fields
     */
    @PostMapping("/templates/public/{templatePublicId}/fields")
    public Result<String> saveTemplateFieldsByPublicId(@PathVariable String templatePublicId,
                                                       @RequestBody List<TemplateField> fields) {
        Long templateId = resolveTemplateId(templatePublicId);
        log.info("Dev: 保存模板字段映射(公共ID), templatePublicId={}, resolvedTemplateId={}, count={}",
                templatePublicId, templateId, fields == null ? 0 : fields.size());
        templateFieldService.saveForTemplate(templateId, fields);
        return Result.success("模板字段配置已保存");
    }

    /**
     * 查看某个模板的字段映射。
     * GET /api/dev/templates/public/{templatePublicId}/fields
     */
    @GetMapping("/templates/public/{templatePublicId}/fields")
    public Result<List<TemplateField>> listTemplateFieldsByPublicId(@PathVariable String templatePublicId) {
        Long templateId = resolveTemplateId(templatePublicId);
        log.info("Dev: 查询模板字段映射(公共ID), templatePublicId={}, resolvedTemplateId={}", templatePublicId, templateId);
        return Result.success(templateFieldService.listByTemplateId(templateId));
    }

    private Long resolveDocumentId(String documentPublicId) {
        if (documentPublicId == null || documentPublicId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "documentPublicId 不能为空");
        }
        Document d = documentMapper.selectByPublicId(documentPublicId);
        if (d == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        return d.getId();
    }

    private Long resolveTemplateId(String templatePublicId) {
        if (templatePublicId == null || templatePublicId.isBlank()) {
            throw new BusinessException(ErrorCode.TEMPLATE_PUBLIC_ID_INVALID);
        }
        Template t = templateMapper.selectByPublicId(templatePublicId);
        if (t == null) {
            throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND);
        }
        return t.getId();
    }
}