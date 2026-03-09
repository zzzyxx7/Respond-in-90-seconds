package com.fusion.docfusion.service.impl;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.config.RabbitConfig;
import com.fusion.docfusion.dto.FillRequest;
import com.fusion.docfusion.dto.FillTaskVO;
import com.fusion.docfusion.dto.FreeFillRequest;
import com.fusion.docfusion.entity.Document;
import com.fusion.docfusion.entity.DocumentSet;
import com.fusion.docfusion.entity.FillTask;
import com.fusion.docfusion.entity.Template;
import com.fusion.docfusion.exception.BusinessException;
import com.fusion.docfusion.mapper.DocumentMapper;
import com.fusion.docfusion.mapper.DocumentSetMapper;
import com.fusion.docfusion.mapper.FillTaskMapper;
import com.fusion.docfusion.mapper.TemplateMapper;
import com.fusion.docfusion.service.FillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Service;

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
    private final AmqpTemplate amqpTemplate;

    @Override
    public Result<FillTaskVO> submitFill(FillRequest request) {
        Long documentSetId = request.getDocumentSetId();
        Long templateId = request.getTemplateId();

        DocumentSet set = documentSetMapper.selectById(documentSetId);
        if (set == null) {
            throw new BusinessException("文档集不存在");
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
        task.setUserId(currentUserId());
        task.setDocumentSetId(documentSetId);
        task.setTemplateId(templateId);
        task.setMode("TEMPLATE");
        task.setUserRequirement(request.getUserRequirement());
        task.setStatus("PENDING");
        task.setCreatedAt(LocalDateTime.now());
        fillTaskMapper.insert(task);

        // 发送异步任务消息
        amqpTemplate.convertAndSend(RabbitConfig.FILL_TASK_EXCHANGE, RabbitConfig.FILL_TASK_ROUTING_KEY, task.getId());

        return Result.success(toVO(task));
    }

    @Override
    public Result<FillTaskVO> getTask(Long taskId) {
        FillTask task = fillTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        return Result.success(toVO(task));
    }

    @Override
    public Result<FillTaskVO> submitFree(FreeFillRequest request) {
        Long documentSetId = request.getDocumentSetId();

        DocumentSet set = documentSetMapper.selectById(documentSetId);
        if (set == null) {
            throw new BusinessException("文档集不存在");
        }
        List<Document> docs = documentMapper.selectByDocumentSetId(documentSetId);
        if (docs.isEmpty()) {
            throw new BusinessException("文档集中没有文档");
        }

        FillTask task = new FillTask();
        task.setUserId(currentUserId());
        task.setDocumentSetId(documentSetId);
        task.setTemplateId(null);
        task.setMode("FREE");
        task.setUserRequirement(request.getUserRequirement());
        task.setStatus("PENDING");
        task.setCreatedAt(LocalDateTime.now());
        fillTaskMapper.insert(task);

        // 发送异步任务消息
        amqpTemplate.convertAndSend(RabbitConfig.FILL_TASK_EXCHANGE, RabbitConfig.FILL_TASK_ROUTING_KEY, task.getId());

        return Result.success(toVO(task));
    }

    @Override
    public Result<List<FillTaskVO>> listTasks(String mode, String status, Integer page, Integer size) {
        Long currentUserId = currentUserId();
        if (currentUserId == null) {
            throw new BusinessException("请先登录查看历史任务");
        }
        int pageNum = (page == null || page < 1) ? 1 : page;
        int pageSize = (size == null || size < 1 || size > 100) ? 20 : size;
        int offset = (pageNum - 1) * pageSize;

        List<FillTask> tasks = fillTaskMapper.selectByConditions(currentUserId, mode, status, pageSize, offset);
        List<FillTaskVO> vos = tasks.stream().map(FillServiceImpl::toVO).toList();
        return Result.success(vos);
    }

    private static Long currentUserId() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof Long l) {
            return l;
        }
        return null;
    }

    private static FillTaskVO toVO(FillTask task) {
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
        return vo;
    }
}
