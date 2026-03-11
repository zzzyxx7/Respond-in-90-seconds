package com.fusion.docfusion.service.impl;

import com.fusion.docfusion.entity.Document;
import com.fusion.docfusion.entity.FillTask;
import com.fusion.docfusion.exception.BusinessException;
import com.fusion.docfusion.service.AiFillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 调用 AI 填表服务的占位实现。
 * 后续由你根据 AI 同学提供的接口完成真实 HTTP 调用和结果文件落盘逻辑。
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AiFillServiceImpl implements AiFillService {

    @Override
    public void fillTemplateForTask(FillTask task, List<Document> docs) {
        // 目前仅作为占位，避免调用方空指针或编译错误。
        // 等 AI 同学提供按模板填表的接口后，在这里完成：
        // 1. 组装请求（模板 + 文档或抽取结果）
        // 2. 调用 AI 填表接口
        // 3. 将返回的结果文件保存到 results 目录，并更新 task.resultFilePath
        log.warn("AI 填表服务尚未实现, taskId={}", task == null ? null : task.getId());
        throw new BusinessException("AI 填表服务尚未接入，请稍后重试");
    }
}
