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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

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

    // 比赛/演示用：避免刷任务导致队列膨胀
    private static final int MAX_SUBMISSIONS_PER_MINUTE = 100;
    private static final long RATE_LIMIT_WINDOW_MILLIS = Duration.ofMinutes(1).toMillis();

    @Override
    public Result<FillTaskVO> submitFill(FillRequest request) {
        Long documentSetId = request.getDocumentSetId();
        Long templateId = request.getTemplateId();

        Long currentUserId = SecurityUtils.currentUserId();
        if (currentUserId == null) {
            throw new BusinessException("请先登录再提交填表任务");
        }

        // rate limit：限制提交频率，防止滥用
        String rateKey = "fill:submitFill:" + currentUserId;
        if (!rateLimiter.tryAcquire(rateKey, MAX_SUBMISSIONS_PER_MINUTE, RATE_LIMIT_WINDOW_MILLIS)) {
            throw new BusinessException(429, "提交操作过于频繁，请在 1 分钟后再试");
        }

        DocumentSet set = documentSetMapper.selectById(documentSetId);
        if (set == null) {
            throw new BusinessException("文档集不存在");
        }
        if (set.getOwnerId() != null && !currentUserId.equals(set.getOwnerId())) {
            throw new BusinessException("无权使用该文档集");
        }
        Template template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        List<Document> docs = documentMapper.selectByDocumentSetId(documentSetId);
        if (docs.isEmpty()) {
            throw new BusinessException("文档集中没有文档");
        }

        FillTask task = new FillTask();
        task.setUserId(currentUserId);
        task.setDocumentSetId(documentSetId);
        task.setTemplateId(templateId);
        task.setMode(TaskMode.TEMPLATE.name());
        task.setUserRequirement(request.getUserRequirement());
        task.setStatus(TaskStatus.PENDING.name());
        task.setCreatedAt(LocalDateTime.now());
        fillTaskMapper.insert(task);

        // 发送异步任务消息
        amqpTemplate.convertAndSend(RabbitConfig.FILL_TASK_EXCHANGE, RabbitConfig.FILL_TASK_ROUTING_KEY, task.getId());

        return Result.success(toVO(task, true));
    }

    @Override
    public Result<FillTaskVO> getTask(Long taskId) {
        FillTask task = fillTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        Long currentUserId = SecurityUtils.currentUserId();
        if (currentUserId == null || (task.getUserId() != null && !currentUserId.equals(task.getUserId()))) {
            throw new BusinessException("无权访问该任务");
        }
        return Result.success(toVO(task, true));
    }

    @Override
    public Result<FillTaskVO> submitFree(FreeFillRequest request) {
        Long documentSetId = request.getDocumentSetId();

        Long currentUserId = SecurityUtils.currentUserId();
        if (currentUserId == null) {
            throw new BusinessException("请先登录再提交填表任务");
        }

        String rateKey = "fill:submitFree:" + currentUserId;
        if (!rateLimiter.tryAcquire(rateKey, MAX_SUBMISSIONS_PER_MINUTE, RATE_LIMIT_WINDOW_MILLIS)) {
            throw new BusinessException(429, "提交操作过于频繁，请在 1 分钟后再试");
        }

        DocumentSet set = documentSetMapper.selectById(documentSetId);
        if (set == null) {
            throw new BusinessException("文档集不存在");
        }
        if (set.getOwnerId() != null && !currentUserId.equals(set.getOwnerId())) {
            throw new BusinessException("无权使用该文档集");
        }
        List<Document> docs = documentMapper.selectByDocumentSetId(documentSetId);
        if (docs.isEmpty()) {
            throw new BusinessException("文档集中没有文档");
        }

        FillTask task = new FillTask();
        task.setUserId(currentUserId);
        task.setDocumentSetId(documentSetId);
        task.setTemplateId(null);
        task.setMode(TaskMode.FREE.name());
        task.setUserRequirement(request.getUserRequirement());
        task.setStatus(TaskStatus.PENDING.name());
        task.setCreatedAt(LocalDateTime.now());
        fillTaskMapper.insert(task);

        // 发送异步任务消息
        amqpTemplate.convertAndSend(RabbitConfig.FILL_TASK_EXCHANGE, RabbitConfig.FILL_TASK_ROUTING_KEY, task.getId());

        return Result.success(toVO(task, true));
    }

    @Override
    public Result<FillTaskListPageVO> listTasks(String mode, String status, Integer page, Integer size) {
        Long currentUserId = SecurityUtils.currentUserId();
        if (currentUserId == null) {
            throw new BusinessException("请先登录查看历史任务");
        }
        int pageNum = (page == null || page < 1) ? 1 : page;
        int pageSize = (size == null || size < 1 || size > 100) ? 20 : size;
        int offset = (pageNum - 1) * pageSize;

        long total = fillTaskMapper.countByConditions(currentUserId, mode, status);
        List<FillTask> tasks = fillTaskMapper.selectByConditions(currentUserId, mode, status, pageSize, offset);
        // 列表页不附带 steps，减轻 payload；详情用 GET /tasks/{id}
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
        if (includeSteps) {
            List<FillTaskStep> steps = fillTaskStepMapper.selectByTaskId(task.getId());
            List<FillTaskStepVO> stepVOs = steps.stream().map(FillServiceImpl::toStepVO).toList();
            vo.setSteps(stepVOs);
        }
        return vo;
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
}
