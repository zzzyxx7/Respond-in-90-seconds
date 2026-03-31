package com.fusion.docfusion.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fusion.docfusion.config.UploadProperties;
import com.fusion.docfusion.entity.Document;
import com.fusion.docfusion.entity.FillTask;
import com.fusion.docfusion.entity.Template;
import com.fusion.docfusion.exception.BusinessException;
import com.fusion.docfusion.exception.ErrorCode;
import com.fusion.docfusion.mapper.TemplateMapper;
import com.fusion.docfusion.service.AiFillService;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * AI 填表适配（方案 B）：
 * 调用 A23 HTTP 任务接口：
 * 1) POST /api/tasks/create
 * 2) 轮询 GET /api/tasks/{task_id}
 * 3) 下载 GET /api/tasks/{task_id}/download/{kind}
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AiFillServiceImpl implements AiFillService {

    private final RestTemplate restTemplate;
    private final TemplateMapper templateMapper;
    private final UploadProperties uploadProperties;
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

    @Value("${ai.fill.poll-timeout-ms:180000}")
    private long pollTimeoutMs;

    @Value("${ai.fill.free-template-path:}")
    private String freeTemplatePath;

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

        String taskId = createRemoteTask(templateFile.toFile(), docs, task.getUserRequirement());
        waitForRemoteTask(taskId);
        DownloadedResult downloaded = downloadRemoteResult(taskId, template.getFileName(),
                new String[]{"result_xlsx", "result_docx", "report_bundle", "result_json"});
        task.setResultFilePath(downloaded.localFileName);
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
        File freeTemplate = resolveFreeTemplateFile();
        String taskId = createRemoteTask(freeTemplate, docs, task.getUserRequirement());
        waitForRemoteTask(taskId);
        DownloadedResult downloaded = downloadRemoteResult(taskId, "free_mode.xlsx",
                new String[]{"result_xlsx", "result_json", "report_bundle", "result_docx"});
        task.setResultFilePath(downloaded.localFileName);
        log.info("AI 自由模式完成并落盘, localFile={}, remoteKind={}", downloaded.localFileName, downloaded.kind);
    }

    private String createRemoteTask(File templateFile, List<Document> docs, String note) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("template", new FileSystemResource(templateFile));
        for (Document doc : docs) {
            File f = Paths.get(uploadProperties.getDocsDir()).resolve(doc.getFilePath()).toFile();
            if (!f.exists()) {
                throw new BusinessException(ErrorCode.AI_INPUT_DOC_MISSING, "输入文档不存在: " + doc.getFileName());
            }
            body.add("input_files", new FileSystemResource(f));
        }
        if (note != null && !note.isBlank()) {
            body.add("note", note);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> req = new HttpEntity<>(body, headers);
        String url = aiBaseUrl + createPath;
        log.info("调用 AI 任务创建接口, url={}, docs={}", url, docs.size());

        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(url, req, String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                throw new BusinessException(ErrorCode.AI_CREATE_REMOTE_FAILED, "AI 创建任务失败，HTTP: " + resp.getStatusCode());
            }
            JsonNode root = objectMapper.readTree(resp.getBody());
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

    private void waitForRemoteTask(String remoteTaskId) {
        String statusUrl = aiBaseUrl + String.format(statusPathFormat, remoteTaskId);
        long deadline = System.currentTimeMillis() + pollTimeoutMs;
        String lastStatus = "";
        while (System.currentTimeMillis() < deadline) {
            try {
                ResponseEntity<String> resp = restTemplate.getForEntity(statusUrl, String.class);
                if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                    sleepPoll();
                    continue;
                }
                JsonNode root = objectMapper.readTree(resp.getBody());
                JsonNode taskNode = root.path("task");
                String status = taskNode.path("status").asText("");
                lastStatus = status;
                if ("succeeded".equalsIgnoreCase(status)) {
                    return;
                }
                if ("failed".equalsIgnoreCase(status)) {
                    String err = taskNode.path("error").asText("");
                    throw new BusinessException(ErrorCode.AI_TASK_FAILED, "AI 填表任务失败: " + (err.isBlank() ? "unknown" : err));
                }
                sleepPoll();
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                sleepPoll();
            }
        }
        throw new BusinessException(ErrorCode.AI_TASK_TIMEOUT, "AI 填表任务超时(" + Duration.ofMillis(pollTimeoutMs).toSeconds() + "s), lastStatus=" + lastStatus);
    }

    private DownloadedResult downloadRemoteResult(String remoteTaskId, String baseName, String[] candidateKinds) {
        Exception last = null;
        for (String kind : candidateKinds) {
            try {
                String url = aiBaseUrl + String.format(downloadPathFormat, remoteTaskId, kind);
                HttpHeaders headers = new HttpHeaders();
                HttpEntity<Void> req = new HttpEntity<>(headers);
                ResponseEntity<byte[]> resp = restTemplate.exchange(url, HttpMethod.GET, req, byte[].class);
                if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null || resp.getBody().length == 0) {
                    continue;
                }

                Path resultsDir = Paths.get(uploadProperties.getResultsDir());
                Files.createDirectories(resultsDir);
                String safeBaseName = (baseName == null || baseName.isBlank()) ? "result" : baseName;
                String localFileName = "fill_" + remoteTaskId + "_" + UUID.randomUUID().toString().substring(0, 8) + "_" + safeBaseName;
                Path out = resultsDir.resolve(localFileName).normalize();
                if (!out.startsWith(resultsDir.normalize())) {
                    throw new BusinessException(ErrorCode.AI_DOWNLOAD_PATH_INVALID);
                }
                Files.write(out, resp.getBody());
                return new DownloadedResult(localFileName, kind);
            } catch (Exception e) {
                last = e;
            }
        }
        throw new BusinessException(ErrorCode.AI_DOWNLOAD_FAILED, "AI 结果下载失败: " + (last == null ? "无可用输出文件" : last.getMessage()));
    }

    private File resolveFreeTemplateFile() {
        if (freeTemplatePath == null || freeTemplatePath.isBlank()) {
            throw new BusinessException(ErrorCode.FREE_MODE_TEMPLATE_NOT_CONFIGURED);
        }
        Path p = Paths.get(freeTemplatePath);
        if (!p.isAbsolute()) {
            p = Paths.get(uploadProperties.getTemplatesDir()).resolve(p);
        }
        p = p.normalize();
        File f = p.toFile();
        if (!f.exists() || !f.isFile()) {
            throw new BusinessException(ErrorCode.FREE_MODE_TEMPLATE_MISSING, "自由模式默认模板不存在: " + p);
        }
        return f;
    }

    private void sleepPoll() {
        try {
            Thread.sleep(pollIntervalMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.AI_POLL_INTERRUPTED);
        }
    }

    private record DownloadedResult(String localFileName, String kind) {
    }
}
