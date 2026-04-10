package com.fusion.docfusion.service.impl;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.config.UploadProperties;
import com.fusion.docfusion.dto.TemplateVO;
import com.fusion.docfusion.entity.Template;
import com.fusion.docfusion.exception.BusinessException;
import com.fusion.docfusion.exception.ErrorCode;
import com.fusion.docfusion.mapper.TemplateMapper;
import com.fusion.docfusion.security.SecurityUtils;
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
    /**
     * 单个模板文件大小上限（bytes），主要是“兜底”。
     * Spring 的 max-file-size 已限制，但这里再加一层，错误信息更可控。
     */
    private static final long MAX_TEMPLATE_UPLOAD_BYTES = 50L * 1024 * 1024;

    private final UploadProperties uploadProperties;
    private final TemplateMapper templateMapper;

    @Override
    public Result<TemplateVO> uploadTemplate(MultipartFile file) {
        Long currentUserId = SecurityUtils.currentUserId();
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.TEMPLATE_UPLOAD_EMPTY);
        }
        if (file.getSize() > MAX_TEMPLATE_UPLOAD_BYTES) {
            throw new BusinessException(ErrorCode.TEMPLATE_TOO_LARGE);
        }

        String originalFilename = file.getOriginalFilename();
        String safeFilename = sanitizeFilename(originalFilename);
        if (safeFilename == null || safeFilename.isBlank()) {
            throw new BusinessException(ErrorCode.TEMPLATE_FILENAME_INVALID);
        }
        String ext = getExtension(safeFilename).toLowerCase();
        if (!ALLOWED_TYPES.contains(ext)) {
            throw new BusinessException(ErrorCode.TEMPLATE_TYPE_UNSUPPORTED);
        }
        Path basePath = Paths.get(uploadProperties.getTemplatesDir());
        try {
            Files.createDirectories(basePath);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.TEMPLATE_DIR_FAIL, "创建模板目录失败: " + e.getMessage());
        }
        String savedName = UUID.randomUUID().toString() + "_" + safeFilename;

        // 防止路径穿越：确保最终落点仍在 basePath 内
        Path normalizedBasePath = basePath.normalize();
        Path target = basePath.resolve(savedName).normalize();
        if (!target.startsWith(normalizedBasePath)) {
            throw new BusinessException(ErrorCode.TEMPLATE_PATH_INVALID);
        }

        try {
            file.transferTo(target.toFile());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.TEMPLATE_SAVE_FAILED, "保存模板失败: " + e.getMessage());
        }
        String fileType = ext.equals("doc") || ext.equals("docx") ? "word" : "excel";
        Template t = new Template();
        // 允许匿名上传：ownerId 可为空；登录后写入 ownerId 便于“历史记录/隔离”
        t.setOwnerId(currentUserId);
        t.setPublicId(generatePublicId());
        t.setReportTypeId(null);
        t.setFileName(safeFilename);
        t.setFileType(fileType);
        t.setFilePath(savedName);
        t.setCreatedAt(LocalDateTime.now());
        templateMapper.insert(t);

        TemplateVO vo = toVO(t);
        return Result.success(vo);
    }

    @Override
    public Result<List<TemplateVO>> listTemplates() {
        Long currentUserId = SecurityUtils.currentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_REQUIRED, "请先登录查看模板列表");
        }
        List<Template> list = templateMapper.selectAllByOwner(currentUserId);
        List<TemplateVO> vos = list.stream().map(this::toVO).toList();
        return Result.success(vos);
    }

    @Override
    public Result<List<TemplateVO>> listByReportType(Long reportTypeId) {
        Long currentUserId = SecurityUtils.currentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_REQUIRED, "请先登录查看模板列表");
        }
        List<Template> list = templateMapper.selectByReportTypeIdAndOwner(reportTypeId, currentUserId);
        List<TemplateVO> vos = list.stream().map(this::toVO).toList();
        return Result.success(vos);
    }

    @Override
    public Result<TemplateVO> getByPublicId(String templatePublicId) {
        if (templatePublicId == null || templatePublicId.isBlank()) {
            throw new BusinessException(ErrorCode.TEMPLATE_PUBLIC_ID_INVALID);
        }
        Template t = templateMapper.selectByPublicId(templatePublicId);
        if (t == null) {
            throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND);
        }
        // 匿名上传的模板 ownerId 为空：允许通过 publicId 访问；有 ownerId 的仍按权限校验
        Long currentUserId = SecurityUtils.currentUserId();
        if (t.getOwnerId() != null && (currentUserId == null || !currentUserId.equals(t.getOwnerId()))) {
            throw new BusinessException(ErrorCode.TEMPLATE_FORBIDDEN_VIEW);
        }
        return Result.success(toVO(t));
    }

    @Override
    public Result<TemplateVO> updateTemplate(Long templateId, TemplateVO vo) {
        Template t = templateMapper.selectById(templateId);
        if (t == null) {
            throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND);
        }
        Long currentUserId = SecurityUtils.currentUserId();
        if (currentUserId == null || (t.getOwnerId() != null && !currentUserId.equals(t.getOwnerId()))) {
            throw new BusinessException(ErrorCode.TEMPLATE_FORBIDDEN_UPDATE);
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
            throw new BusinessException(ErrorCode.TEMPLATE_NOT_FOUND);
        }
        Long currentUserId = SecurityUtils.currentUserId();
        if (currentUserId == null || (t.getOwnerId() != null && !currentUserId.equals(t.getOwnerId()))) {
            throw new BusinessException(ErrorCode.TEMPLATE_FORBIDDEN_DELETE);
        }
        int rows = templateMapper.deleteById(templateId);
        return Result.success(rows > 0);
    }
    //->VO
    private TemplateVO toVO(Template t) {
        TemplateVO vo = new TemplateVO();
        vo.setId(t.getId());
        vo.setPublicId(t.getPublicId());
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

    /**
     * 清理上传文件名，防止携带路径（如 C:\fakepath\xxx 或 ../xxx）。
     */
    private static String sanitizeFilename(String originalFilename) {
        if (originalFilename == null) return null;
        String name = originalFilename.replace('\\', '/');
        int idx = name.lastIndexOf('/');
        if (idx >= 0) {
            name = name.substring(idx + 1);
        }
        name = name.replace("..", "");
        name = name.replace('\r', '_').replace('\n', '_');
        return name.trim();
    }

    private static String generatePublicId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

}
