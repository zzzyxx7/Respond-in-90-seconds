package com.fusion.docfusion.messaging;

import com.fusion.docfusion.config.RabbitConfig;
import com.fusion.docfusion.config.UploadProperties;
import com.fusion.docfusion.entity.Document;
import com.fusion.docfusion.entity.DocumentSet;
import com.fusion.docfusion.entity.FillTask;
import com.fusion.docfusion.entity.Template;
import com.fusion.docfusion.exception.BusinessException;
import com.fusion.docfusion.mapper.DocumentMapper;
import com.fusion.docfusion.mapper.DocumentSetMapper;
import com.fusion.docfusion.mapper.FillTaskMapper;
import com.fusion.docfusion.mapper.TemplateMapper;
import com.fusion.docfusion.service.ExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 异步处理填表任务（当前仍为占位实现：复制模板/生成简单 Excel）。
 * 后续接入 AI 时，只需要在这里接入 RAG + 抽取 + 填表逻辑。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FillTaskConsumer {

    private final UploadProperties uploadProperties;
    private final DocumentSetMapper documentSetMapper;
    private final DocumentMapper documentMapper;
    private final TemplateMapper templateMapper;
    private final FillTaskMapper fillTaskMapper;
    private final ExtractionService extractionService;

    @RabbitListener(queues = RabbitConfig.FILL_TASK_QUEUE)
    public void handleFillTask(Long taskId) {
        log.info("异步处理填表任务, taskId={}", taskId);
        FillTask task = fillTaskMapper.selectById(taskId);
        if (task == null) {
            log.warn("异步任务处理失败：任务不存在, taskId={}", taskId);
            return;
        }

        try {
            task.setStatus("RUNNING");
            fillTaskMapper.updateById(task);

            if ("TEMPLATE".equalsIgnoreCase(task.getMode())) {
                processTemplateTask(task);
            } else if ("FREE".equalsIgnoreCase(task.getMode())) {
                processFreeTask(task);
            } else {
                throw new BusinessException("未知任务模式: " + task.getMode());
            }

            task.setStatus("SUCCESS");
            task.setFinishedAt(LocalDateTime.now());
            fillTaskMapper.updateById(task);
        } catch (Exception e) {
            log.error("异步任务处理异常, taskId={}", taskId, e);
            task.setStatus("FAILED");
            task.setFinishedAt(LocalDateTime.now());
            task.setErrorMessage(e.getMessage());
            fillTaskMapper.updateById(task);
        }
    }

    private void processTemplateTask(FillTask task) throws IOException {
        Long documentSetId = task.getDocumentSetId();
        Long templateId = task.getTemplateId();

        DocumentSet set = documentSetMapper.selectById(documentSetId);
        if (set == null) {
            throw new BusinessException("文档集不存在");
        }
        Template template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        List<Document> docs = documentMapper.selectByDocumentSetId(documentSetId);
        if (docs.isEmpty()) {
            throw new BusinessException("文档集中没有文档");
        }

        // 先对文档集中的每个文档执行抽取，结果写入 extracted_value
        String instruction = task.getUserRequirement();
        for (Document doc : docs) {
            log.info("开始抽取文档, taskId={}, documentId={}", task.getId(), doc.getId());
            extractionService.extractForDocument(doc.getId(), instruction);
        }

        Path templatesDir = Paths.get(uploadProperties.getTemplatesDir());
        Path resultsDir = Paths.get(uploadProperties.getResultsDir());
        Path templatePath = templatesDir.resolve(template.getFilePath());
        if (!Files.exists(templatePath)) {
            throw new BusinessException("模板文件不存在: " + template.getFileName());
        }

        Files.createDirectories(resultsDir);
        String resultFileName = "fill_" + task.getId() + "_" + UUID.randomUUID().toString().substring(0, 8) + "_" + template.getFileName();
        Path resultPath = resultsDir.resolve(resultFileName);
        Files.copy(templatePath, resultPath);

        task.setResultFilePath(resultFileName);
    }

    private void processFreeTask(FillTask task) throws IOException {
        Long documentSetId = task.getDocumentSetId();

        DocumentSet set = documentSetMapper.selectById(documentSetId);
        if (set == null) {
            throw new BusinessException("文档集不存在");
        }
        List<Document> docs = documentMapper.selectByDocumentSetId(documentSetId);
        if (docs.isEmpty()) {
            throw new BusinessException("文档集中没有文档");
        }

        Path resultsDir = Paths.get(uploadProperties.getResultsDir());
        Files.createDirectories(resultsDir);

        String resultFileName = "free_" + task.getId() + "_" + UUID.randomUUID().toString().substring(0, 8) + ".xlsx";
        Path resultPath = resultsDir.resolve(resultFileName);

        try (org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("汇总");
            int rowIdx = 0;
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(rowIdx++);
            header.createCell(0).setCellValue("文件名");
            for (Document doc : docs) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(doc.getFileName());
            }
            try (java.io.OutputStream os = Files.newOutputStream(resultPath)) {
                workbook.write(os);
            }
        }

        task.setResultFilePath(resultFileName);
    }
}

