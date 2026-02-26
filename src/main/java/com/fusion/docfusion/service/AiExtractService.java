package com.fusion.docfusion.service;

import java.io.File;
import java.util.Map;

/**
 * 调用 AI 抽取服务的接口。
 * 后续由真实 HTTP 调用实现，现在可以先用假数据实现方便联调。
 */
public interface AiExtractService {

    /**
     * 对单个文档做字段抽取。
     *
     * @param file        要分析的文件
     * @param instruction 抽取指令（例如“请提取字段：student_name, company, amount”）
     * @return 字段编码 -> 抽取到的值
     */
    Map<String, String> analyze(File file, String instruction);
}

