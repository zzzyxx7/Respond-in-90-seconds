package com.fusion.docfusion.service.impl;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.config.UploadProperties;
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
        task.setUserId(currentUserId());
        task.setDocumentSetId(documentSetId);
        task.setTemplateId(templateId);
        task.setMode("TEMPLATE");
        task.setUserRequirement(null);
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
        task.setStatus("RUNNING");
        task.setCreatedAt(LocalDateTime.now());
        fillTaskMapper.insert(task);

        // 自由模式占位实现：生成一个简单的 Excel，列出文档文件名
        Path resultsDir = Paths.get(uploadProperties.getResultsDir());
        try {
            Files.createDirectories(resultsDir);
        } catch (IOException e) {
            task.setStatus("FAILED");
            task.setFinishedAt(LocalDateTime.now());
            fillTaskMapper.updateById(task);
            throw new BusinessException("创建结果目录失败: " + e.getMessage());
        }

        String resultFileName = "free_" + task.getId() + "_" + UUID.randomUUID().toString().substring(0, 8) + ".xlsx";
        Path resultPath = resultsDir.resolve(resultFileName);

        try (org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("汇总");
            int rowIdx = 0;
            // 表头
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(rowIdx++);
            header.createCell(0).setCellValue("文件名");
            // 数据行
            for (Document doc : docs) {
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(doc.getFileName());
            }
            try (java.io.OutputStream os = Files.newOutputStream(resultPath)) {
                workbook.write(os);
            }
        } catch (IOException e) {
            task.setStatus("FAILED");
            task.setFinishedAt(LocalDateTime.now());
            fillTaskMapper.updateById(task);
            throw new BusinessException("生成自由模式结果文件失败: " + e.getMessage());
        }

        task.setStatus("SUCCESS");
        task.setResultFilePath(resultFileName);
        task.setFinishedAt(LocalDateTime.now());
        fillTaskMapper.updateById(task);

        return Result.success(toVO(task));
    }

    @Override
    public Result<List<FillTaskVO>> listTasks(String mode, String status, Integer page, Integer size) {
        int pageNum = (page == null || page < 1) ? 1 : page;
        int pageSize = (size == null || size < 1 || size > 100) ? 20 : size;
        int offset = (pageNum - 1) * pageSize;

        List<FillTask> tasks = fillTaskMapper.selectByConditions(mode, status, pageSize, offset);
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
