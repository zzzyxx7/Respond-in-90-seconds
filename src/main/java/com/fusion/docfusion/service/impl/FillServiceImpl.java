package com.fusion.docfusion.service.impl;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.config.UploadProperties;
import com.fusion.docfusion.dto.FillRequest;
import com.fusion.docfusion.dto.FillTaskVO;
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
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
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

    private final UploadProperties uploadProperties;
    private final DocumentSetMapper documentSetMapper;
    private final DocumentMapper documentMapper;
    private final TemplateMapper templateMapper;
    private final FillTaskMapper fillTaskMapper;

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
        task.setDocumentSetId(documentSetId);
        task.setTemplateId(templateId);
        task.setStatus("RUNNING");
        task.setCreatedAt(LocalDateTime.now());
        fillTaskMapper.insert(task);

        Path templatesDir = Paths.get(uploadProperties.getTemplatesDir());
        Path resultsDir = Paths.get(uploadProperties.getResultsDir());
        Path templatePath = templatesDir.resolve(template.getFilePath());
        if (!Files.exists(templatePath)) {
            task.setStatus("FAILED");
            task.setFinishedAt(LocalDateTime.now());
            fillTaskMapper.updateById(task);
            throw new BusinessException("模板文件不存在: " + template.getFileName());
        }

        try {
            Files.createDirectories(resultsDir);
        } catch (IOException e) {
            task.setStatus("FAILED");
            task.setFinishedAt(LocalDateTime.now());
            fillTaskMapper.updateById(task);
            throw new BusinessException("创建结果目录失败: " + e.getMessage());
        }
        String resultFileName = "fill_" + task.getId() + "_" + UUID.randomUUID().toString().substring(0, 8) + "_" + template.getFileName();
        Path resultPath = resultsDir.resolve(resultFileName);
        try {
            Files.copy(templatePath, resultPath);
        } catch (IOException e) {
            task.setStatus("FAILED");
            task.setFinishedAt(LocalDateTime.now());
            fillTaskMapper.updateById(task);
            throw new BusinessException("生成结果文件失败: " + e.getMessage());
        }

        task.setStatus("SUCCESS");
        task.setResultFilePath(resultFileName);
        task.setFinishedAt(LocalDateTime.now());
        fillTaskMapper.updateById(task);

        FillTaskVO vo = toVO(task);
        return Result.success(vo);
    }

    @Override
    public Result<FillTaskVO> getTask(Long taskId) {
        FillTask task = fillTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        return Result.success(toVO(task));
    }

    private static FillTaskVO toVO(FillTask task) {
        FillTaskVO vo = new FillTaskVO();
        vo.setId(task.getId());
        vo.setDocumentSetId(task.getDocumentSetId());
        vo.setTemplateId(task.getTemplateId());
        vo.setStatus(task.getStatus());
        vo.setResultFilePath(task.getResultFilePath());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setFinishedAt(task.getFinishedAt());
        return vo;
    }
}
