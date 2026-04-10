package com.fusion.docfusion.service.impl;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.config.UploadProperties;
import com.fusion.docfusion.dto.DocumentSetListItemVO;
import com.fusion.docfusion.dto.DocumentSetVO;
import com.fusion.docfusion.dto.DocumentVO;
import com.fusion.docfusion.entity.Document;
import com.fusion.docfusion.entity.DocumentSet;
import com.fusion.docfusion.exception.BusinessException;
import com.fusion.docfusion.exception.ErrorCode;
import com.fusion.docfusion.mapper.DocumentMapper;
import com.fusion.docfusion.mapper.DocumentSetMapper;
import com.fusion.docfusion.security.SecurityUtils;
import com.fusion.docfusion.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentServiceImpl implements DocumentService {

    private static final List<String> ALLOWED_TYPES = List.of("docx", "md", "xlsx", "txt", "pdf");
    /**
     * 单次上传文档集的“总大小”上限（bytes）。
     * 这里用于防止一次性上传几十 GB 导致内存/磁盘压力。
     * 同时也对齐你 application.yml 里的 max-request-size（100MB）。
     */
    private static final long MAX_TOTAL_UPLOAD_BYTES = 100L * 1024 * 1024;

    private final UploadProperties uploadProperties;
    private final DocumentSetMapper documentSetMapper;
    private final DocumentMapper documentMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<DocumentSetVO> uploadDocuments(List<MultipartFile> files) {
        Long currentUserId = SecurityUtils.currentUserId();
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.DOC_UPLOAD_EMPTY);
        }

        long totalSize = 0L;
        for (MultipartFile f : files) {
            if (f == null) continue;
            long size = f.getSize();
            if (size > 0) {
                totalSize += size;
            }
        }
        if (totalSize > MAX_TOTAL_UPLOAD_BYTES) {
            throw new BusinessException(ErrorCode.DOC_UPLOAD_TOO_LARGE);
        }

        Path basePath = Paths.get(uploadProperties.getDocsDir());
        try {
            Files.createDirectories(basePath);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.DOC_UPLOAD_DIR_FAIL, "创建上传目录失败: " + e.getMessage());
        }
        String dirName = "set_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        Path setPath = basePath.resolve(dirName);
        try {
            Files.createDirectories(setPath);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.DOC_SET_DIR_FAIL, "创建文档集目录失败: " + e.getMessage());
        }

        DocumentSet set = new DocumentSet();
        // 允许匿名上传：ownerId 可为空。若登录则写入 ownerId 便于“历史记录/隔离”。
        set.setOwnerId(currentUserId);
        set.setPublicId(generatePublicId());
        set.setName(dirName);
        set.setCreatedAt(LocalDateTime.now());
        documentSetMapper.insert(set);
        Long documentSetId = set.getId();

        List<DocumentVO> docList = new ArrayList<>();
        for (MultipartFile file : files) {
            String originalFilename = file.getOriginalFilename();
            String safeFilename = sanitizeFilename(originalFilename);
            if (safeFilename == null || safeFilename.isBlank()) continue;

            String ext = getExtension(safeFilename).toLowerCase();
            if (!ALLOWED_TYPES.contains(ext)) {
                log.warn("跳过不支持的类型: {}", safeFilename);
                continue;
            }
            String savedName = UUID.randomUUID().toString() + "_" + safeFilename;

            // 防止路径穿越：确保最终落点仍在 setPath 内
            Path normalizedSetPath = setPath.normalize();
            Path target = setPath.resolve(savedName).normalize();
            if (!target.startsWith(normalizedSetPath)) {
                throw new BusinessException(ErrorCode.DOC_INVALID_PATH);
            }

            try {
                file.transferTo(target.toFile());
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.DOC_SAVE_FAILED, "保存文件失败: " + safeFilename + ", " + e.getMessage());
            }
            Document doc = new Document();
            doc.setPublicId(UUID.randomUUID().toString().replace("-", ""));
            doc.setDocumentSetId(documentSetId);
            doc.setFileName(safeFilename);
            doc.setFileType(ext);
            doc.setFilePath(dirName + "/" + savedName);
            doc.setFileSize(file.getSize());
            doc.setCreatedAt(LocalDateTime.now());
            documentMapper.insert(doc);

            DocumentVO vo = new DocumentVO();
            vo.setId(doc.getId());
            vo.setPublicId(doc.getPublicId());
            vo.setFileName(doc.getFileName());
            vo.setFileType(doc.getFileType());
            vo.setFileSize(doc.getFileSize());
            vo.setCreatedAt(doc.getCreatedAt());
            docList.add(vo);
        }
        if (docList.isEmpty()) {
            throw new BusinessException(ErrorCode.DOC_NO_VALID_FILES);
        }

        DocumentSetVO setVO = new DocumentSetVO();
        setVO.setId(documentSetId);
        setVO.setPublicId(set.getPublicId());
        setVO.setName(set.getName());
        setVO.setCreatedAt(set.getCreatedAt());
        setVO.setDocuments(docList);
        return Result.success(setVO);
    }

    @Override
    public Result<List<DocumentSetListItemVO>> listDocumentSets() {
        Long currentUserId = SecurityUtils.currentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_REQUIRED, "请先登录查看文档集列表");
        }
        List<DocumentSetListItemVO> list = documentSetMapper.selectAllForList(currentUserId);
        return Result.success(list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> deleteDocumentSet(Long documentSetId) {
        DocumentSet set = documentSetMapper.selectById(documentSetId);
        if (set == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_SET_NOT_FOUND);
        }
        Long currentUserId = SecurityUtils.currentUserId();
        if (currentUserId == null || (set.getOwnerId() != null && !currentUserId.equals(set.getOwnerId()))) {
            throw new BusinessException(ErrorCode.DOCUMENT_SET_DELETE_FORBIDDEN);
        }
        int rows = documentSetMapper.deleteById(documentSetId);
        return Result.success(rows > 0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> deleteDocumentSetByPublicId(String documentSetPublicId) {
        if (documentSetPublicId == null || documentSetPublicId.isBlank()) {
            throw new BusinessException(ErrorCode.DOCUMENT_SET_PUBLIC_ID_INVALID);
        }
        DocumentSet set = documentSetMapper.selectByPublicId(documentSetPublicId);
        if (set == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_SET_NOT_FOUND);
        }
        return deleteDocumentSet(set.getId());
    }

    @Override
    public Result<DocumentSetVO> getDocumentSet(Long documentSetId) {
        DocumentSet set = documentSetMapper.selectById(documentSetId);
        if (set == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_SET_NOT_FOUND);
        }
        Long currentUserId = SecurityUtils.currentUserId();
        if (currentUserId == null || (set.getOwnerId() != null && !currentUserId.equals(set.getOwnerId()))) {
            throw new BusinessException(ErrorCode.DOCUMENT_SET_VIEW_FORBIDDEN);
        }
        List<Document> docs = documentMapper.selectByDocumentSetId(documentSetId);
        List<DocumentVO> list = docs.stream().map(d -> {
            DocumentVO vo = new DocumentVO();
            vo.setId(d.getId());
            vo.setFileName(d.getFileName());
            vo.setFileType(d.getFileType());
            vo.setFileSize(d.getFileSize());
            vo.setCreatedAt(d.getCreatedAt());
            return vo;
        }).toList();
        DocumentSetVO vo = new DocumentSetVO();
        vo.setId(set.getId());
        vo.setPublicId(set.getPublicId());
        vo.setName(set.getName());
        vo.setCreatedAt(set.getCreatedAt());
        vo.setDocuments(list);
        return Result.success(vo);
    }

    @Override
    public Result<DocumentSetVO> getDocumentSetByPublicId(String documentSetPublicId) {
        if (documentSetPublicId == null || documentSetPublicId.isBlank()) {
            throw new BusinessException(ErrorCode.DOCUMENT_SET_PUBLIC_ID_INVALID);
        }
        DocumentSet set = documentSetMapper.selectByPublicId(documentSetPublicId);
        if (set == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_SET_NOT_FOUND);
        }
        // 匿名上传的 set.ownerId 为空：允许通过 publicId 访问；有 ownerId 的仍按权限校验
        Long currentUserId = SecurityUtils.currentUserId();
        if (set.getOwnerId() != null && (currentUserId == null || !currentUserId.equals(set.getOwnerId()))) {
            throw new BusinessException(ErrorCode.DOCUMENT_SET_VIEW_FORBIDDEN);
        }
        return getDocumentSet(set.getId());
    }

    private static String getExtension(String filename) {
        int i = filename.lastIndexOf('.');
        return i < 0 ? "" : filename.substring(i + 1);
    }

    /**
     * 清理上传文件名，防止携带路径（如 C:\fakepath\xxx 或 ../xxx）。
     * 保留后缀和主体信息用于保存展示。
     */
    private static String sanitizeFilename(String originalFilename) {
        if (originalFilename == null) return null;
        // 统一分隔符，去掉可能的目录部分
        String name = originalFilename.replace('\\', '/');
        int idx = name.lastIndexOf('/');
        if (idx >= 0) {
            name = name.substring(idx + 1);
        }
        // 移除明显的路径穿越片段
        name = name.replace("..", "");
        // 防止换行/特殊字符干扰日志或展示
        name = name.replace('\r', '_').replace('\n', '_');
        return name.trim();
    }

    private static String generatePublicId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

}
