package com.fusion.docfusion.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * Redis 分布式限流（固定时间窗：从首次请求开始到过期时间）。
 * 通过 Lua 保证 INCR + 设置过期 的原子性。
 *
 * 适用于比赛/演示场景；真实生产建议用更完善的令牌桶/滑动窗口或 Redis Lua + ZSet 方案。
 */
@Component
@RequiredArgsConstructor
public class RedisRateLimiter {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String LUA_SCRIPT =
            // KEYS[1] = key
            // ARGV[1] = maxRequests
            // ARGV[2] = windowMillis
            // 注意：不要在 Java 字符串里写 \\n 等转义换行，避免 Redis 脚本编译失败
            "local current = redis.call('incr', KEYS[1]); " +
            "if current == 1 then redis.call('pexpire', KEYS[1], ARGV[2]); end; " +
            "if current > tonumber(ARGV[1]) then return 0; end; " +
            "return 1;";

    public boolean tryAcquire(String key, int maxRequests, long windowMillis) {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);
        Long result = stringRedisTemplate.execute(
                redisScript,
                Collections.singletonList(key),
                String.valueOf(maxRequests),
                String.valueOf(windowMillis)
        );
        return result != null && result == 1L;
    }
}

