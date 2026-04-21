package com.fusion.docfusion.controller;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.dto.DocumentDeleteRequest;
import com.fusion.docfusion.dto.DocumentSetListItemVO;
import com.fusion.docfusion.dto.DocumentSetVO;
import com.fusion.docfusion.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DocumentSetVO> uploadDocuments(@RequestPart("files") List<MultipartFile> files) {
        log.info("upload document set, fileCount={}", files == null ? 0 : files.size());
        return documentService.uploadDocuments(files);
    }

    @PostMapping(value = "/sets/public/{documentSetPublicId}/append", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DocumentSetVO> appendDocuments(@PathVariable String documentSetPublicId,
                                                 @RequestPart("files") List<MultipartFile> files) {
        log.info("append documents to set, documentSetPublicId={}, fileCount={}",
                documentSetPublicId, files == null ? 0 : files.size());
        return documentService.appendDocumentsByPublicId(documentSetPublicId, files);
    }

    @GetMapping("/sets")
    public Result<List<DocumentSetListItemVO>> listDocumentSets() {
        log.info("list document sets");
        return documentService.listDocumentSets();
    }

    @DeleteMapping("/sets/public/{documentSetPublicId}")
    public Result<Boolean> deleteDocumentSetByPublicId(@PathVariable String documentSetPublicId) {
        log.info("delete document set by public id, documentSetPublicId={}", documentSetPublicId);
        return documentService.deleteDocumentSetByPublicId(documentSetPublicId);
    }

    @DeleteMapping("/sets/public/{documentSetPublicId}/files")
    public Result<Boolean> deleteDocumentByPublicId(@PathVariable String documentSetPublicId,
                                                    @RequestBody(required = false) DocumentDeleteRequest request) {
        String filePublicId = request == null ? null : request.getFilePublicId();
        log.info("delete document from set, documentSetPublicId={}, filePublicId={}", documentSetPublicId, filePublicId);
        return documentService.deleteDocumentByPublicId(documentSetPublicId, filePublicId);
    }

    @GetMapping("/sets/public/{documentSetPublicId}")
    public Result<DocumentSetVO> getDocumentSetByPublicId(@PathVariable String documentSetPublicId) {
        log.info("get document set by public id, documentSetPublicId={}", documentSetPublicId);
        return documentService.getDocumentSetByPublicId(documentSetPublicId);
    }
}
