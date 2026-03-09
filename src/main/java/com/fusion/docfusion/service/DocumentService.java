package com.fusion.docfusion.service;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.dto.DocumentSetListItemVO;
import com.fusion.docfusion.dto.DocumentSetVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档集与文档：上传一批文档（比赛要求 docx/md/xlsx/txt/pdf），落盘并入库
 */
public interface DocumentService {

    /**
     * 一次性上传多个文档，创建一个文档集
     * @param files 多个文件（支持 docx, md, xlsx, txt, pdf）
     * @return 文档集 ID 及文档列表
     */
    Result<DocumentSetVO> uploadDocuments(List<MultipartFile> files);

    /**
     * 查询文档集列表（用于创建任务时选择文档集）
     */
    Result<List<DocumentSetListItemVO>> listDocumentSets();

    /**
     * 删除文档集（级联删除其中的文档与相关任务）
     */
    Result<Boolean> deleteDocumentSet(Long documentSetId);

    /**
     * 查询文档集详情（含文档列表）
     */
    Result<DocumentSetVO> getDocumentSet(Long documentSetId);
}
