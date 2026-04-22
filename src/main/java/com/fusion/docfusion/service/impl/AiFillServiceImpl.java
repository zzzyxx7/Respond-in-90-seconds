package com.fusion.docfusion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusion.docfusion.config.UploadProperties;
import com.fusion.docfusion.entity.Document;
import com.fusion.docfusion.entity.FillTask;
import com.fusion.docfusion.entity.Template;
import com.fusion.docfusion.exception.BusinessException;
import com.fusion.docfusion.exception.ErrorCode;
import com.fusion.docfusion.mapper.FillTaskMapper;
import com.fusion.docfusion.mapper.TemplateMapper;
import com.fusion.docfusion.service.AiFillService;
import com.fusion.docfusion.util.FillTaskCancelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.Iterator;
import java.util.Map;

/**
 * AI 填表适配（方案 B）。
 * 调用 A23 HTTP 任务接口：
 * 1) POST /api/tasks/create
 * 2) 轮询 GET /api/tasks/{task_id}
 * 3) 下载 GET /api/tasks/{task_id}/download/{kind}
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiFillServiceImpl implements AiFillService {

    /** A23 状态接口里 output_files 中可用于填表下载的键（与 downloadRemoteResult 候选 kind 对齐）。 */
    private static final List<String> REMOTE_OUTPUT_FILE_KEYS = List.of(
            "excel", "result_xlsx", "docx", "result_docx", "json", "result_json");

    private final RestTemplate restTemplate;
    private final TemplateMapper templateMapper;
    private final FillTaskMapper fillTaskMapper;
    private final UploadProperties uploadProperties;
    private final FillTaskCancelService cancelService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ai.base-url:http://localhost:8000}")
    private String aiBaseUrl;

    @Value("${ai.fill.create-path:/api/tasks/create}")
    private String createPath;

    @Value("${ai.fill.status-path:/api/tasks/%s}")
    private String statusPathFormat;

    @Value("${ai.fill.download-path:/api/tasks/%s/download/%s}")
    private String downloadPathFormat;

    @Value("${ai.fill.poll-interval-ms:1000}")
    private long pollIntervalMs;

    @Value("${ai.fill.poll-timeout-ms:1000000}")
    private long pollTimeoutMs;

    /**
     * A23 AI 侧任务总超时（秒）。
     * A23 会用该值决定 main.py 的 --total-timeout 以及外层 watchdog（约 total_timeout + 300s）。
     */
    @Value("${ai.fill.total-timeout-seconds:1000}")
    private int aiTotalTimeoutSeconds;

    @Value("${ai.cost.currency:USD}")
    private String defaultCostCurrency;

    @Value("${ai.cost.default-input-per-1k:0}")
    private BigDecimal defaultInputPer1k;

    @Value("${ai.cost.default-output-per-1k:0}")
    private BigDecimal defaultOutputPer1k;

    @Value("${ai.client.max-attempts:3}")
    private int apiRetryMaxAttempts;

    @Value("${ai.client.retry-interval-ms:800}")
    private long apiRetryBaseDelayMs;

    @Value("${ai.client.max-backoff-ms:10000}")
    private long apiRetryMaxBackoffMs;

    /**
     * 为 true 时不调用 A23 填表 HTTP（/api/tasks/create 等），将模板或输入文件复制到结果目录作为「假结果」，
     * 便于无 Python AI 时联调：MQ → 消费 → 步骤 → 下载接口。
     * 与 AiExtractServiceImpl 中的 ai.mock.enabled 共用同一开关。
     */
    @Value("${ai.mock.enabled:false}")
    private boolean aiMockEnabled;

    @Override
    public void fillTemplateForTask(FillTask task, List<Document> docs) {
        if (task == null || task.getTemplateId() == null) {
            throw new BusinessException(ErrorCode.FILL_TASK_INVALID_TEMPLATE);
        }
        if (docs == null || docs.isEmpty()) {
            throw new BusinessException(ErrorCode.FILL_TASK_DOCS_EMPTY);
        }

        Template template = templateMapper.selectById(task.getTemplateId());
        if (template == null) {
            throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND);
        }

        Path templateFile = Paths.get(uploadProperties.getTemplatesDir()).resolve(template.getFilePath()).normalize();
        if (!Files.exists(templateFile)) {
            throw new BusinessException(ErrorCode.TEMPLATE_FILE_MISSING, "模板文件不存在: " + template.getFileName());
        }

        if (aiMockEnabled) {
            log.warn("AI Mock 已启用：跳过 A23 填表 HTTP，将模板复制为结果文件 taskPublicId={}", task.getPublicId());
            String localName = copyMockResultFile(templateFile, template.getFileName(), task.getPublicId());
            task.setResultFilePath(localName);
            applyMockFillMetadata(task);
            log.info("AI Mock 填表完成, localFile={}", localName);
            return;
        }

        String taskId = createRemoteTask(templateFile.toFile(), docs, task.getUserRequirement());
        persistRemoteTaskId(task, taskId);
        RemoteUsageSummary usageSummary = waitForRemoteTask(task.getPublicId(), taskId);

        // 模板模式优先下载 xlsx/docx，避免误拿到 json 等中间结果。
        DownloadedResult downloaded = downloadRemoteResult(taskId, template.getFileName(),
                new String[]{"excel", "result_xlsx", "docx", "result_docx"});
        task.setResultFilePath(downloaded.localFileName);
        mirrorAdditionalRemoteResults(taskId, downloaded.kind);
        applyUsageSummary(task, taskId, usageSummary);
        log.info("AI 填表完成并落盘, localFile={}, remoteKind={}", downloaded.localFileName, downloaded.kind);
    }

    @Override
    public void fillFreeForTask(FillTask task, List<Document> docs) {
        if (task == null) {
            throw new BusinessException(ErrorCode.FREE_MODE_TASK_INVALID);
        }
        if (docs == null || docs.isEmpty()) {
            throw new BusinessException(ErrorCode.FREE_MODE_DOCS_EMPTY);
        }

        if (aiMockEnabled) {
            log.warn("AI Mock 已启用：跳过 A23 填表 HTTP，将首份输入文档复制为结果文件 taskPublicId={}", task.getPublicId());
            Document d0 = docs.get(0);
            Path docPath = Paths.get(uploadProperties.getDocsDir()).resolve(d0.getFilePath()).normalize();
            if (!Files.exists(docPath)) {
                throw new BusinessException(ErrorCode.AI_INPUT_DOC_MISSING, "输入文档不存在: " + d0.getFileName());
            }
            String localName = copyMockResultFile(docPath, d0.getFileName(), task.getPublicId());
            task.setResultFilePath(localName);
            applyMockFillMetadata(task);
            log.info("AI Mock 自由模式填表完成, localFile={}", localName);
            return;
        }

        String taskId = createRemoteTask(null, docs, task.getUserRequirement(), true);
        persistRemoteTaskId(task, taskId);
        RemoteUsageSummary usageSummary = waitForRemoteTask(task.getPublicId(), taskId);

        // 自由模式允许 json 作为兜底结果，同时优先尝试下载文档类结果。
        DownloadedResult downloaded = downloadRemoteResult(taskId, "free_mode.xlsx",
                new String[]{"excel", "result_xlsx", "docx", "result_docx", "json", "result_json"});
        task.setResultFilePath(downloaded.localFileName);
        mirrorAdditionalRemoteResults(taskId, downloaded.kind);
        applyUsageSummary(task, taskId, usageSummary);
        log.info("AI 自由模式完成并落盘, localFile={}, remoteKind={}", downloaded.localFileName, downloaded.kind);
    }

    private String createRemoteTask(File templateFile, List<Document> docs, String note) {
        return createRemoteTask(templateFile, docs, note, false);
    }

    private String createRemoteTask(File templateFile, List<Document> docs, String note, boolean freeMode) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        if (templateFile != null) {
            body.add("template", new FileSystemResource(templateFile));
            body.add("template_mode", "file");
        } else if (freeMode) {
            body.add("template_mode", "llm");
            body.add("template_description", normalizeTemplateDescription(note));
        }

        for (Document doc : docs) {
            File file = Paths.get(uploadProperties.getDocsDir()).resolve(doc.getFilePath()).toFile();
            if (!file.exists()) {
                throw new BusinessException(ErrorCode.AI_INPUT_DOC_MISSING, "输入文档不存在: " + doc.getFileName());
            }
            body.add("input_files", new FileSystemResource(file));
        }

        if (note != null && !note.isBlank()) {
            body.add("note", note);
        }

        // 关键：必须传给 A23，否则 A23 默认 total_timeout=110s，外层 watchdog ~410s 会提前掐断长任务
        if (aiTotalTimeoutSeconds > 0) {
            body.add("total_timeout", String.valueOf(aiTotalTimeoutSeconds));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
        String url = aiBaseUrl + createPath;
        log.info("调用 AI 任务创建接口, url={}, docs={}, freeMode={}", url, docs.size(), freeMode);

        try {
            ResponseEntity<String> response = executeWithRetry(
                    "createRemoteTask",
                    () -> restTemplate.postForEntity(url, request, String.class)
            );
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new BusinessException(ErrorCode.AI_CREATE_REMOTE_FAILED,
                        "AI 创建任务失败，HTTP: " + response.getStatusCode());
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            String taskId = root.path("task_id").asText("");
            if (taskId.isBlank()) {
                throw new BusinessException(ErrorCode.AI_TASK_ID_MISSING);
            }
            return taskId;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AI_CREATE_REMOTE_FAILED, "调用 AI 创建任务异常: " + e.getMessage());
        }
    }

    private String normalizeTemplateDescription(String note) {
        if (note != null && !note.isBlank()) {
            return note;
        }
        return "Analyze uploaded documents and organize the result in the most suitable structure.";
    }

    /**
     * 将源文件复制到 results 目录，返回写入 {@link UploadProperties#getResultsDir()} 下的相对文件名。
     */
    private String copyMockResultFile(Path sourceFile, String originalName, String publicId) {
        try {
            Path resultsDir = Paths.get(uploadProperties.getResultsDir());
            Files.createDirectories(resultsDir);
            String safeBase = "mock-result";
            if (originalName != null && !originalName.isBlank()) {
                int slash = Math.max(originalName.lastIndexOf('/'), originalName.lastIndexOf('\\'));
                String leaf = slash >= 0 ? originalName.substring(slash + 1) : originalName;
                if (!leaf.isBlank()) {
                    int dot = leaf.lastIndexOf('.');
                    safeBase = dot > 0 ? leaf.substring(0, dot) : leaf;
                }
            }
            String ext = "";
            if (originalName != null && !originalName.isBlank()) {
                int dot = originalName.lastIndexOf('.');
                if (dot > 0 && dot < originalName.length() - 1) {
                    ext = originalName.substring(dot);
                }
            }
            String localFileName = "fill_mock_" + publicId + "_"
                    + UUID.randomUUID().toString().substring(0, 8) + "_" + safeBase + ext;
            Path out = resultsDir.resolve(localFileName).normalize();
            if (!out.startsWith(resultsDir.normalize())) {
                throw new BusinessException(ErrorCode.AI_DOWNLOAD_PATH_INVALID);
            }
            Files.copy(sourceFile, out, StandardCopyOption.REPLACE_EXISTING);
            return localFileName;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AI_CREATE_REMOTE_FAILED, "Mock 写出结果文件失败: " + e.getMessage());
        }
    }

    private void applyMockFillMetadata(FillTask task) {
        RemoteUsageSummary u = new RemoteUsageSummary();
        u.provider = "mock";
        u.model = "mock";
        u.inputTokens = 0L;
        u.outputTokens = 0L;
        u.totalTokens = 0L;
        u.cost = BigDecimal.ZERO;
        u.currency = defaultCostCurrency;
        u.rawUsageJson = "{\"mock\":true,\"note\":\"ai.mock.enabled bypasses A23 fill HTTP\"}";
        applyUsageSummary(task, "mock-" + task.getPublicId(), u);
    }

    private RemoteUsageSummary waitForRemoteTask(String taskPublicId, String remoteTaskId) {
        String statusUrl = aiBaseUrl + String.format(statusPathFormat, remoteTaskId);
        long deadline = System.currentTimeMillis() + pollTimeoutMs;
        String lastStatus = "";
        RemoteUsageSummary usageSummary = new RemoteUsageSummary();

        while (System.currentTimeMillis() < deadline) {
            if (cancelService.isCancelRequested(taskPublicId)) {
                throw new BusinessException(ErrorCode.TASK_CANCELLED, "任务已取消，停止等待 AI 结果");
            }
            try {
                ResponseEntity<String> response = executeWithRetry(
                        "waitForRemoteTask/status",
                        () -> restTemplate.getForEntity(statusUrl, String.class)
                );
                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    sleepPoll();
                    continue;
                }

                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode taskNode = root.path("task");
                String status = taskNode.path("status").asText("");
                lastStatus = status;

                if ("succeeded".equalsIgnoreCase(status)) {
                    fillUsageSummary(root, taskNode, usageSummary);
                    return usageSummary;
                }
                if ("failed".equalsIgnoreCase(status)) {
                    // A23 可能因进程退出码/执行器返回值非 success 标记 failed，但 main 已写出结果文件；
                    // 此时 output_files 仍有路径，下载接口可用，按「可恢复」继续走下载落盘。
                    if (hasUsableRemoteOutputFiles(root)) {
                        log.warn(
                                "AI 任务状态为 failed 但 output_files 已存在，按可恢复成功继续下载, remoteTaskId={}",
                                remoteTaskId);
                        fillUsageSummary(root, taskNode, usageSummary);
                        return usageSummary;
                    }
                    String err = extractAiErrorMessage(root, taskNode, response.getBody());
                    log.warn("AI task failed, remoteTaskId={}, err={}", remoteTaskId, err);
                    throw new BusinessException(ErrorCode.AI_TASK_FAILED, "AI 填表任务失败: " + err);
                }
                sleepPoll();
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                sleepPoll();
            }
        }

        throw new BusinessException(
                ErrorCode.AI_TASK_TIMEOUT,
                "AI 填表任务超时(" + Duration.ofMillis(pollTimeoutMs).toSeconds() + "s), lastStatus=" + lastStatus
        );
    }

    private String extractAiErrorMessage(JsonNode root, JsonNode taskNode, String rawBody) {
        String[] keys = new String[]{"error", "error_message", "message", "detail", "traceback"};
        String err = firstNonBlankText(taskNode, keys);
        if (err == null) {
            err = firstNonBlankText(root, keys);
        }
        if (err == null) {
            JsonNode errObj = taskNode.path("error");
            if (!errObj.isMissingNode() && !errObj.isNull() && errObj.isObject()) {
                err = errObj.toString();
            }
        }
        if (err == null || err.isBlank()) {
            err = "unknown";
        }

        if (rawBody != null && !rawBody.isBlank()) {
            String compact = rawBody.replaceAll("\\s+", " ");
            if (compact.length() > 360) {
                compact = compact.substring(0, 360) + "...";
            }
            if (!err.contains("{") && !err.contains("task_id")) {
                err = err + " | raw=" + compact;
            }
        }
        return err;
    }

    /**
     * 判断 A23 GET /api/tasks/{id} 返回体是否已包含可下载的输出文件路径（与任务 status 可能不一致）。
     */
    private static boolean hasUsableRemoteOutputFiles(JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return false;
        }
        JsonNode outputFiles = root.path("output_files");
        if (!outputFiles.isObject()) {
            return false;
        }
        for (String k : REMOTE_OUTPUT_FILE_KEYS) {
            if (nonBlankTextNode(outputFiles.get(k))) {
                return true;
            }
        }
        JsonNode byInput = outputFiles.path("by_input");
        if (!byInput.isObject()) {
            return false;
        }
        Iterator<Map.Entry<String, JsonNode>> it = byInput.fields();
        while (it.hasNext()) {
            JsonNode group = it.next().getValue();
            if (!group.isObject()) {
                continue;
            }
            for (String k : REMOTE_OUTPUT_FILE_KEYS) {
                if (nonBlankTextNode(group.get(k))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean nonBlankTextNode(JsonNode n) {
        return n != null && !n.isNull() && n.isTextual() && !n.asText().isBlank();
    }

    private void fillUsageSummary(JsonNode root, JsonNode taskNode, RemoteUsageSummary usageSummary) {
        usageSummary.provider = firstNonBlankText(taskNode, "provider", "llm_provider");
        if (usageSummary.provider == null) {
            usageSummary.provider = firstNonBlankText(root, "provider", "llm_provider");
        }

        usageSummary.model = firstNonBlankText(taskNode, "model", "model_name", "llm_model");
        if (usageSummary.model == null) {
            usageSummary.model = firstNonBlankText(root, "model", "model_name", "llm_model");
        }

        usageSummary.currency = firstNonBlankText(taskNode, "currency");
        if (usageSummary.currency == null) {
            usageSummary.currency = firstNonBlankText(root, "currency");
        }

        usageSummary.cost = extractDecimal(taskNode, "cost", "total_cost");
        if (usageSummary.cost == null) {
            usageSummary.cost = extractDecimal(root, "cost", "total_cost");
        }

        JsonNode usageNode = pickFirstObject(taskNode, "usage", "token_usage", "llm_usage");
        if (usageNode == null) {
            usageNode = pickFirstObject(root, "usage", "token_usage", "llm_usage");
        }
        if (usageNode != null && !usageNode.isMissingNode()) {
            usageSummary.inputTokens = extractLong(usageNode, "prompt_tokens", "input_tokens", "request_tokens");
            usageSummary.outputTokens = extractLong(usageNode, "completion_tokens", "output_tokens", "response_tokens");
            usageSummary.totalTokens = extractLong(usageNode, "total_tokens");
            usageSummary.rawUsageJson = usageNode.toString();
        }
        if (usageSummary.totalTokens == null) {
            usageSummary.totalTokens = safeSum(usageSummary.inputTokens, usageSummary.outputTokens);
        }

        // 兼容不同 AI 网关/版本：很多字段位于 task/result/meta/data 等深层节点
        fillUsageSummaryDeep(root, taskNode, usageSummary);
    }

    private void fillUsageSummaryDeep(JsonNode root, JsonNode taskNode, RemoteUsageSummary usageSummary) {
        JsonNode[] candidates = new JsonNode[]{
                taskNode,
                root,
                taskNode == null ? null : taskNode.path("result"),
                root == null ? null : root.path("result"),
                taskNode == null ? null : taskNode.path("meta"),
                root == null ? null : root.path("meta"),
                taskNode == null ? null : taskNode.path("data"),
                root == null ? null : root.path("data")
        };

        if (usageSummary.provider == null) {
            usageSummary.provider = firstNonBlankDeep(candidates, "provider", "llm_provider", "vendor", "backend");
        }
        if (usageSummary.model == null) {
            usageSummary.model = firstNonBlankDeep(candidates, "model", "model_name", "llm_model", "model_id");
        }
        if (usageSummary.currency == null) {
            usageSummary.currency = firstNonBlankDeep(candidates, "currency", "cost_currency");
        }
        if (usageSummary.cost == null) {
            usageSummary.cost = extractDecimalDeep(candidates, "cost", "total_cost", "usd_cost", "estimated_cost");
        }

        JsonNode usageNode = pickFirstObjectDeep(candidates, "usage", "token_usage", "llm_usage", "usage_summary", "token_stats");
        if (usageNode != null && usageSummary.rawUsageJson == null) {
            usageSummary.rawUsageJson = usageNode.toString();
        }

        if (usageSummary.inputTokens == null) {
            usageSummary.inputTokens = extractLongDeep(
                    usageNode, "prompt_tokens", "input_tokens", "request_tokens", "prompt_token_count", "input_token_count");
            if (usageSummary.inputTokens == null) {
                usageSummary.inputTokens = extractLongDeep(
                        candidates, "prompt_tokens", "input_tokens", "request_tokens", "prompt_token_count", "input_token_count");
            }
        }
        if (usageSummary.outputTokens == null) {
            usageSummary.outputTokens = extractLongDeep(
                    usageNode, "completion_tokens", "output_tokens", "response_tokens", "completion_token_count", "output_token_count");
            if (usageSummary.outputTokens == null) {
                usageSummary.outputTokens = extractLongDeep(
                        candidates, "completion_tokens", "output_tokens", "response_tokens", "completion_token_count", "output_token_count");
            }
        }
        if (usageSummary.totalTokens == null) {
            usageSummary.totalTokens = extractLongDeep(
                    usageNode, "total_tokens", "token_total", "total_token_count");
            if (usageSummary.totalTokens == null) {
                usageSummary.totalTokens = extractLongDeep(
                        candidates, "total_tokens", "token_total", "total_token_count");
            }
            if (usageSummary.totalTokens == null) {
                usageSummary.totalTokens = safeSum(usageSummary.inputTokens, usageSummary.outputTokens);
            }
        }
    }

    private void applyUsageSummary(FillTask task, String remoteTaskId, RemoteUsageSummary usageSummary) {
        task.setAiRemoteTaskId(remoteTaskId);
        if (usageSummary == null) {
            return;
        }

        task.setAiProvider(usageSummary.provider);
        task.setAiModel(usageSummary.model);
        task.setInputTokens(usageSummary.inputTokens);
        task.setOutputTokens(usageSummary.outputTokens);
        task.setTotalTokens(usageSummary.totalTokens);
        task.setAiUsageRaw(usageSummary.rawUsageJson);

        BigDecimal aiCost = usageSummary.cost;
        boolean estimated = false;
        if (aiCost == null) {
            aiCost = estimateCost(usageSummary.inputTokens, usageSummary.outputTokens);
            estimated = aiCost != null;
        }
        task.setAiCost(aiCost);
        task.setAiCostCurrency(
                usageSummary.currency == null || usageSummary.currency.isBlank()
                        ? defaultCostCurrency
                        : usageSummary.currency
        );
        task.setAiCostEstimated(estimated ? Boolean.TRUE : (usageSummary.cost == null ? null : Boolean.FALSE));
    }

    private void persistRemoteTaskId(FillTask task, String remoteTaskId) {
        if (task == null || task.getId() == null || remoteTaskId == null || remoteTaskId.isBlank()) {
            return;
        }
        task.setAiRemoteTaskId(remoteTaskId);
        try {
            int updated = fillTaskMapper.updateById(task);
            // fill_task 使用乐观锁：SQL 会 version+1，内存中的 task.version 必须同步，否则 MQ 消费末尾的
            // updateById(SUCCESS/resultFilePath) 会因 version 不匹配而更新 0 行，任务永远停在 RUNNING。
            if (updated > 0) {
                Long v = task.getVersion();
                task.setVersion(v == null ? 1L : v + 1L);
            }
        } catch (Exception e) {
            log.warn("Persist ai remote task id failed, taskId={}, remoteTaskId={}", task.getId(), remoteTaskId, e);
        }
    }

    private BigDecimal estimateCost(Long inputTokens, Long outputTokens) {
        if ((inputTokens == null || inputTokens <= 0) && (outputTokens == null || outputTokens <= 0)) {
            return null;
        }

        BigDecimal inputCost = BigDecimal.ZERO;
        BigDecimal outputCost = BigDecimal.ZERO;
        if (inputTokens != null && inputTokens > 0
                && defaultInputPer1k != null && defaultInputPer1k.compareTo(BigDecimal.ZERO) > 0) {
            inputCost = BigDecimal.valueOf(inputTokens)
                    .multiply(defaultInputPer1k)
                    .divide(BigDecimal.valueOf(1000), 8, RoundingMode.HALF_UP);
        }
        if (outputTokens != null && outputTokens > 0
                && defaultOutputPer1k != null && defaultOutputPer1k.compareTo(BigDecimal.ZERO) > 0) {
            outputCost = BigDecimal.valueOf(outputTokens)
                    .multiply(defaultOutputPer1k)
                    .divide(BigDecimal.valueOf(1000), 8, RoundingMode.HALF_UP);
        }

        BigDecimal total = inputCost.add(outputCost);
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return total.setScale(8, RoundingMode.HALF_UP);
    }

    private JsonNode pickFirstObject(JsonNode node, String... keys) {
        if (node == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode child = node.path(key);
            if (!child.isMissingNode() && !child.isNull() && child.isObject()) {
                return child;
            }
        }
        return null;
    }

    private String firstNonBlankText(JsonNode node, String... keys) {
        if (node == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode child = node.path(key);
            if (!child.isMissingNode() && !child.isNull()) {
                String text = child.asText("");
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private Long extractLong(JsonNode node, String... keys) {
        if (node == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode child = node.path(key);
            if (!child.isMissingNode() && !child.isNull()) {
                if (child.isNumber()) {
                    return child.longValue();
                }
                try {
                    String text = child.asText("");
                    if (text != null && !text.isBlank()) {
                        return Long.parseLong(text);
                    }
                } catch (Exception ignore) {
                    // ignore
                }
            }
        }
        return null;
    }

    private String firstNonBlankDeep(JsonNode[] nodes, String... keys) {
        if (nodes == null) {
            return null;
        }
        for (JsonNode node : nodes) {
            String found = firstNonBlankDeep(node, keys);
            if (found != null && !found.isBlank()) {
                return found;
            }
        }
        return null;
    }

    private String firstNonBlankDeep(JsonNode node, String... keys) {
        if (node == null || keys == null || keys.length == 0) {
            return null;
        }
        Set<String> keySet = normalizeKeySet(keys);
        Deque<JsonNode> queue = new ArrayDeque<>();
        queue.add(node);
        while (!queue.isEmpty()) {
            JsonNode cur = queue.pollFirst();
            if (cur == null || cur.isNull()) {
                continue;
            }
            if (cur.isObject()) {
                for (String k : keys) {
                    JsonNode v = cur.get(k);
                    if (v != null && !v.isNull()) {
                        String text = v.asText("");
                        if (text != null && !text.isBlank()) {
                            return text;
                        }
                    }
                }
                cur.fields().forEachRemaining(e -> {
                    String key = e.getKey() == null ? "" : e.getKey().toLowerCase(Locale.ROOT);
                    if (keySet.contains(key)) {
                        JsonNode v = e.getValue();
                        if (v != null && !v.isNull() && !v.isContainerNode()) {
                            String text = v.asText("");
                            if (text != null && !text.isBlank()) {
                                queue.addFirst(v);
                            }
                        }
                    }
                    queue.addLast(e.getValue());
                });
            } else if (cur.isArray()) {
                cur.forEach(queue::addLast);
            }
        }
        return null;
    }

    private Long extractLongDeep(JsonNode node, String... keys) {
        if (node == null || keys == null || keys.length == 0) {
            return null;
        }
        Set<String> keySet = normalizeKeySet(keys);
        Deque<JsonNode> queue = new ArrayDeque<>();
        queue.add(node);
        while (!queue.isEmpty()) {
            JsonNode cur = queue.pollFirst();
            if (cur == null || cur.isNull()) {
                continue;
            }
            Long direct = extractLong(cur, keys);
            if (direct != null) {
                return direct;
            }
            if (cur.isObject()) {
                cur.fields().forEachRemaining(e -> {
                    String key = e.getKey() == null ? "" : e.getKey().toLowerCase(Locale.ROOT);
                    JsonNode v = e.getValue();
                    if (keySet.contains(key) && v != null && !v.isNull()) {
                        queue.addFirst(v);
                    } else {
                        queue.addLast(v);
                    }
                });
            } else if (cur.isArray()) {
                cur.forEach(queue::addLast);
            }
        }
        return null;
    }

    private Long extractLongDeep(JsonNode[] nodes, String... keys) {
        if (nodes == null) {
            return null;
        }
        for (JsonNode node : nodes) {
            Long found = extractLongDeep(node, keys);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private BigDecimal extractDecimalDeep(JsonNode[] nodes, String... keys) {
        if (nodes == null) {
            return null;
        }
        for (JsonNode node : nodes) {
            BigDecimal found = extractDecimalDeep(node, keys);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private BigDecimal extractDecimalDeep(JsonNode node, String... keys) {
        if (node == null || keys == null || keys.length == 0) {
            return null;
        }
        Set<String> keySet = normalizeKeySet(keys);
        Deque<JsonNode> queue = new ArrayDeque<>();
        queue.add(node);
        while (!queue.isEmpty()) {
            JsonNode cur = queue.pollFirst();
            if (cur == null || cur.isNull()) {
                continue;
            }
            BigDecimal direct = extractDecimal(cur, keys);
            if (direct != null) {
                return direct;
            }
            if (cur.isObject()) {
                cur.fields().forEachRemaining(e -> {
                    String key = e.getKey() == null ? "" : e.getKey().toLowerCase(Locale.ROOT);
                    JsonNode v = e.getValue();
                    if (keySet.contains(key) && v != null && !v.isNull()) {
                        queue.addFirst(v);
                    } else {
                        queue.addLast(v);
                    }
                });
            } else if (cur.isArray()) {
                cur.forEach(queue::addLast);
            }
        }
        return null;
    }

    private JsonNode pickFirstObjectDeep(JsonNode[] nodes, String... keys) {
        if (nodes == null) {
            return null;
        }
        for (JsonNode node : nodes) {
            JsonNode found = pickFirstObjectDeep(node, keys);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private JsonNode pickFirstObjectDeep(JsonNode node, String... keys) {
        if (node == null || keys == null || keys.length == 0) {
            return null;
        }
        Set<String> keySet = normalizeKeySet(keys);
        Deque<JsonNode> queue = new ArrayDeque<>();
        queue.add(node);
        while (!queue.isEmpty()) {
            JsonNode cur = queue.pollFirst();
            if (cur == null || cur.isNull()) {
                continue;
            }
            JsonNode direct = pickFirstObject(cur, keys);
            if (direct != null) {
                return direct;
            }
            if (cur.isObject()) {
                cur.fields().forEachRemaining(e -> {
                    String key = e.getKey() == null ? "" : e.getKey().toLowerCase(Locale.ROOT);
                    JsonNode v = e.getValue();
                    if (keySet.contains(key) && v != null && v.isObject()) {
                        queue.addFirst(v);
                    } else {
                        queue.addLast(v);
                    }
                });
            } else if (cur.isArray()) {
                cur.forEach(queue::addLast);
            }
        }
        return null;
    }

    private Set<String> normalizeKeySet(String... keys) {
        return new HashSet<>(Arrays.stream(keys)
                .filter(k -> k != null && !k.isBlank())
                .map(k -> k.toLowerCase(Locale.ROOT))
                .toList());
    }

    private BigDecimal extractDecimal(JsonNode node, String... keys) {
        if (node == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode child = node.path(key);
            if (!child.isMissingNode() && !child.isNull()) {
                try {
                    if (child.isNumber()) {
                        return child.decimalValue();
                    }
                    String text = child.asText("");
                    if (text != null && !text.isBlank()) {
                        return new BigDecimal(text);
                    }
                } catch (Exception ignore) {
                    // ignore
                }
            }
        }
        return null;
    }

    private Long safeSum(Long a, Long b) {
        if (a == null && b == null) {
            return null;
        }
        long av = a == null ? 0L : a;
        long bv = b == null ? 0L : b;
        return av + bv;
    }

    private <T> ResponseEntity<T> executeWithRetry(String operationName, Supplier<ResponseEntity<T>> call) {
        int maxAttempts = Math.max(1, apiRetryMaxAttempts);
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return call.get();
            } catch (RuntimeException e) {
                lastException = e;
                if (!isRetryableException(e) || attempt >= maxAttempts) {
                    throw e;
                }
                long delayMs = computeBackoffWithJitterMs(attempt);
                log.warn("AI API retry, op={}, attempt={}/{}, reason={}, nextDelayMs={}",
                        operationName, attempt, maxAttempts, shortRetryReason(e), delayMs);
                sleepMillis(delayMs);
            }
        }
        throw lastException == null ? new RuntimeException("AI API 调用失败: " + operationName) : lastException;
    }

    private boolean isRetryableException(Throwable e) {
        if (e instanceof HttpStatusCodeException hsce) {
            int code = hsce.getStatusCode().value();
            return code == 429 || code >= 500;
        }
        return e instanceof ResourceAccessException;
    }

    private String shortRetryReason(Throwable e) {
        if (e instanceof HttpStatusCodeException hsce) {
            return "HTTP_" + hsce.getStatusCode().value();
        }
        if (e instanceof ResourceAccessException) {
            return "NETWORK_" + e.getClass().getSimpleName();
        }
        return e.getClass().getSimpleName();
    }

    private long computeBackoffWithJitterMs(int attempt) {
        long base = Math.max(100L, apiRetryBaseDelayMs);
        long maxBackoff = Math.max(base, apiRetryMaxBackoffMs);
        long exp = base;
        for (int i = 1; i < attempt; i++) {
            if (exp >= maxBackoff / 2) {
                exp = maxBackoff;
                break;
            }
            exp = exp * 2;
        }
        exp = Math.min(exp, maxBackoff);
        long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1L, exp / 3));
        return Math.min(maxBackoff, exp + jitter);
    }

    private void sleepMillis(long delayMs) {
        try {
            Thread.sleep(Math.max(0L, delayMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.AI_POLL_INTERRUPTED);
        }
    }

    private DownloadedResult downloadRemoteResult(String remoteTaskId, String baseName, String[] candidateKinds) {
        Exception last = null;
        long deadline = System.currentTimeMillis() + Math.max(15_000L, Math.min(pollTimeoutMs, 90_000L));
        while (System.currentTimeMillis() < deadline) {
            for (String kind : resolveDownloadCandidateKinds(remoteTaskId, candidateKinds)) {
                try {
                    String url = aiBaseUrl + String.format(downloadPathFormat, remoteTaskId, kind);
                    HttpHeaders headers = new HttpHeaders();
                    HttpEntity<Void> request = new HttpEntity<>(headers);
                    ResponseEntity<byte[]> response = executeWithRetry(
                        "downloadRemoteResult/" + kind,
                        () -> restTemplate.exchange(url, HttpMethod.GET, request, byte[].class)
                );
                    if (!response.getStatusCode().is2xxSuccessful()
                            || response.getBody() == null
                            || response.getBody().length == 0) {
                        continue;
                    }

                    Path resultsDir = Paths.get(uploadProperties.getResultsDir());
                    Files.createDirectories(resultsDir);
                    String safeBaseName = (baseName == null || baseName.isBlank()) ? "result" : baseName;
                    safeBaseName = withSuffixByKind(safeBaseName, kind);
                    String localFileName = "fill_" + remoteTaskId + "_" + UUID.randomUUID().toString().substring(0, 8)
                            + "_" + safeBaseName;
                    Path out = resultsDir.resolve(localFileName).normalize();
                    if (!out.startsWith(resultsDir.normalize())) {
                        throw new BusinessException(ErrorCode.AI_DOWNLOAD_PATH_INVALID);
                    }
                    Files.write(out, response.getBody());
                    return new DownloadedResult(localFileName, kind);
                } catch (Exception e) {
                    last = e;
                }
            }
            long remain = deadline - System.currentTimeMillis();
            if (remain <= 0) {
                break;
            }
            sleepMillis(Math.min(1_500L, Math.max(400L, remain)));
        }
        throw new BusinessException(
                ErrorCode.AI_DOWNLOAD_FAILED,
                "AI 结果下载失败: " + (last == null ? "无可用输出文件" : last.getMessage())
        );
    }

    private List<String> resolveDownloadCandidateKinds(String remoteTaskId, String[] candidateKinds) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        ordered.addAll(fetchAdvertisedRemoteKinds(remoteTaskId, candidateKinds));
        if (candidateKinds != null) {
            ordered.addAll(Arrays.asList(candidateKinds));
        }
        return List.copyOf(ordered);
    }

    private Set<String> fetchAdvertisedRemoteKinds(String remoteTaskId, String[] candidateKinds) {
        if (remoteTaskId == null || remoteTaskId.isBlank()) {
            return Set.of();
        }
        try {
            String statusUrl = aiBaseUrl + String.format(statusPathFormat, remoteTaskId);
            ResponseEntity<String> response = executeWithRetry(
                    "downloadRemoteResult/status",
                    () -> restTemplate.getForEntity(statusUrl, String.class)
            );
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
                return Set.of();
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode outputFiles = root.path("output_files");
            if (!outputFiles.isObject() || candidateKinds == null || candidateKinds.length == 0) {
                return Set.of();
            }

            LinkedHashSet<String> present = new LinkedHashSet<>();
            for (String kind : candidateKinds) {
                if (nonBlankTextNode(outputFiles.get(kind))) {
                    present.add(kind);
                }
            }

            JsonNode byInput = outputFiles.path("by_input");
            if (byInput.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> it = byInput.fields();
                while (it.hasNext()) {
                    JsonNode group = it.next().getValue();
                    if (!group.isObject()) {
                        continue;
                    }
                    for (String kind : candidateKinds) {
                        if (nonBlankTextNode(group.get(kind))) {
                            present.add(kind);
                        }
                    }
                }
            }
            return present;
        } catch (Exception e) {
            log.debug("读取 AI 可下载输出失败, remoteTaskId={}", remoteTaskId, e);
            return Set.of();
        }
    }

    private String withSuffixByKind(String baseName, String kind) {
        if (baseName == null || baseName.isBlank()) {
            baseName = "result";
        }
        String ext = switch (kind == null ? "" : kind.toLowerCase()) {
            case "excel", "result_xlsx" -> ".xlsx";
            case "docx", "result_docx" -> ".docx";
            case "json", "result_json", "report_bundle" -> ".json";
            default -> "";
        };
        if (ext.isBlank()) {
            return baseName;
        }
        String lower = baseName.toLowerCase();
        if (lower.endsWith(ext)) {
            return baseName;
        }
        int dot = baseName.lastIndexOf('.');
        if (dot > 0 && dot < baseName.length() - 1) {
            return baseName.substring(0, dot) + ext;
        }
        return baseName + ext;
    }

    private void sleepPoll() {
        try {
            Thread.sleep(pollIntervalMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.AI_POLL_INTERRUPTED);
        }
    }

    private void mirrorAdditionalRemoteResults(String remoteTaskId, String primaryKind) {
        try {
            JsonNode outputFiles = fetchRemoteOutputFiles(remoteTaskId);
            if (outputFiles == null || !outputFiles.isObject()) {
                return;
            }
            Set<String> copiedSourcePaths = new LinkedHashSet<>();
            String primaryPath = primaryKind == null ? null : textOrNull(outputFiles.get(primaryKind));
            if (primaryPath != null) {
                copiedSourcePaths.add(normalizeRemoteSourcePath(primaryPath));
            }
            JsonNode byInput = outputFiles.path("by_input");
            if (!byInput.isObject()) {
                return;
            }
            Iterator<Map.Entry<String, JsonNode>> it = byInput.fields();
            while (it.hasNext()) {
                JsonNode group = it.next().getValue();
                if (!group.isObject()) {
                    continue;
                }
                for (String key : REMOTE_OUTPUT_FILE_KEYS) {
                    String remotePath = textOrNull(group.get(key));
                    if (remotePath == null) {
                        continue;
                    }
                    String normalized = normalizeRemoteSourcePath(remotePath);
                    if (!copiedSourcePaths.add(normalized)) {
                        continue;
                    }
                    mirrorRemoteResultFile(remoteTaskId, remotePath);
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("鏄犲皠 AI 澶氱粨鏋滃埌鏈湴澶辫触, remoteTaskId={}", remoteTaskId, e);
        }
    }

    private JsonNode fetchRemoteOutputFiles(String remoteTaskId) throws Exception {
        if (remoteTaskId == null || remoteTaskId.isBlank()) {
            return null;
        }
        String statusUrl = aiBaseUrl + String.format(statusPathFormat, remoteTaskId);
        ResponseEntity<String> response = executeWithRetry(
                "mirrorAdditionalRemoteResults/status",
                () -> restTemplate.getForEntity(statusUrl, String.class)
        );
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
            return null;
        }
        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode outputFiles = root.path("output_files");
        return outputFiles.isObject() ? outputFiles : null;
    }

    private void mirrorRemoteResultFile(String remoteTaskId, String remotePath) throws Exception {
        Path source = resolveRemoteResultSourcePath(remotePath);
        if (source == null || !Files.exists(source) || !Files.isRegularFile(source)) {
            return;
        }
        Path resultsDir = Paths.get(uploadProperties.getResultsDir()).normalize();
        Files.createDirectories(resultsDir);
        String sourceName = source.getFileName().toString();
        String localFileName = "fill_" + remoteTaskId + "_" + UUID.randomUUID().toString().substring(0, 8)
                + "_" + sourceName;
        Path out = resultsDir.resolve(localFileName).normalize();
        if (!out.startsWith(resultsDir)) {
            throw new BusinessException(ErrorCode.AI_DOWNLOAD_PATH_INVALID);
        }
        Files.copy(source, out, StandardCopyOption.REPLACE_EXISTING);
    }

    private Path resolveRemoteResultSourcePath(String remotePath) {
        if (remotePath == null || remotePath.isBlank()) {
            return null;
        }
        Path path = Paths.get(remotePath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        Path candidate = cwd.resolve(path).normalize();
        if (Files.exists(candidate)) {
            return candidate;
        }
        Path aiWorkspace = cwd.resolve("Respond in 90 seconds_A23").resolve(path).normalize();
        if (Files.exists(aiWorkspace)) {
            return aiWorkspace;
        }
        return candidate;
    }

    private static String textOrNull(JsonNode node) {
        return nonBlankTextNode(node) ? node.asText() : null;
    }

    private static String normalizeRemoteSourcePath(String remotePath) {
        return remotePath == null ? "" : remotePath.replace('/', '\\').trim().toLowerCase(Locale.ROOT);
    }

    private record DownloadedResult(String localFileName, String kind) {
    }

    private static class RemoteUsageSummary {
        private String provider;
        private String model;
        private Long inputTokens;
        private Long outputTokens;
        private Long totalTokens;
        private BigDecimal cost;
        private String currency;
        private String rawUsageJson;
    }
}
