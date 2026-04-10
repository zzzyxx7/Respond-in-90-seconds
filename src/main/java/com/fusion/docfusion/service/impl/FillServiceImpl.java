package com.fusion.docfusion.service.impl;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.config.RabbitConfig;
import com.fusion.docfusion.dto.FillRequest;
import com.fusion.docfusion.dto.FillTaskListPageVO;
import com.fusion.docfusion.dto.FillTaskVO;
import com.fusion.docfusion.dto.FillTaskStepVO;
import com.fusion.docfusion.dto.FreeFillRequest;
import com.fusion.docfusion.entity.FillTaskStep;
import com.fusion.docfusion.entity.Document;
import com.fusion.docfusion.entity.DocumentSet;
import com.fusion.docfusion.entity.FillTask;
import com.fusion.docfusion.entity.Template;
import com.fusion.docfusion.exception.BusinessException;
import com.fusion.docfusion.exception.ErrorCode;
import com.fusion.docfusion.mapper.DocumentMapper;
import com.fusion.docfusion.mapper.DocumentSetMapper;
import com.fusion.docfusion.mapper.FillTaskMapper;
import com.fusion.docfusion.enums.TaskMode;
import com.fusion.docfusion.enums.TaskStatus;
import com.fusion.docfusion.mapper.FillTaskStepMapper;
import com.fusion.docfusion.mapper.TemplateMapper;
import com.fusion.docfusion.service.FillService;
import com.fusion.docfusion.security.SecurityUtils;
import com.fusion.docfusion.util.RedisRateLimiter;
import com.fusion.docfusion.util.FillTaskCancelService;
import com.fusion.docfusion.util.RequestUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Service;
import com.fusion.docfusion.sse.FillTaskSseBroker;
import com.fusion.docfusion.sse.FillTaskStatusEvent;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 填表任务：创建任务后执行填表逻辑。
 * 当前为占位实现：将模板复制到结果目录并标记完成；实际“从文档抽取 + 填表”由你与 AI 同学后续接入。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FillServiceImpl implements FillService {

    private final DocumentSetMapper documentSetMapper;
    private final DocumentMapper documentMapper;
    private final TemplateMapper templateMapper;
    private final FillTaskMapper fillTaskMapper;
    private final FillTaskStepMapper fillTaskStepMapper;
    private final AmqpTemplate amqpTemplate;
    private final RedisRateLimiter rateLimiter;
    private final FillTaskCancelService cancelService;
    private final FillTaskSseBroker sseBroker;

    // 比赛/演示用：避免刷任务导致队列膨胀
    private static final int MAX_SUBMISSIONS_PER_MINUTE = 100;
    private static final long RATE_LIMIT_WINDOW_MILLIS = Duration.ofMinutes(1).toMillis();

    @Override
    public Result<FillTaskVO> submitFill(FillRequest request) {
        Long documentSetId = resolveDocumentSetId(request.getDocumentSetId(), request.getDocumentSetPublicId());
        Long templateId = resolveTemplateId(request.getTemplateId(), request.getTemplatePublicId());

        Long currentUserId = SecurityUtils.currentUserId();

        // rate limit：限制提交频率，防止滥用
        String rateKey = "fill:submitFill:" + (currentUserId != null ? ("uid:" + currentUserId) : ("anon:" + RequestUtils.clientIp()));
        if (!rateLimiter.tryAcquire(rateKey, MAX_SUBMISSIONS_PER_MINUTE, RATE_LIMIT_WINDOW_MILLIS)) {
            throw new BusinessException(ErrorCode.FILL_RATE_LIMITED);
        }

        DocumentSet set = documentSetMapper.selectById(documentSetId);
        if (set == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_SET_NOT_FOUND);
        }
        if (set.getOwnerId() != null && (currentUserId == null || !currentUserId.equals(set.getOwnerId()))) {
            throw new BusinessException(ErrorCode.DOCUMENT_SET_FORBIDDEN);
        }
        Template template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND);
        }
        List<Document> docs = documentMapper.selectByDocumentSetId(documentSetId);
        if (docs.isEmpty()) {
            throw new BusinessException(ErrorCode.DOCUMENT_SET_EMPTY_DOCS);
        }

        FillTask task = new FillTask();
        // 允许匿名使用核心功能：未登录时 userId 为空；登录后 userId 用于隔离与历史记录
        task.setUserId(currentUserId);
        task.setDocumentSetId(documentSetId);
        task.setTemplateId(templateId);
        task.setMode(TaskMode.TEMPLATE.name());
        task.setUserRequirement(request.getUserRequirement());
        task.setPublicId(generatePublicId());
        task.setStatus(TaskStatus.PENDING.name());
        task.setCreatedAt(LocalDateTime.now());
        fillTaskMapper.insert(task);
        cancelService.clearCancel(task.getPublicId());

        // 发送异步任务消息
        amqpTemplate.convertAndSend(RabbitConfig.FILL_TASK_EXCHANGE, RabbitConfig.FILL_TASK_ROUTING_KEY, task.getId());

        return Result.success(toVO(task, true));
    }

    @Override
    public Result<FillTaskVO> getTaskByPublicId(String taskPublicId) {
        FillTask task = fillTaskMapper.selectByPublicId(taskPublicId);
        ensureTaskAccessible(task, ErrorCode.TASK_FORBIDDEN);
        return Result.success(toVO(task, true));
    }

    @Override
    public Result<FillTaskVO> rerunTaskByPublicId(String taskPublicId) {
        FillTask task = fillTaskMapper.selectByPublicId(taskPublicId);
        ensureTaskAccessible(task, ErrorCode.TASK_OPERATION_FORBIDDEN);
        int changed = fillTaskMapper.resetForRerun(
                task.getId(),
                TaskStatus.FAILED.name(),
                TaskStatus.TIMEOUT.name(),
                TaskStatus.PENDING.name(),
                "人工触发重跑，等待重新处理"
        );
        FillTask latest = fillTaskMapper.selectById(task.getId());
        if (changed <= 0) {
            String latestStatus = latest == null ? null : latest.getStatus();
            if (TaskStatus.PENDING.name().equalsIgnoreCase(latestStatus)
                    || TaskStatus.RUNNING.name().equalsIgnoreCase(latestStatus)) {
                return Result.success(toVO(latest, true));
            }
            throw new BusinessException(ErrorCode.TASK_RERUN_NOT_ALLOWED);
        }
        cancelService.clearCancel(task.getPublicId());
        amqpTemplate.convertAndSend(RabbitConfig.FILL_TASK_EXCHANGE, RabbitConfig.FILL_TASK_ROUTING_KEY, task.getId());
        return Result.success(toVO(latest, true));
    }

    @Override
    public Result<FillTaskVO> cancelTaskByPublicId(String taskPublicId) {
        FillTask task = fillTaskMapper.selectByPublicId(taskPublicId);
        ensureTaskAccessible(task, ErrorCode.TASK_OPERATION_FORBIDDEN);
        int changed = fillTaskMapper.cancelIfStatusIn(
                task.getId(),
                TaskStatus.PENDING.name(),
                TaskStatus.RUNNING.name(),
                TaskStatus.CANCELLED.name(),
                "用户主动取消任务",
                LocalDateTime.now()
        );
        FillTask latest = fillTaskMapper.selectById(task.getId());
        if (changed <= 0) {
            String latestStatus = latest == null ? null : latest.getStatus();
            if (TaskStatus.CANCELLED.name().equalsIgnoreCase(latestStatus)) {
                return Result.success(toVO(latest, true));
            }
            throw new BusinessException(ErrorCode.TASK_CANCEL_NOT_ALLOWED);
        }
        cancelService.requestCancel(latest.getPublicId());
        publishCancelled(latest);
        return Result.success(toVO(latest, true));
    }

    @Override
    public Result<FillTaskVO> submitFree(FreeFillRequest request) {
        Long documentSetId = resolveDocumentSetId(request.getDocumentSetId(), request.getDocumentSetPublicId());

        Long currentUserId = SecurityUtils.currentUserId();

        String rateKey = "fill:submitFree:" + (currentUserId != null ? ("uid:" + currentUserId) : ("anon:" + RequestUtils.clientIp()));
        if (!rateLimiter.tryAcquire(rateKey, MAX_SUBMISSIONS_PER_MINUTE, RATE_LIMIT_WINDOW_MILLIS)) {
            throw new BusinessException(ErrorCode.FILL_RATE_LIMITED);
        }

        DocumentSet set = documentSetMapper.selectById(documentSetId);
        if (set == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_SET_NOT_FOUND);
        }
        if (set.getOwnerId() != null && (currentUserId == null || !currentUserId.equals(set.getOwnerId()))) {
            throw new BusinessException(ErrorCode.DOCUMENT_SET_FORBIDDEN);
        }
        List<Document> docs = documentMapper.selectByDocumentSetId(documentSetId);
        if (docs.isEmpty()) {
            throw new BusinessException(ErrorCode.DOCUMENT_SET_EMPTY_DOCS);
        }

        FillTask task = new FillTask();
        task.setUserId(currentUserId);
        task.setDocumentSetId(documentSetId);
        task.setTemplateId(null);
        task.setMode(TaskMode.FREE.name());
        task.setUserRequirement(request.getUserRequirement());
        task.setPublicId(generatePublicId());
        task.setStatus(TaskStatus.PENDING.name());
        task.setCreatedAt(LocalDateTime.now());
        fillTaskMapper.insert(task);
        cancelService.clearCancel(task.getPublicId());

        // 发送异步任务消息
        amqpTemplate.convertAndSend(RabbitConfig.FILL_TASK_EXCHANGE, RabbitConfig.FILL_TASK_ROUTING_KEY, task.getId());

        return Result.success(toVO(task, true));
    }

    @Override
    public Result<FillTaskListPageVO> listTasks(String mode, String status, Integer page, Integer size) {
        Long currentUserId = SecurityUtils.currentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_REQUIRED, "请先登录查看历史任务");
        }
        int pageNum = (page == null || page < 1) ? 1 : page;
        int pageSize = (size == null || size < 1 || size > 100) ? 20 : size;
        int offset = (pageNum - 1) * pageSize;

        long total = fillTaskMapper.countByConditions(currentUserId, mode, status);
        List<FillTask> tasks = fillTaskMapper.selectByConditions(currentUserId, mode, status, pageSize, offset);
        // 列表页不附带 steps，减轻 payload；详情用 GET /tasks/public/{taskPublicId}
        List<FillTaskVO> vos = tasks.stream().map(t -> toVO(t, false)).toList();

        FillTaskListPageVO pageVO = new FillTaskListPageVO();
        pageVO.setList(vos);
        pageVO.setTotal(total);
        pageVO.setPage(pageNum);
        pageVO.setSize(pageSize);
        pageVO.setHasMore((long) pageNum * pageSize < total);
        return Result.success(pageVO);
    }

    private FillTaskVO toVO(FillTask task, boolean includeSteps) {
        FillTaskVO vo = new FillTaskVO();
        vo.setId(task.getId());
        vo.setPublicId(task.getPublicId());
        vo.setUserId(task.getUserId());
        vo.setDocumentSetId(task.getDocumentSetId());
        vo.setTemplateId(task.getTemplateId());
        vo.setMode(task.getMode());
        vo.setUserRequirement(task.getUserRequirement());
        vo.setStatus(task.getStatus());
        vo.setResultFilePath(task.getResultFilePath());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setFinishedAt(task.getFinishedAt());
        vo.setErrorMessage(task.getErrorMessage());

        vo.setTotalDurationMs(calcTotalDurationMs(task.getCreatedAt(), task.getFinishedAt()));
        List<FillTaskStep> steps = null;
        if (includeSteps) {
            steps = fillTaskStepMapper.selectByTaskId(task.getId());
            List<FillTaskStepVO> stepVOs = steps.stream().map(FillServiceImpl::toStepVO).toList();
            vo.setSteps(stepVOs);
        }
        fillStateAndFailureHints(vo, task, steps);
        return vo;
    }

    private static void fillStateAndFailureHints(FillTaskVO vo, FillTask task, List<FillTaskStep> steps) {
        if (task == null || vo == null) {
            return;
        }
        String status = task.getStatus();
        List<String> allowed = new ArrayList<>();
        if (TaskStatus.SUCCESS.name().equalsIgnoreCase(status)) {
            allowed.add("DOWNLOAD");
        }
        if (TaskStatus.PENDING.name().equalsIgnoreCase(status) || TaskStatus.RUNNING.name().equalsIgnoreCase(status)) {
            allowed.add("CANCEL");
        }
        if (TaskStatus.FAILED.name().equalsIgnoreCase(status) || TaskStatus.TIMEOUT.name().equalsIgnoreCase(status)) {
            allowed.add("MANUAL_RERUN");
        }
        vo.setAllowedActions(allowed);

        if (!(TaskStatus.FAILED.name().equalsIgnoreCase(status) || TaskStatus.TIMEOUT.name().equalsIgnoreCase(status))) {
            return;
        }
        vo.setFailureReasonCode(resolveFailureReasonCode(task));
        vo.setFailureSuggestion(buildFailureSuggestion(status, vo.getFailureReasonCode()));
        FillTaskStep failedStep = findLastFailedStep(steps);
        if (failedStep != null) {
            vo.setFailureStage(failedStep.getStepCode());
            if (vo.getErrorMessage() == null || vo.getErrorMessage().isBlank()) {
                vo.setErrorMessage(failedStep.getErrorMessage());
            }
        } else {
            vo.setFailureStage("TASK");
        }
    }

    private static FillTaskStep findLastFailedStep(List<FillTaskStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return null;
        }
        for (int i = steps.size() - 1; i >= 0; i--) {
            FillTaskStep s = steps.get(i);
            if (s != null && "FAILED".equalsIgnoreCase(s.getStatus())) {
                return s;
            }
        }
        return null;
    }

    private static String resolveFailureReasonCode(FillTask task) {
        if (task == null) {
            return "UNKNOWN";
        }
        if (TaskStatus.TIMEOUT.name().equalsIgnoreCase(task.getStatus())) {
            return "TIMEOUT";
        }
        String msg = task.getErrorMessage();
        if (msg == null) {
            return "UNKNOWN";
        }
        String lower = msg.toLowerCase();
        if (lower.contains("timeout") || lower.contains("超时")) {
            return "TIMEOUT";
        }
        if (lower.contains("process exit code")) {
            return "AI_PROCESS_EXIT";
        }
        if (lower.contains("connect") || lower.contains("refused") || lower.contains("io")) {
            return "AI_NETWORK";
        }
        return "UNKNOWN";
    }

    private static String buildFailureSuggestion(String status, String reasonCode) {
        if (TaskStatus.TIMEOUT.name().equalsIgnoreCase(status) || "TIMEOUT".equalsIgnoreCase(reasonCode)) {
            return "任务超时，建议检查 AI 服务负载或日志后执行人工重跑。";
        }
        if ("AI_PROCESS_EXIT".equalsIgnoreCase(reasonCode)) {
            return "AI 进程异常退出，建议先修复 AI 侧报错后再重跑。";
        }
        if ("AI_NETWORK".equalsIgnoreCase(reasonCode)) {
            return "AI 网络调用失败，建议确认 AI 服务地址与连通性。";
        }
        return "建议查看任务步骤错误信息后执行人工重跑。";
    }

    private static FillTaskStepVO toStepVO(FillTaskStep step) {
        FillTaskStepVO vo = new FillTaskStepVO();
        vo.setStepCode(step.getStepCode());
        vo.setStepName(step.getStepName());
        vo.setStatus(step.getStatus());
        vo.setStartedAt(step.getStartedAt());
        vo.setFinishedAt(step.getFinishedAt());
        vo.setDurationMs(calcStepDurationMs(step.getDurationMs(), step.getStartedAt(), step.getFinishedAt()));
        vo.setMessage(step.getMessage());
        vo.setErrorMessage(step.getErrorMessage());
        return vo;
    }

    private static Long calcTotalDurationMs(LocalDateTime createdAt, LocalDateTime finishedAt) {
        if (createdAt == null) {
            return null;
        }
        LocalDateTime end = finishedAt != null ? finishedAt : LocalDateTime.now();
        return Duration.between(createdAt, end).toMillis();
    }

    private static Long calcStepDurationMs(Long persistedDurationMs,
                                           LocalDateTime startedAt,
                                           LocalDateTime finishedAt) {
        if (persistedDurationMs != null) {
            return persistedDurationMs;
        }
        if (startedAt == null) {
            return null;
        }
        LocalDateTime end = finishedAt != null ? finishedAt : LocalDateTime.now();
        return Duration.between(startedAt, end).toMillis();
    }

    private static String generatePublicId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static void ensureTaskAccessible(FillTask task, ErrorCode forbiddenCode) {
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        Long currentUserId = SecurityUtils.currentUserId();
        // 匿名任务：userId 为空，允许持有 publicId 的调用方访问（用于“匿名可用核心功能”）
        if (task.getUserId() == null) {
            return;
        }
        // 登录任务：必须登录且为本人
        if (currentUserId == null || !currentUserId.equals(task.getUserId())) {
            throw new BusinessException(forbiddenCode);
        }
    }

    private void publishCancelled(FillTask task) {
        if (task == null || task.getPublicId() == null || task.getPublicId().isBlank()) {
            return;
        }
        sseBroker.publish(task.getPublicId(), "TASK_STATUS", new FillTaskStatusEvent(
                TaskStatus.CANCELLED.name(),
                task.getErrorMessage(),
                task.getFinishedAt()
        ));
    }

    private Long resolveDocumentSetId(Long documentSetId, String documentSetPublicId) {
        if (documentSetPublicId != null && !documentSetPublicId.isBlank()) {
            DocumentSet set = documentSetMapper.selectByPublicId(documentSetPublicId);
            if (set == null) {
                throw new BusinessException(ErrorCode.DOCUMENT_SET_NOT_FOUND);
            }
            return set.getId();
        }
        if (documentSetId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "documentSetId 或 documentSetPublicId 不能为空");
        }
        return documentSetId;
    }

    private Long resolveTemplateId(Long templateId, String templatePublicId) {
        if (templatePublicId != null && !templatePublicId.isBlank()) {
            Template t = templateMapper.selectByPublicId(templatePublicId);
            if (t == null) {
                throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND);
            }
            return t.getId();
        }
        if (templateId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "templateId 或 templatePublicId 不能为空");
        }
        return templateId;
    }

}
