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
    FAILED,
    /**
     * 处理超时。可由人工或系统重新投递执行。
     */
    TIMEOUT,
    /**
     * 已取消。用户主动终止本次任务。
     */
    CANCELLED
}

