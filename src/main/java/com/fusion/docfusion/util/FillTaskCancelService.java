package com.fusion.docfusion.util;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 任务取消标记（Redis）：
 * - cancel 接口写入标记
 * - worker/AI 轮询过程中周期检查，尽快停止
 *
 * 单实例/多实例均适用（只要共用 Redis）。
 */
@Component
@RequiredArgsConstructor
public class FillTaskCancelService {

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${fill.task.cancel-flag-ttl-seconds:3600}")
    private long cancelFlagTtlSeconds;

    private static String key(String taskPublicId) {
        return "fill:task:cancel:" + taskPublicId;
    }

    public void requestCancel(String taskPublicId) {
        if (taskPublicId == null || taskPublicId.isBlank()) {
            return;
        }
        stringRedisTemplate.opsForValue().set(key(taskPublicId), "1", Duration.ofSeconds(cancelFlagTtlSeconds));
    }

    public boolean isCancelRequested(String taskPublicId) {
        if (taskPublicId == null || taskPublicId.isBlank()) {
            return false;
        }
        String v = stringRedisTemplate.opsForValue().get(key(taskPublicId));
        return v != null && !v.isBlank();
    }

    public void clearCancel(String taskPublicId) {
        if (taskPublicId == null || taskPublicId.isBlank()) {
            return;
        }
        stringRedisTemplate.delete(key(taskPublicId));
    }
}

