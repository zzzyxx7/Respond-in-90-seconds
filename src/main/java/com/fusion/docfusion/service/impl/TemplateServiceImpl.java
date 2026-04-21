package com.fusion.docfusion.service.impl;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.config.UploadProperties;
import com.fusion.docfusion.dto.HistorySyncRequest;
import com.fusion.docfusion.dto.HistorySyncResultVO;
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

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    @Override
    public Result<HistorySyncResultVO> syncTemplateHistory(HistorySyncRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_REQUIRED, "请先登录后再同步模板历史");
        }
        List<String> ids = normalizePublicIds(request);
        HistorySyncResultVO result = new HistorySyncResultVO();
        result.setTotal(ids.size());
        if (ids.isEmpty()) {
            result.setClaimed(0);
            result.setAlreadyOwned(0);
            result.setNotFound(0);
            result.setForbidden(0);
            return Result.success(result);
        }

        Map<String, Template> existing = templateMapper.selectByPublicIds(ids).stream()
                .collect(Collectors.toMap(Template::getPublicId, Function.identity(), (a, b) -> a));
        for (String publicId : ids) {
            Template template = existing.get(publicId);
            if (template == null) {
                result.getNotFoundIds().add(publicId);
                continue;
            }
            if (template.getOwnerId() == null) {
                int updated = templateMapper.claimOwnerByPublicId(publicId, currentUserId);
                if (updated > 0) {
                    result.getClaimedIds().add(publicId);
                } else {
                    Template latest = templateMapper.selectByPublicId(publicId);
                    if (latest != null && currentUserId.equals(latest.getOwnerId())) {
                        result.getAlreadyOwnedIds().add(publicId);
                    } else {
                        result.getForbiddenIds().add(publicId);
                    }
                }
                continue;
            }
            if (currentUserId.equals(template.getOwnerId())) {
                result.getAlreadyOwnedIds().add(publicId);
            } else {
                result.getForbiddenIds().add(publicId);
            }
        }

        result.setClaimed(result.getClaimedIds().size());
        result.setAlreadyOwned(result.getAlreadyOwnedIds().size());
        result.setNotFound(result.getNotFoundIds().size());
        result.setForbidden(result.getForbiddenIds().size());
        return Result.success(result);
    }

    @Override
    public void writeTemplateFileByPublicId(String templatePublicId, HttpServletResponse response) {
        if (templatePublicId == null || templatePublicId.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        Template t = templateMapper.selectByPublicId(templatePublicId.trim());
        if (t == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Long currentUserId = SecurityUtils.currentUserId();
        if (t.getOwnerId() != null && (currentUserId == null || !currentUserId.equals(t.getOwnerId()))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        if (t.getFilePath() == null || t.getFilePath().isBlank()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Path basePath = Paths.get(uploadProperties.getTemplatesDir()).normalize();
        Path filePath = basePath.resolve(t.getFilePath()).normalize();
        if (!filePath.startsWith(basePath)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            String downloadName = t.getFileName() != null && !t.getFileName().isBlank()
                    ? t.getFileName()
                    : filePath.getFileName().toString();
            String asciiName = downloadName.replaceAll("[^a-zA-Z0-9._-]", "_");
            if (asciiName.isBlank()) {
                asciiName = "template.bin";
            }
            String encodedPreferred = URLEncoder.encode(downloadName, StandardCharsets.UTF_8).replace("+", "%20");
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(resolveTemplateContentType(downloadName));
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + asciiName + "\"; filename*=UTF-8''" + encodedPreferred);
            long len = resource.contentLength();
            if (len >= 0) {
                response.setContentLengthLong(len);
            }
            try (InputStream in = resource.getInputStream(); OutputStream out = response.getOutputStream()) {
                in.transferTo(out);
                out.flush();
            }
        } catch (Exception e) {
            log.warn("读取模板文件失败, templatePublicId={}", templatePublicId, e);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private static String resolveTemplateContentType(String filename) {
        if (filename == null) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if (lower.endsWith(".xls")) {
            return "application/vnd.ms-excel";
        }
        if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (lower.endsWith(".doc")) {
            return "application/msword";
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private static List<String> normalizePublicIds(HistorySyncRequest request) {
        if (request == null || request.getPublicIds() == null || request.getPublicIds().isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(request.getPublicIds().stream()
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }
    //->VO
    private TemplateVO toVO(Template t) {
        TemplateVO vo = new TemplateVO();
        vo.setId(t.getId());
        vo.setPublicId(t.getPublicId());
        vo.setReportTypeId(t.getReportTypeId());
        vo.setFileName(t.getFileName());
        vo.setFileType(t.getFileType());
        vo.setFileSize(resolveTemplateFileSize(t));
        vo.setCreatedAt(t.getCreatedAt());
        return vo;
    }

    private Long resolveTemplateFileSize(Template template) {
        if (template == null || template.getFilePath() == null || template.getFilePath().isBlank()) {
            return null;
        }
        try {
            Path filePath = Paths.get(uploadProperties.getTemplatesDir()).resolve(template.getFilePath()).normalize();
            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                return null;
            }
            return Files.size(filePath);
        } catch (Exception e) {
            log.warn("读取模板文件大小失败, publicId={}, filePath={}", template.getPublicId(), template.getFilePath(), e);
            return null;
        }
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
