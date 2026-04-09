package com.fusion.docfusion.service;

import com.fusion.docfusion.common.Result;
import com.fusion.docfusion.dto.FillRequest;
import com.fusion.docfusion.dto.FillTaskListPageVO;
import com.fusion.docfusion.dto.FillTaskVO;
import com.fusion.docfusion.dto.FreeFillRequest;

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
    /**
     * 兼容新增：按 publicId 查询任务（用于防枚举）
     */
    Result<FillTaskVO> getTaskByPublicId(String taskPublicId);

    /**
     * 人工重跑入口：允许对 FAILED/TIMEOUT 任务重新投递。
     */
    Result<FillTaskVO> rerunTask(Long taskId);
    /**
     * 兼容新增：按 publicId 重跑任务
     */
    Result<FillTaskVO> rerunTaskByPublicId(String taskPublicId);

    /**
     * 取消任务：允许对 PENDING/RUNNING 任务取消。
     */
    Result<FillTaskVO> cancelTask(Long taskId);
    /**
     * 兼容新增：按 publicId 取消任务
     */
    Result<FillTaskVO> cancelTaskByPublicId(String taskPublicId);

    /**
     * 自由模式：根据文档集 + 用户需求，生成临时汇总表（不依赖预先配置的模板）。
     * 目前为占位实现：生成一个简单的 Excel，列出文档文件名，后续你可以接入 AI 生成真实表头和数据。
     */
    Result<FillTaskVO> submitFree(FreeFillRequest request);

    /**
     * 查询任务列表（分页，含 total / hasMore）
     */
    Result<FillTaskListPageVO> listTasks(String mode, String status, Integer page, Integer size);
}
