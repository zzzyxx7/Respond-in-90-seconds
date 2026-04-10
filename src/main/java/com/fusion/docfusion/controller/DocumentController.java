package com.fusion.docfusion.controller;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.dto.DocumentSetListItemVO;
import com.fusion.docfusion.dto.DocumentSetVO;
import com.fusion.docfusion.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档集与文档上传（比赛：一次性上传 docx/md/xlsx/txt/pdf）
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentService documentService;

    /**
     * 一次性上传多个文档，创建文档集
     * POST /api/documents/upload
     * Content-Type: multipart/form-data, key 建议用 "files"
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DocumentSetVO> uploadDocuments(@RequestPart("files") List<MultipartFile> files) {
        log.info("上传文档集请求, fileCount={}", files == null ? 0 : files.size());
        return documentService.uploadDocuments(files);
    }

    /**
     * 查询文档集列表（用于创建任务时选择文档集）
     * GET /api/documents/sets
     */
    @GetMapping("/sets")
    public Result<List<DocumentSetListItemVO>> listDocumentSets() {
        log.info("查询文档集列表");
        return documentService.listDocumentSets();
    }

    /**
     * 删除文档集（级联删除其中的文档与相关任务）
     * DELETE /api/documents/sets/public/{documentSetPublicId}
     */
    @DeleteMapping("/sets/public/{documentSetPublicId}")
    public Result<Boolean> deleteDocumentSetByPublicId(@PathVariable String documentSetPublicId) {
        log.info("删除文档集(公共ID), documentSetPublicId={}", documentSetPublicId);
        return documentService.deleteDocumentSetByPublicId(documentSetPublicId);
    }

    /**
     * 按 publicId 查询文档集详情（用于防枚举/匿名场景）。
     * GET /api/documents/sets/public/{documentSetPublicId}
     */
    @GetMapping("/sets/public/{documentSetPublicId}")
    public Result<DocumentSetVO> getDocumentSetByPublicId(@PathVariable String documentSetPublicId) {
        log.info("查询文档集(公共ID), documentSetPublicId={}", documentSetPublicId);
        return documentService.getDocumentSetByPublicId(documentSetPublicId);
    }
}
