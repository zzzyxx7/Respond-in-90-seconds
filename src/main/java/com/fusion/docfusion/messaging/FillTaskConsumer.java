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
import com.fusion.docfusion.util.RedisDistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Component;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/** 异步处理填表任务：调用 AI 任务流并记录步骤链路。 */
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
    /**
     * 任务长时间处于 RUNNING 时视为卡死，允许恢复重跑（分钟）。
     */
    @Value("${fill.task.running-timeout-minutes:10}")
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
            log.warn("任务锁已被占用，跳过本次消费, taskId={}", taskId);
            channel.basicAck(deliveryTag, false);
            return;
        }
        int attempts = extractAttemptsFromXDeath(xDeath, RabbitConfig.FILL_TASK_QUEUE);
        log.info("异步处理填表任务, taskId={}, deliveryTag={}, attempts={}",
                taskId, deliveryTag, attempts);
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
            if (TaskStatus.CANCELLED.name().equalsIgnoreCase(task.getStatus())) {
                log.info("任务已取消，跳过消费, taskId={}", taskId);
                channel.basicAck(deliveryTag, false);
                return;
            }

            if (TaskStatus.RUNNING.name().equalsIgnoreCase(task.getStatus())) {
                if (isRunningTimedOut(task)) {
                    log.warn("检测到 RUNNING 超时，触发恢复重跑, taskId={}, createdAt={}, timeoutMinutes={}",
                            taskId, task.getCreatedAt(), runningTimeoutMinutes);
                    task.setStatus(TaskStatus.TIMEOUT.name());
                    task.setErrorMessage("检测到任务 RUNNING 超时，已触发自动恢复重跑");
                    int timeoutUpdated = fillTaskMapper.updateById(task);
                    if (timeoutUpdated > 0) {
                        task.setVersion(nextVersion(task.getVersion()));
                    }
                } else {
                    // 重复投递保护：已有消费者正在处理，直接 ACK 跳过
                    log.warn("重复投递保护：任务仍在 RUNNING 且未超时，跳过本次消费, taskId={}", taskId);
                    channel.basicAck(deliveryTag, false);
                    return;
                }
            }

            task.setStatus(TaskStatus.RUNNING.name());
            int runningUpdated = fillTaskMapper.updateById(task);
            if (runningUpdated <= 0) {
                log.warn("任务状态更新冲突，跳过本次消费, taskId={}", taskId);
                channel.basicAck(deliveryTag, false);
                return;
            }
            task.setVersion(nextVersion(task.getVersion()));

            if (TaskMode.TEMPLATE.name().equalsIgnoreCase(task.getMode())) {
                processTemplateTask(task);
            } else if (TaskMode.FREE.name().equalsIgnoreCase(task.getMode())) {
                processFreeTask(task);
            } else {
                throw new BusinessException(ErrorCode.TASK_MODE_UNKNOWN, "未知任务模式: " + task.getMode());
            }

            task.setStatus(TaskStatus.SUCCESS.name());
            task.setFinishedAt(LocalDateTime.now());
            FillTask latestTask = fillTaskMapper.selectById(taskId);
            if (latestTask != null && TaskStatus.CANCELLED.name().equalsIgnoreCase(latestTask.getStatus())) {
                log.info("任务处理中被用户取消，保持 CANCELLED, taskId={}", taskId);
                channel.basicAck(deliveryTag, false);
                return;
            }
            int successUpdated = fillTaskMapper.updateById(task);
            if (successUpdated <= 0) {
                log.warn("任务完成写回冲突，保持最新状态, taskId={}", taskId);
            }
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("异步任务处理异常, taskId={}", taskId, e);

            // 尝试更新任务状态（可能 taskId 查不到，这里允许失败）
            try {
                FillTask task = fillTaskMapper.selectById(taskId);
                if (task != null) {
                    task.setStatus(resolveFailedStatus(e).name());
                    task.setFinishedAt(LocalDateTime.now());
                    task.setErrorMessage(truncateErrorMessage(e));
                    fillTaskMapper.updateById(task);
                }
            } catch (Exception ignore) {
                // ignore
            }

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
        } finally {
            distributedLock.unlock(lockKey, lockToken);
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

    private boolean isRunningTimedOut(FillTask task) {
        if (task == null || task.getCreatedAt() == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(task.getCreatedAt().plusMinutes(runningTimeoutMinutes));
    }

    private static TaskStatus resolveFailedStatus(Throwable e) {
        if (e == null || e.getMessage() == null) {
            return TaskStatus.FAILED;
        }
        String lower = e.getMessage().toLowerCase();
        if (lower.contains("timeout") || lower.contains("超时")) {
            return TaskStatus.TIMEOUT;
        }
        return TaskStatus.FAILED;
    }

    private static Long nextVersion(Long v) {
        return v == null ? 1L : v + 1;
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

        // 模板模式改为完全交由 AI 任务流处理，后端不再本地抽取
        upsertStep(taskId, STEP_EXTRACT, "字段抽取", STEP_STATUS_SKIPPED, null, null,
                "已切换为 AI 端内置抽取", null);

        // 真实填表：改为调用 AI 任务流接口（/api/tasks/create -> 轮询 -> download）
        LocalDateTime fillStart = LocalDateTime.now();
        upsertStep(taskId, STEP_FILL, "模板填表", STEP_STATUS_RUNNING, fillStart, null,
                "调用 AI 填表任务流", null);
        try {
            aiFillService.fillTemplateForTask(task, docs);
            LocalDateTime fillEnd = LocalDateTime.now();
            upsertStep(taskId, STEP_FILL, "模板填表", STEP_STATUS_SUCCESS, fillStart, fillEnd,
                    "已完成 AI 填表", null);

            // 结果文件已由 AiFillService 落盘，这里仅补一个“生成结果”步骤用于前端可视化
            LocalDateTime genNow = LocalDateTime.now();
            upsertStep(taskId, STEP_GENERATE, "生成结果文件", STEP_STATUS_SUCCESS, genNow, genNow,
                    "输出: " + task.getResultFilePath(), null);
        } catch (Exception e) {
            LocalDateTime fillEnd = LocalDateTime.now();
            upsertStep(taskId, STEP_FILL, "模板填表", STEP_STATUS_FAILED, fillStart, fillEnd,
                    "AI 填表失败", e.getMessage());
            throw e;
        }
    }

    private void processFreeTask(FillTask task) throws IOException {
        Long taskId = task.getId();
        Long documentSetId = task.getDocumentSetId();

        upsertStep(taskId, STEP_RAG, "RAG 检索", STEP_STATUS_SKIPPED, null, null, "当前未接入 RAG", null);
        upsertStep(taskId, STEP_EXTRACT, "字段抽取", STEP_STATUS_SKIPPED, null, null, "自由模式由 AI 内部处理抽取", null);

        DocumentSet set = documentSetMapper.selectById(documentSetId);
        if (set == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_SET_NOT_FOUND);
        }
        List<Document> docs = documentMapper.selectByDocumentSetId(documentSetId);
        if (docs.isEmpty()) {
            throw new BusinessException(ErrorCode.DOCUMENT_SET_EMPTY_DOCS);
        }

        LocalDateTime fillStart = LocalDateTime.now();
        upsertStep(taskId, STEP_FILL, "汇总生成", STEP_STATUS_RUNNING, fillStart, null,
                "调用 AI 自由模式任务流, 文档数: " + docs.size(), null);
        try {
            aiFillService.fillFreeForTask(task, docs);
            LocalDateTime fillEnd = LocalDateTime.now();
            upsertStep(taskId, STEP_FILL, "汇总生成", STEP_STATUS_SUCCESS, fillStart, fillEnd,
                    "已完成 AI 自由模式处理", null);
            LocalDateTime genNow = LocalDateTime.now();
            upsertStep(taskId, STEP_GENERATE, "生成结果文件", STEP_STATUS_SUCCESS, genNow, genNow,
                    "输出: " + task.getResultFilePath(), null);
        } catch (Exception e) {
            LocalDateTime fillEnd = LocalDateTime.now();
            upsertStep(taskId, STEP_FILL, "汇总生成", STEP_STATUS_FAILED, fillStart, fillEnd,
                    "AI 自由模式处理失败", e.getMessage());
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

