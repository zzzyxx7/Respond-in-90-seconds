package com.fusion.docfusion.messaging;

import com.fusion.docfusion.config.RabbitConfig;
import com.fusion.docfusion.entity.Document;
import com.fusion.docfusion.entity.DocumentSet;
import com.fusion.docfusion.entity.FillTask;
import com.fusion.docfusion.entity.FillTaskStep;
import com.fusion.docfusion.entity.Template;
import com.fusion.docfusion.enums.TaskMode;
import com.fusion.docfusion.enums.TaskStatus;
import com.fusion.docfusion.exception.BusinessException;
import com.fusion.docfusion.exception.ErrorCode;
import com.fusion.docfusion.mapper.DocumentMapper;
import com.fusion.docfusion.mapper.DocumentSetMapper;
import com.fusion.docfusion.mapper.FillTaskMapper;
import com.fusion.docfusion.mapper.FillTaskStepMapper;
import com.fusion.docfusion.mapper.TemplateMapper;
import com.fusion.docfusion.service.AiFillService;
import com.fusion.docfusion.sse.FillTaskSseBroker;
import com.fusion.docfusion.sse.FillTaskStatusEvent;
import com.fusion.docfusion.sse.FillTaskStepEvent;
import com.fusion.docfusion.util.FillTaskCancelService;
import com.fusion.docfusion.util.RedisDistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class FillTaskConsumer {

    private static final String STEP_PREPARE = "PREPARE";
    private static final String STEP_AI_PROCESS = "AI_PROCESS";
    private static final String STEP_RESULT = "RESULT";

    private static final String STEP_STATUS_RUNNING = "RUNNING";
    private static final String STEP_STATUS_SUCCESS = "SUCCESS";
    private static final String STEP_STATUS_FAILED = "FAILED";

    private static final int MAX_RETRY_ATTEMPTS = 5;

    @Value("${fill.task.running-timeout-minutes:20}")
    private int runningTimeoutMinutes;

    @Value("${fill.task.lock-ttl-seconds:1800}")
    private long lockTtlSeconds;

    private final DocumentSetMapper documentSetMapper;
    private final DocumentMapper documentMapper;
    private final TemplateMapper templateMapper;
    private final FillTaskMapper fillTaskMapper;
    private final FillTaskStepMapper fillTaskStepMapper;
    private final AiFillService aiFillService;
    private final AmqpTemplate amqpTemplate;
    private final RedisDistributedLock distributedLock;
    private final FillTaskSseBroker sseBroker;
    private final FillTaskCancelService cancelService;

    @RabbitListener(queues = RabbitConfig.FILL_TASK_QUEUE)
    public void handleFillTask(
            @Payload Long taskId,
            com.rabbitmq.client.Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
            @Header(value = "x-death", required = false) List<Map<String, Object>> xDeath
    ) throws IOException {
        String lockKey = "fill:task:lock:" + taskId;
        String lockToken = distributedLock.tryLock(lockKey, Duration.ofSeconds(lockTtlSeconds));
        if (lockToken == null) {
            log.warn("task lock already held, skip consume, taskId={}", taskId);
            channel.basicAck(deliveryTag, false);
            return;
        }

        int attempts = extractAttemptsFromXDeath(xDeath, RabbitConfig.FILL_TASK_QUEUE);
        log.info("consume fill task, taskId={}, deliveryTag={}, attempts={}", taskId, deliveryTag, attempts);

        try {
            FillTask task = fillTaskMapper.selectById(taskId);
            if (task == null) {
                log.warn("task not found, taskId={}", taskId);
                channel.basicAck(deliveryTag, false);
                return;
            }
            if (cancelService.isCancelRequested(task.getPublicId())
                    || TaskStatus.CANCELLED.name().equalsIgnoreCase(task.getStatus())) {
                markCancelledIfNeeded(task);
                channel.basicAck(deliveryTag, false);
                return;
            }
            if (TaskStatus.SUCCESS.name().equalsIgnoreCase(task.getStatus())) {
                log.info("task already success, skip duplicate delivery, taskId={}", taskId);
                channel.basicAck(deliveryTag, false);
                return;
            }
            if (TaskStatus.CANCELLED.name().equalsIgnoreCase(task.getStatus())) {
                log.info("task already cancelled, skip consume, taskId={}", taskId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            if (TaskStatus.RUNNING.name().equalsIgnoreCase(task.getStatus())) {
                if (isRunningTimedOut(task)) {
                    log.warn("running task timeout detected, taskId={}, createdAt={}, timeoutMinutes={}",
                            taskId, task.getCreatedAt(), runningTimeoutMinutes);
                    task.setStatus(TaskStatus.TIMEOUT.name());
                    task.setErrorMessage("detected RUNNING timeout, auto-recovery triggered");
                    int timeoutUpdated = fillTaskMapper.updateById(task);
                    if (timeoutUpdated > 0) {
                        task.setVersion(nextVersion(task.getVersion()));
                    }
                    publishTaskStatusOrReload(taskId, task, timeoutUpdated);
                    // 必须结束：否则会继续 markRunning，把刚写入的 TIMEOUT 又改回 RUNNING 并重复执行
                    channel.basicAck(deliveryTag, false);
                    return;
                } else {
                    log.warn("duplicate delivery while task still running, skip consume, taskId={}", taskId);
                    channel.basicAck(deliveryTag, false);
                    return;
                }
            }

            task.setStatus(TaskStatus.RUNNING.name());
            task.setFinishedAt(null);
            task.setErrorMessage(null);
            task.setResultFilePath(null);
            int runningUpdated = fillTaskMapper.markRunning(task.getId(), TaskStatus.RUNNING.name(), task.getVersion());
            if (runningUpdated <= 0) {
                log.warn("task state update conflict, skip consume, taskId={}", taskId);
                channel.basicAck(deliveryTag, false);
                return;
            }
            task.setVersion(nextVersion(task.getVersion()));
            fillTaskStepMapper.deleteByTaskId(taskId);
            publishTaskStatus(task);

            if (TaskMode.TEMPLATE.name().equalsIgnoreCase(task.getMode())) {
                processTemplateTask(task);
            } else if (TaskMode.FREE.name().equalsIgnoreCase(task.getMode())) {
                processFreeTask(task);
            } else {
                throw new BusinessException(ErrorCode.TASK_MODE_UNKNOWN, "unknown task mode: " + task.getMode());
            }

            task.setStatus(TaskStatus.SUCCESS.name());
            task.setFinishedAt(LocalDateTime.now());
            FillTask latestTask = fillTaskMapper.selectById(taskId);
            if (latestTask != null && TaskStatus.CANCELLED.name().equalsIgnoreCase(latestTask.getStatus())) {
                log.info("task cancelled during processing, keep CANCELLED, taskId={}", taskId);
                channel.basicAck(deliveryTag, false);
                return;
            }
            int successUpdated = fillTaskMapper.updateById(task);
            if (successUpdated <= 0) {
                log.warn("task finish update conflict, keep latest status, taskId={}", taskId);
            }
            publishTaskStatusOrReload(taskId, task, successUpdated);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("consume fill task failed, taskId={}", taskId, e);

            try {
                FillTask task = fillTaskMapper.selectById(taskId);
                if (task != null) {
                    if (isCancelledException(e) || cancelService.isCancelRequested(task.getPublicId())) {
                        markCancelledIfNeeded(task);
                        throw e;
                    }
                    task.setStatus(resolveFailedStatus(e).name());
                    task.setFinishedAt(LocalDateTime.now());
                    task.setErrorMessage(truncateErrorMessage(e));
                    int failUpdated = fillTaskMapper.updateById(task);
                    publishTaskStatusOrReload(taskId, task, failUpdated);
                }
            } catch (Exception ignore) {
                // ignore secondary status update failures
            }

            if (attempts >= MAX_RETRY_ATTEMPTS) {
                log.error("message exceeded max retries, send to DLQ, taskId={}, attempts={}", taskId, attempts);
                amqpTemplate.convertAndSend(
                        RabbitConfig.FILL_TASK_DLX_EXCHANGE,
                        RabbitConfig.FILL_TASK_DLQ_ROUTING_KEY,
                        taskId
                );
                channel.basicAck(deliveryTag, false);
            } else {
                channel.basicNack(deliveryTag, false, false);
            }
        } finally {
            distributedLock.unlock(lockKey, lockToken);
        }
    }

    private void processTemplateTask(FillTask task) throws IOException {
        Long taskId = task.getId();
        String taskPublicId = task.getPublicId();
        Long documentSetId = task.getDocumentSetId();
        Long templateId = task.getTemplateId();

        LocalDateTime prepareStart = LocalDateTime.now();
        upsertStep(taskPublicId, taskId, STEP_PREPARE, "准备输入与模板", STEP_STATUS_RUNNING, prepareStart, null,
                "校验模板、文档集和任务参数", null);

        DocumentSet set = documentSetMapper.selectById(documentSetId);
        if (set == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_SET_NOT_FOUND);
        }
        Template template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND);
        }
        List<Document> docs = documentMapper.selectByDocumentSetId(documentSetId);
        if (docs.isEmpty()) {
            throw new BusinessException(ErrorCode.DOCUMENT_SET_EMPTY_DOCS);
        }

        LocalDateTime prepareEnd = LocalDateTime.now();
        upsertStep(taskPublicId, taskId, STEP_PREPARE, "准备输入与模板", STEP_STATUS_SUCCESS, prepareStart, prepareEnd,
                "已确认模板与文档，准备提交 AI 任务", null);

        LocalDateTime aiStart = LocalDateTime.now();
        upsertStep(taskPublicId, taskId, STEP_AI_PROCESS, "AI 处理任务", STEP_STATUS_RUNNING, aiStart, null,
                "已提交 AI 任务，等待处理与返回结果", null);
        try {
            ensureNotCancelled(taskPublicId, taskId, "AI 处理任务");
            aiFillService.fillTemplateForTask(task, docs);
            LocalDateTime aiEnd = LocalDateTime.now();
            upsertStep(taskPublicId, taskId, STEP_AI_PROCESS, "AI 处理任务", STEP_STATUS_SUCCESS, aiStart, aiEnd,
                    "AI 已返回处理结果", null);

            LocalDateTime resultNow = LocalDateTime.now();
            upsertStep(taskPublicId, taskId, STEP_RESULT, "保存结果文件", STEP_STATUS_SUCCESS, resultNow, resultNow,
                    "输出: " + task.getResultFilePath(), null);
        } catch (Exception e) {
            LocalDateTime aiEnd = LocalDateTime.now();
            if (isCancelledException(e) || cancelService.isCancelRequested(taskPublicId)) {
                upsertStep(taskPublicId, taskId, STEP_AI_PROCESS, "AI 处理任务", STEP_STATUS_FAILED, aiStart, aiEnd,
                        "用户取消任务，停止处理", "cancelled");
            } else {
                upsertStep(taskPublicId, taskId, STEP_AI_PROCESS, "AI 处理任务", STEP_STATUS_FAILED, aiStart, aiEnd,
                        "AI 处理失败", e.getMessage());
            }
            throw e;
        }
    }

    private void processFreeTask(FillTask task) throws IOException {
        Long taskId = task.getId();
        String taskPublicId = task.getPublicId();
        Long documentSetId = task.getDocumentSetId();

        LocalDateTime prepareStart = LocalDateTime.now();
        upsertStep(taskPublicId, taskId, STEP_PREPARE, "准备输入文档", STEP_STATUS_RUNNING, prepareStart, null,
                "校验文档集和任务参数", null);

        DocumentSet set = documentSetMapper.selectById(documentSetId);
        if (set == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_SET_NOT_FOUND);
        }
        List<Document> docs = documentMapper.selectByDocumentSetId(documentSetId);
        if (docs.isEmpty()) {
            throw new BusinessException(ErrorCode.DOCUMENT_SET_EMPTY_DOCS);
        }

        LocalDateTime prepareEnd = LocalDateTime.now();
        upsertStep(taskPublicId, taskId, STEP_PREPARE, "准备输入文档", STEP_STATUS_SUCCESS, prepareStart, prepareEnd,
                "已确认文档，准备提交 AI 任务", null);

        LocalDateTime aiStart = LocalDateTime.now();
        upsertStep(taskPublicId, taskId, STEP_AI_PROCESS, "AI 处理任务", STEP_STATUS_RUNNING, aiStart, null,
                "已提交 AI 任务，等待处理与返回结果", null);
        try {
            ensureNotCancelled(taskPublicId, taskId, "AI 处理任务");
            aiFillService.fillFreeForTask(task, docs);
            LocalDateTime aiEnd = LocalDateTime.now();
            upsertStep(taskPublicId, taskId, STEP_AI_PROCESS, "AI 处理任务", STEP_STATUS_SUCCESS, aiStart, aiEnd,
                    "AI 已返回处理结果", null);

            LocalDateTime resultNow = LocalDateTime.now();
            upsertStep(taskPublicId, taskId, STEP_RESULT, "保存结果文件", STEP_STATUS_SUCCESS, resultNow, resultNow,
                    "输出: " + task.getResultFilePath(), null);
        } catch (Exception e) {
            LocalDateTime aiEnd = LocalDateTime.now();
            if (isCancelledException(e) || cancelService.isCancelRequested(taskPublicId)) {
                upsertStep(taskPublicId, taskId, STEP_AI_PROCESS, "AI 处理任务", STEP_STATUS_FAILED, aiStart, aiEnd,
                        "用户取消任务，停止处理", "cancelled");
            } else {
                upsertStep(taskPublicId, taskId, STEP_AI_PROCESS, "AI 处理任务", STEP_STATUS_FAILED, aiStart, aiEnd,
                        "AI 处理失败", e.getMessage());
            }
            throw e;
        }
    }

    private void upsertStep(String taskPublicId,
                            Long taskId,
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
        step.setDurationMs(startedAt != null && finishedAt != null
                ? ChronoUnit.MILLIS.between(startedAt, finishedAt)
                : null);
        step.setMessage(message);
        step.setErrorMessage(errorMessage);
        fillTaskStepMapper.upsert(step);

        if (taskPublicId != null && !taskPublicId.isBlank()) {
            sseBroker.publish(taskPublicId, "STEP_UPSERT", new FillTaskStepEvent(
                    stepCode,
                    stepName,
                    status,
                    startedAt,
                    finishedAt,
                    step.getDurationMs(),
                    message,
                    errorMessage
            ));
        }
    }

    /**
     * 乐观锁更新 0 行时，用数据库最新行推送 SSE，避免订阅方收不到终态。
     */
    private void publishTaskStatusOrReload(long taskId, FillTask candidate, int rowsUpdated) {
        if (rowsUpdated > 0) {
            publishTaskStatus(candidate);
            return;
        }
        FillTask latest = fillTaskMapper.selectById(taskId);
        if (latest != null) {
            publishTaskStatus(latest);
        }
    }

    private void publishTaskStatus(FillTask task) {
        if (task == null || task.getPublicId() == null || task.getPublicId().isBlank()) {
            return;
        }
        sseBroker.publish(task.getPublicId(), "TASK_STATUS", new FillTaskStatusEvent(
                task.getStatus(),
                task.getErrorMessage(),
                task.getFinishedAt(),
                task.getResultFilePath(),
                detectResultFileType(task.getResultFilePath()),
                calcTotalDurationMs(task.getCreatedAt(), task.getFinishedAt())
        ));
    }

    private static Long calcTotalDurationMs(LocalDateTime createdAt, LocalDateTime finishedAt) {
        if (createdAt == null) {
            return null;
        }
        LocalDateTime end = finishedAt != null ? finishedAt : LocalDateTime.now();
        return ChronoUnit.MILLIS.between(createdAt, end);
    }

    private static String detectResultFileType(String resultFilePath) {
        if (resultFilePath == null || resultFilePath.isBlank()) {
            return null;
        }
        String lower = resultFilePath.toLowerCase();
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return "excel";
        }
        if (lower.endsWith(".docx") || lower.endsWith(".doc")) {
            return "docx";
        }
        if (lower.endsWith(".json")) {
            return "json";
        }
        return "unknown";
    }

    private void ensureNotCancelled(String taskPublicId, Long taskId, String stepName) {
        if (taskPublicId != null && cancelService.isCancelRequested(taskPublicId)) {
            throw new BusinessException(ErrorCode.TASK_CANCELLED,
                    "task cancelled, stop step: " + stepName + ", taskId=" + taskId);
        }
    }

    private void markCancelledIfNeeded(FillTask task) {
        if (task == null) {
            return;
        }
        if (!TaskStatus.CANCELLED.name().equalsIgnoreCase(task.getStatus())) {
            task.setStatus(TaskStatus.CANCELLED.name());
            task.setFinishedAt(LocalDateTime.now());
            if (task.getErrorMessage() == null || task.getErrorMessage().isBlank()) {
                task.setErrorMessage("用户主动取消任务");
            }
            int upd = fillTaskMapper.updateById(task);
            publishTaskStatusOrReload(task.getId(), task, upd);
            return;
        }
        publishTaskStatus(task);
    }

    private boolean isRunningTimedOut(FillTask task) {
        return task != null
                && task.getCreatedAt() != null
                && LocalDateTime.now().isAfter(task.getCreatedAt().plusMinutes(runningTimeoutMinutes));
    }

    private static boolean isCancelledException(Throwable e) {
        if (e instanceof BusinessException be) {
            return ErrorCode.TASK_CANCELLED.name().equals(be.getErrorCode());
        }
        return false;
    }

    private static TaskStatus resolveFailedStatus(Throwable e) {
        if (e != null && e.getMessage() != null) {
            String lower = e.getMessage().toLowerCase();
            if (lower.contains("timeout") || lower.contains("超时")) {
                return TaskStatus.TIMEOUT;
            }
        }
        return TaskStatus.FAILED;
    }

    private static String truncateErrorMessage(Throwable e) {
        if (e == null) {
            return null;
        }
        String msg = e.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = e.getClass().getSimpleName();
        }
        return msg.length() <= 500 ? msg : msg.substring(0, 500) + "...";
    }

    private static Long nextVersion(Long version) {
        return version == null ? 1L : version + 1;
    }

    private static int extractAttemptsFromXDeath(List<Map<String, Object>> xDeath, String queueName) {
        if (xDeath == null || xDeath.isEmpty()) {
            return 0;
        }
        long total = 0L;
        for (Map<String, Object> death : xDeath) {
            if (death == null) {
                continue;
            }
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
                    // ignore malformed x-death count
                }
            }
        }
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }
}
