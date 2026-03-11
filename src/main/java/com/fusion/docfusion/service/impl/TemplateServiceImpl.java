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
        Long currentUserId = currentUserId();
        if (currentUserId == null) {
            throw new BusinessException("请先登录再上传模板");
        }
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
        t.setOwnerId(currentUserId);
        t.setReportTypeId(null);
        t.setFileName(originalFilename);
        t.setFileType(fileType);
        t.setFilePath(savedName);
        t.setCreatedAt(LocalDateTime.now());
        templateMapper.insert(t);

        TemplateVO vo = toVO(t);
        return Result.success(vo);
    }

    @Override
    public Result<List<TemplateVO>> listTemplates() {
        Long currentUserId = currentUserId();
        if (currentUserId == null) {
            throw new BusinessException("请先登录查看模板列表");
        }
        List<Template> list = templateMapper.selectAllByOwner(currentUserId);
        List<TemplateVO> vos = list.stream().map(this::toVO).toList();
        return Result.success(vos);
    }

    @Override
    public Result<List<TemplateVO>> listByReportType(Long reportTypeId) {
        Long currentUserId = currentUserId();
        if (currentUserId == null) {
            throw new BusinessException("请先登录查看模板列表");
        }
        List<Template> list = templateMapper.selectByReportTypeIdAndOwner(reportTypeId, currentUserId);
        List<TemplateVO> vos = list.stream().map(this::toVO).toList();
        return Result.success(vos);
    }

    @Override
    public Result<TemplateVO> getById(Long templateId) {
        Template t = templateMapper.selectById(templateId);
        if (t == null) {
            throw new BusinessException("模板不存在");
        }
        Long currentUserId = currentUserId();
        if (currentUserId == null || (t.getOwnerId() != null && !currentUserId.equals(t.getOwnerId()))) {
            throw new BusinessException("无权访问该模板");
        }
        TemplateVO vo = toVO(t);
        return Result.success(vo);
    }

    @Override
    public Result<TemplateVO> updateTemplate(Long templateId, TemplateVO vo) {
        Template t = templateMapper.selectById(templateId);
        if (t == null) {
            throw new BusinessException("模板不存在");
        }
        Long currentUserId = currentUserId();
        if (currentUserId == null || (t.getOwnerId() != null && !currentUserId.equals(t.getOwnerId()))) {
            throw new BusinessException("无权修改该模板");
        }
        if (vo.getFileName() != null && !vo.getFileName().isBlank()) {
            t.setFileName(vo.getFileName().trim());
        }
        // 允许前端切换报表类型（可为 null）
        if (vo.getReportTypeId() != null || vo.getReportTypeId() == null) {
            t.setReportTypeId(vo.getReportTypeId());
        }
        templateMapper.update(t);
        return Result.success(toVO(t));
    }

    @Override
    public Result<Boolean> deleteTemplate(Long templateId) {
        Template t = templateMapper.selectById(templateId);
        if (t == null) {
            throw new BusinessException("模板不存在");
        }
        Long currentUserId = currentUserId();
        if (currentUserId == null || (t.getOwnerId() != null && !currentUserId.equals(t.getOwnerId()))) {
            throw new BusinessException("无权删除该模板");
        }
        int rows = templateMapper.deleteById(templateId);
        return Result.success(rows > 0);
    }
    //->VO
    private TemplateVO toVO(Template t) {
        TemplateVO vo = new TemplateVO();
        vo.setId(t.getId());
        vo.setReportTypeId(t.getReportTypeId());
        vo.setFileName(t.getFileName());
        vo.setFileType(t.getFileType());
        vo.setCreatedAt(t.getCreatedAt());
        return vo;
    }
    //拿到文件后缀名
    private static String getExtension(String filename) {
        int i = filename.lastIndexOf('.');
        return i < 0 ? "" : filename.substring(i + 1);
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
}
