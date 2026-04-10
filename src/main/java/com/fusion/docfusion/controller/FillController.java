package com.fusion.docfusion.controller;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.config.UploadProperties;
import com.fusion.docfusion.dto.FillRequest;
import com.fusion.docfusion.dto.FillTaskListPageVO;
import com.fusion.docfusion.dto.FillTaskVO;
import com.fusion.docfusion.dto.FreeFillRequest;
import com.fusion.docfusion.entity.DocumentSet;
import com.fusion.docfusion.entity.Template;
import com.fusion.docfusion.enums.TaskStatus;
import com.fusion.docfusion.mapper.DocumentSetMapper;
import com.fusion.docfusion.mapper.TemplateMapper;
import com.fusion.docfusion.service.FillService;
import com.fusion.docfusion.sse.FillTaskSseBroker;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 填表任务：提交填表、查询任务、下载结果文件
 */
@RestController
@RequestMapping("/api/fill")
@RequiredArgsConstructor
@Slf4j
public class FillController {

    private final FillService fillService;
    private final UploadProperties uploadProperties;
    private final DocumentSetMapper documentSetMapper;
    private final TemplateMapper templateMapper;
    private final FillTaskSseBroker sseBroker;

    /**
     * 提交填表任务（同步执行，比赛要求单次 ≤90 秒）
     * POST /api/fill/submit
     */
    @PostMapping("/submit")
    public Result<FillTaskVO> submitFill(@RequestBody @Valid FillRequest request) {
        log.info("提交填表任务, documentSetId={}, templateId={}", request.getDocumentSetId(), request.getTemplateId());
        return fillService.submitFill(request);
    }

    /**
     * 自由模式：根据文档集 + 用户需求，生成临时汇总表
     * POST /api/fill/free
     */
    @PostMapping("/free")
    public Result<FillTaskVO> submitFree(@RequestBody @Valid FreeFillRequest request) {
        log.info("提交自由模式填表任务, documentSetId={}", request.getDocumentSetId());
        return fillService.submitFree(request);
    }

    /**
     * 按 publicId 查询任务（用于防枚举）。
     * GET /api/fill/tasks/public/{taskPublicId}
     */
    @GetMapping("/tasks/public/{taskPublicId}")
    public Result<FillTaskVO> getTaskByPublicId(@PathVariable String taskPublicId) {
        log.info("查询填表任务(公共ID), taskPublicId={}", taskPublicId);
        return fillService.getTaskByPublicId(taskPublicId);
    }

    /**
     * 订阅任务进度事件（SSE）。
     * 前端可使用 EventSource 连接：GET /api/fill/tasks/public/{taskPublicId}/events
     *
     * 事件流会包含：
     * - INIT：连接建立后立即发送一次当前任务快照（FillTaskVO）
     * - TASK_STATUS：任务状态变化（RUNNING/SUCCESS/FAILED/TIMEOUT/CANCELLED）
     * - STEP_UPSERT：步骤更新（开始/结束/失败/跳过等）
     * - HEARTBEAT：心跳
     */
    @GetMapping(value = "/tasks/public/{taskPublicId}/events", produces = "text/event-stream")
    public SseEmitter streamTaskEvents(@PathVariable String taskPublicId,
                                       @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        log.info("订阅任务进度事件, taskPublicId={}, lastEventId={}", taskPublicId, lastEventId);
        SseEmitter emitter = sseBroker.subscribe(taskPublicId, lastEventId);
        // 连接建立后先推一次快照，便于前端首次渲染（即使后续没有新事件也能展示当前状态）
        try {
            Result<FillTaskVO> snapshot = fillService.getTaskByPublicId(taskPublicId);
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data(snapshot.getData(), MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            // INIT 失败不影响连接，后续事件仍可到达
            log.warn("发送 INIT 快照失败, taskPublicId={}", taskPublicId, e);
        }
        return emitter;
    }

    /**
     * 按 publicId 人工重跑
     * POST /api/fill/tasks/public/{taskPublicId}/rerun
     */
    @PostMapping("/tasks/public/{taskPublicId}/rerun")
    public Result<FillTaskVO> rerunTaskByPublicId(@PathVariable String taskPublicId) {
        log.info("人工重跑填表任务(公共ID), taskPublicId={}", taskPublicId);
        return fillService.rerunTaskByPublicId(taskPublicId);
    }

    /**
     * 按 publicId 取消任务
     * POST /api/fill/tasks/public/{taskPublicId}/cancel
     */
    @PostMapping("/tasks/public/{taskPublicId}/cancel")
    public Result<FillTaskVO> cancelTaskByPublicId(@PathVariable String taskPublicId) {
        log.info("取消填表任务(公共ID), taskPublicId={}", taskPublicId);
        return fillService.cancelTaskByPublicId(taskPublicId);
    }

    /**
     * 查询任务列表（支持按模式和状态简单筛选）
     * GET /api/fill/tasks?mode=TEMPLATE&status=SUCCESS&page=1&size=20
     */
    @GetMapping("/tasks")
    public Result<FillTaskListPageVO> listTasks(@RequestParam(value = "mode", required = false) String mode,
                                                @RequestParam(value = "status", required = false) String status,
                                                @RequestParam(value = "page", required = false) Integer page,
                                                @RequestParam(value = "size", required = false) Integer size) {
        log.info("查询填表任务列表, mode={}, status={}, page={}, size={}", mode, status, page, size);
        return fillService.listTasks(mode, status, page, size);
    }

    /**
     * 按 publicId 下载填表结果
     * GET /api/fill/download/public/{taskPublicId}
     */
    @GetMapping("/download/public/{taskPublicId}")
    public void downloadResultByPublicId(@PathVariable String taskPublicId, HttpServletResponse response) {
        log.info("下载填表结果(公共ID), taskPublicId={}", taskPublicId);
        FillTaskVO task = fillService.getTaskByPublicId(taskPublicId).getData();
        if (task == null || !TaskStatus.SUCCESS.name().equals(task.getStatus()) || task.getResultFilePath() == null) {
            log.warn("下载失败：任务未成功或无结果文件, taskPublicId={}, status={}, resultFilePath={}",
                    taskPublicId, task == null ? null : task.getStatus(), task == null ? null : task.getResultFilePath());
            response.setStatus(404);
            return;
        }
        Path resultsDir = Paths.get(uploadProperties.getResultsDir());
        Path normalizedResultsDir = resultsDir.normalize();
        Path filePath = resultsDir.resolve(task.getResultFilePath()).normalize();
        if (!filePath.startsWith(normalizedResultsDir)) {
            log.warn("下载失败：结果文件落点不在结果目录内, taskPublicId={}, path={}",
                    taskPublicId, filePath);
            response.setStatus(404);
            return;
        }
        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                log.warn("下载失败：文件不存在或不可读, taskPublicId={}, path={}", taskPublicId, filePath);
                response.setStatus(404);
                return;
            }
            String resultFilePath = task.getResultFilePath();
            String filename = resultFilePath;
            int lastSlash = filename.lastIndexOf('/');
            int lastBackslash = filename.lastIndexOf('\\');
            int idx = Math.max(lastSlash, lastBackslash);
            if (idx >= 0) filename = filename.substring(idx + 1);

            String contentType = resolveContentTypeByFilename(resultFilePath);
            String preferredFilename = buildPreferredFilename(task, filename);
            String asciiFilename = buildAsciiFilename(taskPublicId, filename);
            String encodedPreferred = URLEncoder.encode(preferredFilename, StandardCharsets.UTF_8)
                    .replace("+", "%20");
            String simpleContentDisposition = "attachment; filename=\"" + asciiFilename + "\"; " +
                    "filename*=UTF-8''" + encodedPreferred;
            response.setStatus(200);
            response.setContentType(contentType);
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, simpleContentDisposition);

            long contentLength = resource.contentLength();
            if (contentLength >= 0) {
                response.setContentLengthLong(contentLength);
            }
            try (InputStream in = resource.getInputStream();
                 OutputStream out = response.getOutputStream()) {
                in.transferTo(out);
                out.flush();
            }
        } catch (Exception e) {
            log.error("下载失败：异常, taskPublicId={}", taskPublicId, e);
            response.setStatus(404);
        }
    }

    private static String resolveContentTypeByFilename(String filename) {
        if (filename == null) {
            return "application/octet-stream";
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if (lower.endsWith(".xls")) {
            return "application/vnd.ms-excel";
        }
        if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (lower.endsWith(".doc")) {
            return "application/msword";
        }
        if (lower.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        return "application/octet-stream";
    }

    private static String buildAsciiFilename(String taskPublicId, String rawFilename) {
        String name = rawFilename == null ? "" : rawFilename;
        String lower = name.toLowerCase();
        int dot = lower.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot) : ".bin";
        ext = ext.replaceAll("[^a-zA-Z0-9\\.]", "");
        if (ext.isBlank()) {
            ext = ".bin";
        }
        String safePublicId = taskPublicId == null ? "unknown" : taskPublicId.replaceAll("[^a-zA-Z0-9_-]", "");
        if (safePublicId.isBlank()) {
            safePublicId = "unknown";
        }
        return "fill_" + safePublicId + ext;
    }

    private String buildPreferredFilename(FillTaskVO task, String fallbackFilename) {
        String ext = extractExtensionOrBin(fallbackFilename);
        String docSetName = null;
        String templateName = null;
        if (task.getDocumentSetId() != null) {
            DocumentSet set = documentSetMapper.selectById(task.getDocumentSetId());
            if (set != null) {
                docSetName = set.getName();
            }
        }
        if (task.getTemplateId() != null) {
            Template template = templateMapper.selectById(task.getTemplateId());
            if (template != null) {
                templateName = stripExtension(template.getFileName());
            }
        }
        String base;
        if (templateName != null && !templateName.isBlank()) {
            base = sanitizeFilenamePart(templateName);
            if (docSetName != null && !docSetName.isBlank()) {
                base = base + "_" + sanitizeFilenamePart(docSetName);
            }
        } else if (docSetName != null && !docSetName.isBlank()) {
            base = "填表结果_" + sanitizeFilenamePart(docSetName);
        } else {
            base = "填表结果";
        }
        if (base.isBlank()) {
            base = "fill_result";
        }
        return base + ext;
    }

    private static String extractExtensionOrBin(String filename) {
        if (filename == null) {
            return ".bin";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return ".bin";
        }
        String ext = filename.substring(dot);
        ext = ext.replaceAll("[^a-zA-Z0-9\\.]", "");
        return ext.isBlank() ? ".bin" : ext;
    }

    private static String stripExtension(String filename) {
        if (filename == null) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        if (dot <= 0) {
            return filename;
        }
        return filename.substring(0, dot);
    }

    private static String sanitizeFilenamePart(String input) {
        if (input == null) {
            return "";
        }
        String value = input.trim()
                .replaceAll("[\\\\/:*?\"<>|]+", "_")
                .replaceAll("\\s+", " ");
        return value.length() > 80 ? value.substring(0, 80) : value;
    }

}
