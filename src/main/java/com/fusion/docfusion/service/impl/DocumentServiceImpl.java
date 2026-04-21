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
    private static final long MAX_TOTAL_UPLOAD_BYTES = 100L * 1024 * 1024;

    private final UploadProperties uploadProperties;
    private final DocumentSetMapper documentSetMapper;
    private final DocumentMapper documentMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<DocumentSetVO> uploadDocuments(List<MultipartFile> files) {
        Long currentUserId = SecurityUtils.currentUserId();
        validateUploadFiles(files);

        Path basePath = Paths.get(uploadProperties.getDocsDir());
        createDirectories(basePath, ErrorCode.DOC_UPLOAD_DIR_FAIL, "create docs dir failed");

        String dirName = "set_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        Path setPath = basePath.resolve(dirName);
        createDirectories(setPath, ErrorCode.DOC_SET_DIR_FAIL, "create document set dir failed");

        DocumentSet set = new DocumentSet();
        set.setOwnerId(currentUserId);
        set.setPublicId(generatePublicId());
        set.setName(dirName);
        set.setCreatedAt(LocalDateTime.now());
        documentSetMapper.insert(set);

        List<DocumentVO> savedDocuments = saveDocuments(files, setPath, dirName, set.getId());
        if (savedDocuments.isEmpty()) {
            throw new BusinessException(ErrorCode.DOC_NO_VALID_FILES);
        }

        DocumentSetVO setVO = new DocumentSetVO();
        setVO.setId(set.getId());
        setVO.setPublicId(set.getPublicId());
        setVO.setName(set.getName());
        setVO.setCreatedAt(set.getCreatedAt());
        setVO.setDocuments(savedDocuments);
        return Result.success(setVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<DocumentSetVO> appendDocumentsByPublicId(String documentSetPublicId, List<MultipartFile> files) {
        if (documentSetPublicId == null || documentSetPublicId.isBlank()) {
            throw new BusinessException(ErrorCode.DOCUMENT_SET_PUBLIC_ID_INVALID);
        }
        validateUploadFiles(files);

        DocumentSet set = documentSetMapper.selectByPublicId(documentSetPublicId);
        if (set == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_SET_NOT_FOUND);
        }
        ensureSetAccessible(set, ErrorCode.DOCUMENT_SET_VIEW_FORBIDDEN);

        Path setPath = Paths.get(uploadProperties.getDocsDir()).resolve(set.getName()).normalize();
        createDirectories(setPath, ErrorCode.DOC_SET_DIR_FAIL, "create document set dir failed");

        List<DocumentVO> savedDocuments = saveDocuments(files, setPath, set.getName(), set.getId());
        if (savedDocuments.isEmpty()) {
            throw new BusinessException(ErrorCode.DOC_NO_VALID_FILES);
        }
        return getDocumentSet(set.getId());
    }

    @Override
    public Result<List<DocumentSetListItemVO>> listDocumentSets() {
        Long currentUserId = SecurityUtils.currentUserId();
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.AUTH_LOGIN_REQUIRED, "请先登录");
        }
        return Result.success(documentSetMapper.selectAllForList(currentUserId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> deleteDocumentSet(Long documentSetId) {
        DocumentSet set = documentSetMapper.selectById(documentSetId);
        if (set == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_SET_NOT_FOUND);
        }
        ensureSetAccessible(set, ErrorCode.DOCUMENT_SET_DELETE_FORBIDDEN);
        return Result.success(documentSetMapper.deleteById(documentSetId) > 0);
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
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> deleteDocumentByPublicId(String documentSetPublicId, String filePublicId) {
        if (documentSetPublicId == null || documentSetPublicId.isBlank()) {
            throw new BusinessException(ErrorCode.DOCUMENT_SET_PUBLIC_ID_INVALID);
        }
        if (filePublicId == null || filePublicId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "filePublicId required");
        }

        DocumentSet set = documentSetMapper.selectByPublicId(documentSetPublicId);
        if (set == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_SET_NOT_FOUND);
        }
        ensureSetAccessible(set, ErrorCode.DOCUMENT_SET_DELETE_FORBIDDEN);

        Document doc = documentMapper.selectByPublicId(filePublicId);
        if (doc == null || doc.getDocumentSetId() == null || !doc.getDocumentSetId().equals(set.getId())) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }

        deleteStoredDocumentFile(doc);
        return Result.success(documentMapper.deleteById(doc.getId()) > 0);
    }

    @Override
    public Result<DocumentSetVO> getDocumentSet(Long documentSetId) {
        DocumentSet set = documentSetMapper.selectById(documentSetId);
        if (set == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_SET_NOT_FOUND);
        }
        ensureSetAccessible(set, ErrorCode.DOCUMENT_SET_VIEW_FORBIDDEN);

        List<DocumentVO> documents = documentMapper.selectByDocumentSetId(documentSetId).stream()
                .map(DocumentServiceImpl::toDocumentVO)
                .toList();

        DocumentSetVO vo = new DocumentSetVO();
        vo.setId(set.getId());
        vo.setPublicId(set.getPublicId());
        vo.setName(set.getName());
        vo.setCreatedAt(set.getCreatedAt());
        vo.setDocuments(documents);
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
        return getDocumentSet(set.getId());
    }

    private void validateUploadFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorCode.DOC_UPLOAD_EMPTY);
        }
        long totalSize = 0L;
        for (MultipartFile file : files) {
            if (file != null && file.getSize() > 0) {
                totalSize += file.getSize();
            }
        }
        if (totalSize > MAX_TOTAL_UPLOAD_BYTES) {
            throw new BusinessException(ErrorCode.DOC_UPLOAD_TOO_LARGE);
        }
    }

    private void ensureSetAccessible(DocumentSet set, ErrorCode forbiddenCode) {
        Long currentUserId = SecurityUtils.currentUserId();
        if (set.getOwnerId() != null && (currentUserId == null || !currentUserId.equals(set.getOwnerId()))) {
            throw new BusinessException(forbiddenCode);
        }
    }

    private void createDirectories(Path path, ErrorCode code, String message) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new BusinessException(code, message + ": " + e.getMessage());
        }
    }

    private List<DocumentVO> saveDocuments(List<MultipartFile> files, Path setPath, String dirName, Long documentSetId) {
        List<DocumentVO> saved = new ArrayList<>();
        for (MultipartFile file : files) {
            DocumentVO vo = saveDocument(file, setPath, dirName, documentSetId);
            if (vo != null) {
                saved.add(vo);
            }
        }
        return saved;
    }

    private DocumentVO saveDocument(MultipartFile file, Path setPath, String dirName, Long documentSetId) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String safeFilename = sanitizeFilename(file.getOriginalFilename());
        if (safeFilename == null || safeFilename.isBlank()) {
            return null;
        }
        String ext = getExtension(safeFilename).toLowerCase();
        if (!ALLOWED_TYPES.contains(ext)) {
            log.warn("skip unsupported file type, filename={}", safeFilename);
            return null;
        }

        String savedName = UUID.randomUUID() + "_" + safeFilename;
        Path normalizedSetPath = setPath.normalize();
        Path target = setPath.resolve(savedName).normalize();
        if (!target.startsWith(normalizedSetPath)) {
            throw new BusinessException(ErrorCode.DOC_INVALID_PATH);
        }

        try {
            file.transferTo(target.toFile());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.DOC_SAVE_FAILED,
                    "save file failed: " + safeFilename + ", " + e.getMessage());
        }

        Document doc = new Document();
        doc.setPublicId(generatePublicId());
        doc.setDocumentSetId(documentSetId);
        doc.setFileName(safeFilename);
        doc.setFileType(ext);
        doc.setFilePath(dirName + "/" + savedName);
        doc.setFileSize(file.getSize());
        doc.setCreatedAt(LocalDateTime.now());
        documentMapper.insert(doc);
        return toDocumentVO(doc);
    }

    private void deleteStoredDocumentFile(Document doc) {
        if (doc == null || doc.getFilePath() == null || doc.getFilePath().isBlank()) {
            return;
        }
        Path docsRoot = Paths.get(uploadProperties.getDocsDir()).normalize();
        Path target = docsRoot.resolve(doc.getFilePath()).normalize();
        if (!target.startsWith(docsRoot)) {
            throw new BusinessException(ErrorCode.DOC_INVALID_PATH);
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.DOC_SAVE_FAILED, "delete file failed: " + e.getMessage());
        }
    }

    private static DocumentVO toDocumentVO(Document doc) {
        DocumentVO vo = new DocumentVO();
        vo.setId(doc.getId());
        vo.setPublicId(doc.getPublicId());
        vo.setFileName(doc.getFileName());
        vo.setFileType(doc.getFileType());
        vo.setFileSize(doc.getFileSize());
        vo.setCreatedAt(doc.getCreatedAt());
        return vo;
    }

    private static String getExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx < 0 ? "" : filename.substring(idx + 1);
    }

    private static String sanitizeFilename(String originalFilename) {
        if (originalFilename == null) {
            return null;
        }
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
