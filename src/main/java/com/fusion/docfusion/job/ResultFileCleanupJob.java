package com.fusion.docfusion.job;

import com.fusion.docfusion.config.UploadProperties;
import com.fusion.docfusion.entity.FillTask;
import com.fusion.docfusion.mapper.FillTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 结果文件生命周期清理任务。
 * 按保留天数扫描已完成任务，删除历史结果文件并将 task.resultFilePath 置空。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResultFileCleanupJob {

    private final FillTaskMapper fillTaskMapper;
    private final UploadProperties uploadProperties;

    @Value("${app.cleanup.enabled:true}")
    private boolean cleanupEnabled;

    @Value("${app.cleanup.retention-days:30}")
    private int retentionDays;

    @Value("${app.cleanup.batch-size:200}")
    private int batchSize;

    /**
     * 每天凌晨 3 点执行一次，避免业务高峰期扫盘。
     */
    @Scheduled(cron = "${app.cleanup.cron:0 0 3 * * ?}")
    public void cleanupExpiredResultFiles() {
        if (!cleanupEnabled) {
            return;
        }
        if (retentionDays <= 0) {
            log.warn("结果文件清理已启用但 retentionDays={} 非法，跳过本次执行", retentionDays);
            return;
        }

        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        List<FillTask> tasks = fillTaskMapper.selectExpiredResultTasks(cutoff, batchSize);
        if (tasks == null || tasks.isEmpty()) {
            return;
        }

        Path resultsDir = Paths.get(uploadProperties.getResultsDir()).normalize();
        int cleaned = 0;
        int failed = 0;

        for (FillTask task : tasks) {
            String resultFilePath = task.getResultFilePath();
            if (resultFilePath == null || resultFilePath.isBlank()) {
                continue;
            }

            try {
                Path filePath = resultsDir.resolve(resultFilePath).normalize();
                if (!filePath.startsWith(resultsDir)) {
                    log.warn("跳过清理：任务结果文件路径越界, taskId={}, path={}", task.getId(), filePath);
                    failed++;
                    continue;
                }
                Files.deleteIfExists(filePath);

                String msg = "结果文件已按生命周期策略清理（保留 " + retentionDays + " 天）";
                fillTaskMapper.markResultExpired(task.getId(), msg);
                cleaned++;
            } catch (Exception e) {
                failed++;
                log.warn("清理任务结果文件失败, taskId={}, resultFilePath={}, err={}",
                        task.getId(), resultFilePath, e.getMessage());
            }
        }

        log.info("结果文件清理完成, scanned={}, cleaned={}, failed={}, cutoff={}",
                tasks.size(), cleaned, failed, cutoff);
    }
}

