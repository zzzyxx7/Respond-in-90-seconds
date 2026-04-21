package com.fusion.docfusion.service;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.dto.DocumentSetListItemVO;
import com.fusion.docfusion.dto.DocumentSetVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {

    Result<DocumentSetVO> uploadDocuments(List<MultipartFile> files);

    Result<DocumentSetVO> appendDocumentsByPublicId(String documentSetPublicId, List<MultipartFile> files);

    Result<List<DocumentSetListItemVO>> listDocumentSets();

    Result<Boolean> deleteDocumentSet(Long documentSetId);

    Result<Boolean> deleteDocumentSetByPublicId(String documentSetPublicId);

    Result<Boolean> deleteDocumentByPublicId(String documentSetPublicId, String filePublicId);

    Result<DocumentSetVO> getDocumentSet(Long documentSetId);

    Result<DocumentSetVO> getDocumentSetByPublicId(String documentSetPublicId);
}
