package com.fusion.docfusion.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 填表任务进度 SSE Broker：
 * - 支持多个订阅者（一个任务 publicId 对应多个前端页面/用户）
 * - 支持断线重连后按 Last-Event-ID 补发
 * - 统一心跳保活与死连接清理
 *
 * 注意：当前实现为单实例内存版；多实例部署可替换为 Redis Stream / MQ 广播。
 */
@Component
@Slf4j
public class FillTaskSseBroker {

    private static final long DEFAULT_EMITTER_TIMEOUT_MS = 30L * 60L * 1000L; // 30 min
    private static final int DEFAULT_BUFFER_SIZE = 200;

    private final ConcurrentHashMap<String, TaskChannel> channels = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String taskPublicId, String lastEventIdHeader) {
        TaskChannel channel = channels.computeIfAbsent(taskPublicId, k -> new TaskChannel(DEFAULT_BUFFER_SIZE));
        long lastEventId = parseLastEventId(lastEventIdHeader);

        SseEmitter emitter = new SseEmitter(DEFAULT_EMITTER_TIMEOUT_MS);
        channel.emitters.add(emitter);

        emitter.onCompletion(() -> channel.emitters.remove(emitter));
        emitter.onTimeout(() -> channel.emitters.remove(emitter));
        emitter.onError((e) -> channel.emitters.remove(emitter));

        // 断线重连补发（可能会失败：连接刚建立但客户端已断开，忽略即可）
        channel.replayFrom(lastEventId, emitter);

        return emitter;
    }

    public void publish(String taskPublicId, String eventName, Object payload) {
        TaskChannel channel = channels.computeIfAbsent(taskPublicId, k -> new TaskChannel(DEFAULT_BUFFER_SIZE));
        long id = channel.seq.incrementAndGet();
        FillTaskSseMessage msg = new FillTaskSseMessage(id, eventName, payload, Instant.now().toString());
        channel.buffer(msg);
        channel.broadcast(msg);
    }

    /** 心跳：避免中间代理/浏览器把长连接静默断开，同时清理失效 emitter。 */
    @Scheduled(fixedDelayString = "${sse.heartbeat.delay-ms:15000}")
    public void heartbeat() {
        for (var entry : channels.entrySet()) {
            String taskPublicId = entry.getKey();
            TaskChannel channel = entry.getValue();
            if (channel == null) continue;
            if (channel.emitters.isEmpty()) {
                // 无订阅者则延迟清理：避免刚断开就丢重连补发的 buffer
                channel.markMaybeIdle();
                if (channel.idleForTooLong()) {
                    channels.remove(taskPublicId, channel);
                }
                continue;
            }
            channel.clearIdleMark();
            // 发送轻量心跳事件
            channel.broadcastRaw(SseEmitter.event().name("HEARTBEAT").data("ok"));
        }
    }

    private static long parseLastEventId(String header) {
        if (header == null || header.isBlank()) return 0L;
        try {
            return Long.parseLong(header.trim());
        } catch (NumberFormatException ignore) {
            return 0L;
        }
    }

    private static final class TaskChannel {
        private final AtomicLong seq = new AtomicLong(0);
        private final Set<SseEmitter> emitters = new CopyOnWriteArraySet<>();
        private final Deque<FillTaskSseMessage> ring;
        private final int ringSize;

        private volatile long idleMarkedAtMs = 0L;

        private TaskChannel(int ringSize) {
            this.ringSize = Math.max(10, ringSize);
            this.ring = new ArrayDeque<>(this.ringSize);
        }

        private void buffer(FillTaskSseMessage msg) {
            synchronized (ring) {
                ring.addLast(msg);
                while (ring.size() > ringSize) {
                    ring.removeFirst();
                }
            }
        }

        private void replayFrom(long lastEventId, SseEmitter emitter) {
            Deque<FillTaskSseMessage> snapshot;
            synchronized (ring) {
                snapshot = new ArrayDeque<>(ring);
            }
            for (FillTaskSseMessage msg : snapshot) {
                if (msg.id() <= lastEventId) continue;
                try {
                    emitter.send(toEvent(msg));
                } catch (IOException e) {
                    emitters.remove(emitter);
                    return;
                }
            }
        }

        private void broadcast(FillTaskSseMessage msg) {
            broadcastRaw(toEvent(msg));
        }

        private void broadcastRaw(SseEmitter.SseEventBuilder event) {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(event);
                } catch (Exception e) {
                    emitters.remove(emitter);
                    try {
                        emitter.complete();
                    } catch (Exception ignore) {
                        // ignore
                    }
                }
            }
        }

        private static SseEmitter.SseEventBuilder toEvent(FillTaskSseMessage msg) {
            return SseEmitter.event()
                    .id(String.valueOf(msg.id()))
                    .name(msg.event())
                    .data(msg, MediaType.APPLICATION_JSON);
        }

        private void markMaybeIdle() {
            if (idleMarkedAtMs == 0L) {
                idleMarkedAtMs = System.currentTimeMillis();
            }
        }

        private void clearIdleMark() {
            idleMarkedAtMs = 0L;
        }

        private boolean idleForTooLong() {
            long marked = idleMarkedAtMs;
            if (marked == 0L) return false;
            // 2 minutes 无订阅者则清理 channel
            return System.currentTimeMillis() - marked > 120_000L;
        }
    }
}

