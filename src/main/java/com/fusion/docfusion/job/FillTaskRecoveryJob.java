package com.fusion.docfusion.job;

import com.fusion.docfusion.config.RabbitConfig;
import com.fusion.docfusion.entity.FillTask;
import com.fusion.docfusion.enums.TaskStatus;
import com.fusion.docfusion.mapper.FillTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 启动恢复 + 定时补偿：
 * 扫描超时 RUNNING 任务，标记 TIMEOUT 后重新投递 MQ，避免重启后遗留僵尸任务。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FillTaskRecoveryJob {

    private final FillTaskMapper fillTaskMapper;
    private final AmqpTemplate amqpTemplate;

    @Value("${fill.task.recovery.enabled:true}")
    private boolean recoveryEnabled;
    @Value("${fill.task.recovery.startup-run:true}")
    private boolean startupRunEnabled;
    @Value("${fill.task.recovery.batch-size:200}")
    private int batchSize;
    @Value("${fill.task.running-timeout-minutes:20}")
    private int runningTimeoutMinutes;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        if (!startupRunEnabled) {
            return;
        }
        recoverStuckRunningTasks("startup");
    }

    @Scheduled(cron = "${fill.task.recovery.cron:0 */5 * * * ?}")
    public void recoverOnSchedule() {
        recoverStuckRunningTasks("scheduled");
    }

    private void recoverStuckRunningTasks(String trigger) {
        if (!recoveryEnabled) {
            return;
        }
        if (runningTimeoutMinutes <= 0) {
            log.warn("任务恢复已启用但 runningTimeoutMinutes={} 非法，跳过本次执行", runningTimeoutMinutes);
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(runningTimeoutMinutes);
        List<FillTask> candidates = fillTaskMapper.selectRunningTimeoutTasks(cutoff, batchSize);
        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        int marked = 0;
        int requeued = 0;
        int skipped = 0;
        for (FillTask task : candidates) {
            if (task == null || task.getId() == null) {
                continue;
            }
            String message = "检测到 RUNNING 超时，已由恢复任务自动补偿重试";
            int changed = fillTaskMapper.markRunningTimeout(
                    task.getId(),
                    TaskStatus.TIMEOUT.name(),
                    message
            );
            if (changed <= 0) {
                skipped++;
                continue;
            }
            marked++;
            amqpTemplate.convertAndSend(
                    RabbitConfig.FILL_TASK_EXCHANGE,
                    RabbitConfig.FILL_TASK_ROUTING_KEY,
                    task.getId()
            );
            requeued++;
        }

        log.info("任务恢复执行完成, trigger={}, scanned={}, markedTimeout={}, requeued={}, skipped={}, cutoff={}",
                trigger, candidates.size(), marked, requeued, skipped, cutoff);
    }
}
