package com.fusion.docfusion.messaging;

import com.fusion.docfusion.config.RabbitConfig;
import com.fusion.docfusion.config.UploadProperties;
import com.fusion.docfusion.entity.Document;
import com.fusion.docfusion.entity.DocumentSet;
import com.fusion.docfusion.entity.FillTask;
import com.fusion.docfusion.entity.FillTaskStep;
import com.fusion.docfusion.entity.Template;
import com.fusion.docfusion.enums.TaskMode;
import com.fusion.docfusion.enums.TaskStatus;
import com.fusion.docfusion.exception.BusinessException;
import com.fusion.docfusion.mapper.DocumentMapper;
import com.fusion.docfusion.mapper.DocumentSetMapper;
import com.fusion.docfusion.mapper.FillTaskMapper;
import com.fusion.docfusion.mapper.FillTaskStepMapper;
import com.fusion.docfusion.mapper.TemplateMapper;
import com.fusion.docfusion.service.ExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Component;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 异步处理填表任务（当前仍为占位实现：复制模板/生成简单 Excel）。
 * 后续接入 AI 时，只需要在这里接入 RAG + 抽取 + 填表逻辑。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FillTaskConsumer {

    private static final String STEP_RAG = "RAG";
    private static final String STEP_EXTRACT = "EXTRACT";
    private static final String STEP_FILL = "FILL";
    private static final String STEP_GENERATE = "GENERATE";

    private static final String STEP_STATUS_RUNNING = "RUNNING";
    private static final String STEP_STATUS_SUCCESS = "SUCCESS";
    private static final String STEP_STATUS_FAILED = "FAILED";
    private static final String STEP_STATUS_SKIPPED = "SKIPPED";

    /**
     * 主队列失败后会进入 retry 队列（TTL 到期回主队列）。
     * 超过最大次数后，消息将被投递到 DLQ（parking lot）并 ACK，避免无限循环。
     */
    private static final int MAX_RETRY_ATTEMPTS = 5;

    private final UploadProperties uploadProperties;
    private final DocumentSetMapper documentSetMapper;
    private final DocumentMapper documentMapper;
    private final TemplateMapper templateMapper;
    private final FillTaskMapper fillTaskMapper;
    private final FillTaskStepMapper fillTaskStepMapper;
    private final ExtractionService extractionService;
    private final AmqpTemplate amqpTemplate;

    @RabbitListener(queues = RabbitConfig.FILL_TASK_QUEUE)
    public void handleFillTask(
            @Payload Long taskId,
            com.rabbitmq.client.Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
            @Header(value = "x-death", required = false) List<Map<String, Object>> xDeath
    ) throws IOException {
        log.info("异步处理填表任务, taskId={}", taskId);
        try {
            FillTask task = fillTaskMapper.selectById(taskId);
            if (task == null) {
                log.warn("异步任务处理失败：任务不存在, taskId={}", taskId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 幂等：MQ 可能重复投递已成功消息，避免重复写结果/重复生成文件
            if (TaskStatus.SUCCESS.name().equalsIgnoreCase(task.getStatus())) {
                log.info("任务已为 SUCCESS，幂等跳过, taskId={}", taskId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            task.setStatus(TaskStatus.RUNNING.name());
            fillTaskMapper.updateById(task);

            if (TaskMode.TEMPLATE.name().equalsIgnoreCase(task.getMode())) {
                processTemplateTask(task);
            } else if (TaskMode.FREE.name().equalsIgnoreCase(task.getMode())) {
                processFreeTask(task);
            } else {
                throw new BusinessException("未知任务模式: " + task.getMode());
            }

            task.setStatus(TaskStatus.SUCCESS.name());
            task.setFinishedAt(LocalDateTime.now());
            fillTaskMapper.updateById(task);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("异步任务处理异常, taskId={}", taskId, e);

            // 尝试更新任务状态（可能 taskId 查不到，这里允许失败）
            try {
                FillTask task = fillTaskMapper.selectById(taskId);
                if (task != null) {
                    task.setStatus(TaskStatus.FAILED.name());
                    task.setFinishedAt(LocalDateTime.now());
                    task.setErrorMessage(truncateErrorMessage(e));
                    fillTaskMapper.updateById(task);
                }
            } catch (Exception ignore) {
                // ignore
            }

            int attempts = extractAttemptsFromXDeath(xDeath, RabbitConfig.FILL_TASK_QUEUE);
            if (attempts >= MAX_RETRY_ATTEMPTS) {
                // 超过最大重试次数：投递到 DLQ，ACK 掉当前消息，避免无限循环
                log.error("消息超过最大重试次数，进入 DLQ, taskId={}, attempts={}", taskId, attempts);
                amqpTemplate.convertAndSend(
                        RabbitConfig.FILL_TASK_DLX_EXCHANGE,
                        RabbitConfig.FILL_TASK_DLQ_ROUTING_KEY,
                        taskId
                );
                channel.basicAck(deliveryTag, false);
            } else {
                // 进入重试队列（通过 DLX 路由）
                channel.basicNack(deliveryTag, false, false);
            }
        }
    }

    private static int extractAttemptsFromXDeath(List<Map<String, Object>> xDeath, String queueName) {
        if (xDeath == null || xDeath.isEmpty()) {
            return 0;
        }
        long total = 0L;
        for (Map<String, Object> death : xDeath) {
            if (death == null) continue;
            Object q = death.get("queue");
            if (q == null || !queueName.equals(String.valueOf(q))) {
                continue;
            }
            Object count = death.get("count");
            if (count instanceof Long l) {
                total += l;
            } else if (count instanceof Integer i) {
                total += i.longValue();
            } else if (count != null) {
                try {
                    total += Long.parseLong(String.valueOf(count));
                } catch (NumberFormatException ignore) {
                    // ignore
                }
            }
        }
        // x-death 计数表示“被 dead-letter 的次数”，也就是失败次数
        if (total > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) total;
    }

    /** fill_task.error_message 列为 VARCHAR(512)，避免异常信息过长写入失败 */
    private static String truncateErrorMessage(Throwable e) {
        if (e == null) {
            return null;
        }
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = e.getClass().getSimpleName();
        }
        int max = 500;
        return msg.length() <= max ? msg : msg.substring(0, max) + "...";
    }

    private void processTemplateTask(FillTask task) throws IOException {
        Long taskId = task.getId();
        Long documentSetId = task.getDocumentSetId();
        Long templateId = task.getTemplateId();

        // RAG：当前未接入，先标记跳过，便于前端链路展示（后续接入时改为 RUNNING/SUCCESS）
        upsertStep(taskId, STEP_RAG, "RAG 检索", STEP_STATUS_SKIPPED, null, null, "当前未接入 RAG", null);

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
        LocalDateTime extractStart = LocalDateTime.now();
        upsertStep(taskId, STEP_EXTRACT, "字段抽取", STEP_STATUS_RUNNING, extractStart, null,
                "文档数: " + docs.size(), null);
        try {
            for (Document doc : docs) {
                log.info("开始抽取文档, taskId={}, documentId={}", task.getId(), doc.getId());
                extractionService.extractForDocument(doc.getId(), instruction);
            }
            LocalDateTime extractEnd = LocalDateTime.now();
            upsertStep(taskId, STEP_EXTRACT, "字段抽取", STEP_STATUS_SUCCESS, extractStart, extractEnd,
                    "文档数: " + docs.size(), null);
        } catch (Exception e) {
            LocalDateTime extractEnd = LocalDateTime.now();
            upsertStep(taskId, STEP_EXTRACT, "字段抽取", STEP_STATUS_FAILED, extractStart, extractEnd,
                    "文档数: " + docs.size(), e.getMessage());
            throw e;
        }

        // 填表：当前未接入 AI 填表，引擎侧仅保留步骤占位
        upsertStep(taskId, STEP_FILL, "模板填表", STEP_STATUS_SKIPPED, null, null,
                "当前未接入 AI 填表服务（暂以复制模板文件占位）", null);

        Path templatesDir = Paths.get(uploadProperties.getTemplatesDir());
        Path resultsDir = Paths.get(uploadProperties.getResultsDir());
        Path templatePath = templatesDir.resolve(template.getFilePath());
        if (!Files.exists(templatePath)) {
            throw new BusinessException("模板文件不存在: " + template.getFileName());
        }

        LocalDateTime genStart = LocalDateTime.now();
        upsertStep(taskId, STEP_GENERATE, "生成结果文件", STEP_STATUS_RUNNING, genStart, null,
                "模板: " + template.getFileName(), null);
        try {
            Files.createDirectories(resultsDir);
            String resultFileName = "fill_" + task.getId() + "_" + UUID.randomUUID().toString().substring(0, 8) + "_" + template.getFileName();
            Path resultPath = resultsDir.resolve(resultFileName);
            Files.copy(templatePath, resultPath);

            task.setResultFilePath(resultFileName);

            LocalDateTime genEnd = LocalDateTime.now();
            upsertStep(taskId, STEP_GENERATE, "生成结果文件", STEP_STATUS_SUCCESS, genStart, genEnd,
                    "输出: " + resultFileName, null);
        } catch (Exception e) {
            LocalDateTime genEnd = LocalDateTime.now();
            upsertStep(taskId, STEP_GENERATE, "生成结果文件", STEP_STATUS_FAILED, genStart, genEnd,
                    "模板: " + template.getFileName(), e.getMessage());
            throw e;
        }
    }

    private void processFreeTask(FillTask task) throws IOException {
        Long taskId = task.getId();
        Long documentSetId = task.getDocumentSetId();

        upsertStep(taskId, STEP_RAG, "RAG 检索", STEP_STATUS_SKIPPED, null, null, "当前未接入 RAG", null);
        upsertStep(taskId, STEP_EXTRACT, "字段抽取", STEP_STATUS_SKIPPED, null, null, "自由模式当前未接入抽取（仅生成汇总占位）", null);
        upsertStep(taskId, STEP_FILL, "汇总生成", STEP_STATUS_SKIPPED, null, null, "自由模式未来将由 AI 生成汇总表", null);

        DocumentSet set = documentSetMapper.selectById(documentSetId);
        if (set == null) {
            throw new BusinessException("文档集不存在");
        }
        List<Document> docs = documentMapper.selectByDocumentSetId(documentSetId);
        if (docs.isEmpty()) {
            throw new BusinessException("文档集中没有文档");
        }

        Path resultsDir = Paths.get(uploadProperties.getResultsDir());
        LocalDateTime genStart = LocalDateTime.now();
        upsertStep(taskId, STEP_GENERATE, "生成结果文件", STEP_STATUS_RUNNING, genStart, null,
                "文档数: " + docs.size(), null);
        try {
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

            LocalDateTime genEnd = LocalDateTime.now();
            upsertStep(taskId, STEP_GENERATE, "生成结果文件", STEP_STATUS_SUCCESS, genStart, genEnd,
                    "输出: " + resultFileName, null);
        } catch (Exception e) {
            LocalDateTime genEnd = LocalDateTime.now();
            upsertStep(taskId, STEP_GENERATE, "生成结果文件", STEP_STATUS_FAILED, genStart, genEnd,
                    "文档数: " + docs.size(), e.getMessage());
            throw e;
        }
    }

    private void upsertStep(Long taskId,
                            String stepCode,
                            String stepName,
                            String status,
                            LocalDateTime startedAt,
                            LocalDateTime finishedAt,
                            String message,
                            String errorMessage) {
        FillTaskStep step = new FillTaskStep();
        step.setTaskId(taskId);
        step.setStepCode(stepCode);
        step.setStepName(stepName);
        step.setStatus(status);
        step.setStartedAt(startedAt);
        step.setFinishedAt(finishedAt);
        if (startedAt != null && finishedAt != null) {
            step.setDurationMs(ChronoUnit.MILLIS.between(startedAt, finishedAt));
        } else {
            step.setDurationMs(null);
        }
        step.setMessage(message);
        step.setErrorMessage(errorMessage);
        fillTaskStepMapper.upsert(step);
    }
}

