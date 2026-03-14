package com.fusion.docfusion.enums;

/**
 * 填表任务状态。
 */
public enum TaskStatus {
    /**
     * 已创建，等待异步处理。
     */
    PENDING,
    /**
     * 异步任务处理中。
     */
    RUNNING,
    /**
     * 处理成功，结果文件可下载。
     */
    SUCCESS,
    /**
     * 处理失败，错误信息记录在 errorMessage 中。
     */
    FAILED
}

