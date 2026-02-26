package com.fusion.docfusion.controller;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.config.UploadProperties;
import com.fusion.docfusion.dto.FillRequest;
import com.fusion.docfusion.dto.FillTaskVO;
import com.fusion.docfusion.service.FillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
public class FillController {

    private final FillService fillService;
    private final UploadProperties uploadProperties;

    /**
     * 提交填表任务（同步执行，比赛要求单次 ≤90 秒）
     * POST /api/fill/submit
     */
    @PostMapping("/submit")
    public Result<FillTaskVO> submitFill(@RequestBody @Valid FillRequest request) {
        return fillService.submitFill(request);
    }

    /**
     * 查询任务状态与结果文件路径
     * GET /api/fill/tasks/{taskId}
     */
    @GetMapping("/tasks/{taskId}")
    public Result<FillTaskVO> getTask(@PathVariable Long taskId) {
        return fillService.getTask(taskId);
    }

    /**
     * 下载填表结果文件
     * GET /api/fill/download/{taskId}
     */
    @GetMapping("/download/{taskId}")
    public ResponseEntity<Resource> downloadResult(@PathVariable Long taskId) {
        FillTaskVO task = fillService.getTask(taskId).getData();
        if (task == null || !"SUCCESS".equals(task.getStatus()) || task.getResultFilePath() == null) {
            return ResponseEntity.notFound().build();
        }
        Path resultsDir = Paths.get(uploadProperties.getResultsDir());
        Path filePath = resultsDir.resolve(task.getResultFilePath());
        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
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
            return ResponseEntity.notFound().build();
        }
    }
}
