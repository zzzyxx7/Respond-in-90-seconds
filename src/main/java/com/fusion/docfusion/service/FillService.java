package com.fusion.docfusion.service;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.dto.FillRequest;
import com.fusion.docfusion.dto.FillTaskVO;

/**
 * 填表任务：根据文档集 + 模板，触发一次自动填表，返回任务 ID；支持异步查询结果与下载
 */
public interface FillService {

    /**
     * 提交填表任务（同步：先创建任务，再执行填表逻辑，完成后返回；比赛要求单次 ≤90 秒）
     */
    Result<FillTaskVO> submitFill(FillRequest request);

    /**
     * 查询任务状态与结果文件路径
     */
    Result<FillTaskVO> getTask(Long taskId);
}
