package com.fusion.docfusion.controller;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.dto.DocumentSetVO;
import com.fusion.docfusion.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档集与文档上传（比赛：一次性上传 docx/md/xlsx/txt）
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /**
     * 一次性上传多个文档，创建文档集
     * POST /api/documents/upload
     * Content-Type: multipart/form-data, key 建议用 "files"
     */
    @PostMapping("/upload")
    public Result<DocumentSetVO> uploadDocuments(@RequestParam("files") List<MultipartFile> files) {
        return documentService.uploadDocuments(files);
    }

    /**
     * 查询文档集详情（含文档列表）
     * GET /api/documents/sets/{documentSetId}
     */
    @GetMapping("/sets/{documentSetId}")
    public Result<DocumentSetVO> getDocumentSet(@PathVariable Long documentSetId) {
        return documentService.getDocumentSet(documentSetId);
    }
}
