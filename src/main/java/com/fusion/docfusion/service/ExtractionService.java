package com.fusion.docfusion.service;

/**
 * 负责对文档执行字段抽取并将结果写入数据库的服务。
 * 目前只定义接口，具体实现由 ExtractionServiceImpl 完成。
 */
public interface ExtractionService {

    /**
     * 对指定文档执行抽取，并将结果写入 extracted_value 表。
     *
     * @param documentId 文档ID
     * @param instruction 抽取指令，如果为 null 或空，则使用默认指令
     */
    void extractForDocument(Long documentId, String instruction);
}

