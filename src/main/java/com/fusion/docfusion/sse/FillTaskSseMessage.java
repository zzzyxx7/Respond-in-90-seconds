package com.fusion.docfusion.sse;

/**
 * SSE 消息统一封装：作为 event.data 的 JSON。
 *
 * @param id    事件序号（可用于断线重连补发）
 * @param event 事件名（对应 SSE 的 event name）
 * @param data  事件 payload（任意对象，会被序列化为 JSON）
 * @param ts    ISO 时间戳
 */
public record FillTaskSseMessage(
        long id,
        String event,
        Object data,
        String ts
) {
}

