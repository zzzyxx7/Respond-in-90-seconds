package com.fusion.docfusion.service.impl;

import com.fusion.docfusion.config.UploadProperties;
import com.fusion.docfusion.dto.ExtractFieldResult;
import com.fusion.docfusion.entity.Document;
import com.fusion.docfusion.entity.ExtractedValue;
import com.fusion.docfusion.entity.FieldSchema;
import com.fusion.docfusion.exception.BusinessException;
import com.fusion.docfusion.mapper.DocumentMapper;
import com.fusion.docfusion.mapper.ExtractedValueMapper;
import com.fusion.docfusion.mapper.FieldSchemaMapper;
import com.fusion.docfusion.service.AiExtractService;
import com.fusion.docfusion.service.ExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExtractionServiceImpl implements ExtractionService {

    private final UploadProperties uploadProperties;
    private final DocumentMapper documentMapper;
    private final FieldSchemaMapper fieldSchemaMapper;
    private final ExtractedValueMapper extractedValueMapper;
    private final AiExtractService aiExtractService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void extractForDocument(Long documentId, String instruction) {
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new BusinessException("文档不存在");
        }

        Path docsDir = Paths.get(uploadProperties.getDocsDir());
        Path filePath = docsDir.resolve(document.getFilePath());
        File file = filePath.toFile();
        if (!file.exists()) {
            throw new BusinessException("文档文件不存在: " + filePath);
        }

        // 如果没有传入指令，使用一个默认指令
        String finalInstruction = (instruction == null || instruction.isBlank())
                ? "请根据文档内容，提取所有你能识别的关键信息，并以 JSON 形式返回，键名使用字段编码。"
                : instruction;

        // 调用 AI 服务进行抽取（当前若 AI 未部署，调用会失败，但代码结构已经就绪）
        Map<String, ExtractFieldResult> extractedMap = aiExtractService.analyze(file, finalInstruction);
        if (extractedMap == null) {
            extractedMap = new HashMap<>();
        }

        if (extractedMap.isEmpty()) {
            log.warn("AI 抽取结果为空，documentId={}", documentId);
            return;
        }

        // 构建 code -> FieldSchema 的映射，便于快速查找字段ID
        List<FieldSchema> schemas = fieldSchemaMapper.selectAll();
        Map<String, FieldSchema> schemaByCode = new HashMap<>();
        for (FieldSchema schema : schemas) {
            if (Boolean.TRUE.equals(schema.getEnabled())) {
                schemaByCode.put(schema.getCode(), schema);
            }
        }

        List<ExtractedValue> values = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, ExtractFieldResult> entry : extractedMap.entrySet()) {
            String code = entry.getKey();
            ExtractFieldResult fieldResult = entry.getValue();
            if (fieldResult == null) {
                continue;
            }
            String value = fieldResult.getValue();
            BigDecimal confidence = fieldResult.getConfidence();
            FieldSchema schema = schemaByCode.get(code);
            if (schema == null) {
                log.warn("抽取到未知字段 code={}, documentId={}，已跳过", code, documentId);
                continue;
            }
            ExtractedValue ev = new ExtractedValue();
            ev.setDocumentId(documentId);
            ev.setFieldSchemaId(schema.getId());
            ev.setFieldValue(value);
            ev.setConfidence(confidence);
            ev.setCreatedAt(now);
            values.add(ev);
        }

        if (!values.isEmpty()) {
            extractedValueMapper.deleteByDocumentId(documentId);
            extractedValueMapper.insertBatch(values);
            log.info("保存抽取结果 {} 条，documentId={}", values.size(), documentId);
        } else {
            log.warn("没有可保存的抽取结果，documentId={}", documentId);
        }
    }
}

