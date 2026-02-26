package com.fusion.docfusion.service.impl;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.config.UploadProperties;
import com.fusion.docfusion.dto.TemplateVO;
import com.fusion.docfusion.entity.Template;
import com.fusion.docfusion.exception.BusinessException;
import com.fusion.docfusion.mapper.TemplateMapper;
import com.fusion.docfusion.service.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateServiceImpl implements TemplateService {

    private static final List<String> ALLOWED_TYPES = List.of("docx", "doc", "xlsx", "xls");

    private final UploadProperties uploadProperties;
    private final TemplateMapper templateMapper;

    @Override
    public Result<TemplateVO> uploadTemplate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择模板文件");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BusinessException("文件名无效");
        }
        String ext = getExtension(originalFilename).toLowerCase();
        if (!ALLOWED_TYPES.contains(ext)) {
            throw new BusinessException("仅支持 word(docx/doc) 或 excel(xlsx/xls) 模板");
        }
        Path basePath = Paths.get(uploadProperties.getTemplatesDir());
        try {
            Files.createDirectories(basePath);
        } catch (IOException e) {
            throw new BusinessException("创建模板目录失败: " + e.getMessage());
        }
        String savedName = UUID.randomUUID().toString() + "_" + originalFilename;
        Path target = basePath.resolve(savedName);
        try {
            file.transferTo(target.toFile());
        } catch (IOException e) {
            throw new BusinessException("保存模板失败: " + e.getMessage());
        }
        String fileType = ext.equals("doc") || ext.equals("docx") ? "word" : "excel";
        Template t = new Template();
        t.setFileName(originalFilename);
        t.setFileType(fileType);
        t.setFilePath(savedName);
        t.setCreatedAt(LocalDateTime.now());
        templateMapper.insert(t);

        TemplateVO vo = new TemplateVO();
        vo.setId(t.getId());
        vo.setFileName(t.getFileName());
        vo.setFileType(t.getFileType());
        vo.setCreatedAt(t.getCreatedAt());
        return Result.success(vo);
    }

    @Override
    public Result<List<TemplateVO>> listTemplates() {
        List<Template> list = templateMapper.selectAll();
        List<TemplateVO> vos = list.stream().map(t -> {
            TemplateVO vo = new TemplateVO();
            vo.setId(t.getId());
            vo.setFileName(t.getFileName());
            vo.setFileType(t.getFileType());
            vo.setCreatedAt(t.getCreatedAt());
            return vo;
        }).toList();
        return Result.success(vos);
    }

    @Override
    public Result<TemplateVO> getById(Long templateId) {
        Template t = templateMapper.selectById(templateId);
        if (t == null) {
            throw new BusinessException("模板不存在");
        }
        TemplateVO vo = new TemplateVO();
        vo.setId(t.getId());
        vo.setFileName(t.getFileName());
        vo.setFileType(t.getFileType());
        vo.setCreatedAt(t.getCreatedAt());
        return Result.success(vo);
    }

    private static String getExtension(String filename) {
        int i = filename.lastIndexOf('.');
        return i < 0 ? "" : filename.substring(i + 1);
    }
}
