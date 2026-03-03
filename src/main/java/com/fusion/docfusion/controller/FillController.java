package com.fusion.docfusion.controller;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.config.UploadProperties;
import com.fusion.docfusion.dto.FillRequest;
import com.fusion.docfusion.dto.FillTaskVO;
import com.fusion.docfusion.dto.FreeFillRequest;
import com.fusion.docfusion.service.FillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;

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
     * 查询任务状态与结果文件路径
     * GET /api/fill/tasks/{taskId}
     */
    @GetMapping("/tasks/{taskId}")
    public Result<FillTaskVO> getTask(@PathVariable Long taskId) {
        log.info("查询填表任务, taskId={}", taskId);
        return fillService.getTask(taskId);
    }

    /**
     * 查询任务列表（支持按模式和状态简单筛选）
     * GET /api/fill/tasks?mode=TEMPLATE&status=SUCCESS&page=1&size=20
     */
    @GetMapping("/tasks")
    public Result<java.util.List<FillTaskVO>> listTasks(@RequestParam(value = "mode", required = false) String mode,
                                                        @RequestParam(value = "status", required = false) String status,
                                                        @RequestParam(value = "page", required = false) Integer page,
                                                        @RequestParam(value = "size", required = false) Integer size) {
        log.info("查询填表任务列表, mode={}, status={}, page={}, size={}", mode, status, page, size);
        return fillService.listTasks(mode, status, page, size);
    }

    /**
     * 下载填表结果文件
     * GET /api/fill/download/{taskId}
     */
    @GetMapping("/download/{taskId}")
    public ResponseEntity<Resource> downloadResult(@PathVariable Long taskId) {
        log.info("下载填表结果, taskId={}", taskId);
        FillTaskVO task = fillService.getTask(taskId).getData();
        if (task == null || !"SUCCESS".equals(task.getStatus()) || task.getResultFilePath() == null) {
            log.warn("下载失败：任务未成功或无结果文件, taskId={}, status={}, resultFilePath={}",
                    taskId, task == null ? null : task.getStatus(), task == null ? null : task.getResultFilePath());
            return ResponseEntity.notFound().build();
        }
        Path resultsDir = Paths.get(uploadProperties.getResultsDir());
        Path filePath = resultsDir.resolve(task.getResultFilePath());
        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                log.warn("下载失败：文件不存在或不可读, taskId={}, path={}", taskId, filePath);
                return ResponseEntity.notFound().build();
            }
            String contentType = "application/octet-stream";
            String filename = task.getResultFilePath();
            int lastSlash = filename.lastIndexOf('/');
            if (lastSlash >= 0) {
                filename = filename.substring(lastSlash + 1);
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);
        } catch (Exception e) {
            log.error("下载失败：异常, taskId={}", taskId, e);
            return ResponseEntity.notFound().build();
        }
    }
}
