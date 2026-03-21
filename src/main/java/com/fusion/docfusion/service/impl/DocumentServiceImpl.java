package com.fusion.docfusion.service.impl;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.config.UploadProperties;
import com.fusion.docfusion.dto.DocumentSetListItemVO;
import com.fusion.docfusion.dto.DocumentSetVO;
import com.fusion.docfusion.dto.DocumentVO;
import com.fusion.docfusion.entity.Document;
import com.fusion.docfusion.entity.DocumentSet;
import com.fusion.docfusion.exception.BusinessException;
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
        if (currentUserId == null) {
            throw new BusinessException("请先登录再上传文档");
        }
        if (files == null || files.isEmpty()) {
            throw new BusinessException("请至少上传一个文档");
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
            throw new BusinessException(400, "单次上传文件总大小过大，请控制在 100MB 以内");
        }

        Path basePath = Paths.get(uploadProperties.getDocsDir());
        try {
            Files.createDirectories(basePath);
        } catch (IOException e) {
            throw new BusinessException("创建上传目录失败: " + e.getMessage());
        }
        String dirName = "set_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8);
        Path setPath = basePath.resolve(dirName);
        try {
            Files.createDirectories(setPath);
        } catch (IOException e) {
            throw new BusinessException("创建文档集目录失败: " + e.getMessage());
        }

        DocumentSet set = new DocumentSet();
        set.setOwnerId(currentUserId);
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
                throw new BusinessException(400, "非法文件名或路径");
            }

            try {
                file.transferTo(target.toFile());
            } catch (IOException e) {
                throw new BusinessException("保存文件失败: " + safeFilename + ", " + e.getMessage());
            }
            Document doc = new Document();
            doc.setDocumentSetId(documentSetId);
            doc.setFileName(safeFilename);
            doc.setFileType(ext);
            doc.setFilePath(dirName + "/" + savedName);
            doc.setFileSize(file.getSize());
            doc.setCreatedAt(LocalDateTime.now());
            documentMapper.insert(doc);

            DocumentVO vo = new DocumentVO();
            vo.setId(doc.getId());
            vo.setFileName(doc.getFileName());
            vo.setFileType(doc.getFileType());
            vo.setFileSize(doc.getFileSize());
            vo.setCreatedAt(doc.getCreatedAt());
            docList.add(vo);
        }
        if (docList.isEmpty()) {
            throw new BusinessException("没有可保存的文档，请上传支持的类型（docx、md、xlsx、txt、pdf）");
        }

        DocumentSetVO setVO = new DocumentSetVO();
        setVO.setId(documentSetId);
        setVO.setName(set.getName());
        setVO.setCreatedAt(set.getCreatedAt());
        setVO.setDocuments(docList);
        return Result.success(setVO);
    }

    @Override
    public Result<List<DocumentSetListItemVO>> listDocumentSets() {
        Long currentUserId = SecurityUtils.currentUserId();
        if (currentUserId == null) {
            throw new BusinessException("请先登录查看文档集列表");
        }
        List<DocumentSetListItemVO> list = documentSetMapper.selectAllForList(currentUserId);
        return Result.success(list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> deleteDocumentSet(Long documentSetId) {
        DocumentSet set = documentSetMapper.selectById(documentSetId);
        if (set == null) {
            throw new BusinessException("文档集不存在");
        }
        Long currentUserId = SecurityUtils.currentUserId();
        if (currentUserId == null || (set.getOwnerId() != null && !currentUserId.equals(set.getOwnerId()))) {
            throw new BusinessException("无权删除该文档集");
        }
        int rows = documentSetMapper.deleteById(documentSetId);
        return Result.success(rows > 0);
    }

    @Override
    public Result<DocumentSetVO> getDocumentSet(Long documentSetId) {
        DocumentSet set = documentSetMapper.selectById(documentSetId);
        if (set == null) {
            throw new BusinessException("文档集不存在");
        }
        Long currentUserId = SecurityUtils.currentUserId();
        if (currentUserId == null || (set.getOwnerId() != null && !currentUserId.equals(set.getOwnerId()))) {
            throw new BusinessException("无权查看该文档集");
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
        vo.setName(set.getName());
        vo.setCreatedAt(set.getCreatedAt());
        vo.setDocuments(list);
        return Result.success(vo);
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

}
