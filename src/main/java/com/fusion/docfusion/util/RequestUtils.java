package com.fusion.docfusion.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 请求相关工具（用于匿名限流、日志等）。
 */
public final class RequestUtils {

    private RequestUtils() {
    }

    public static String clientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return "unknown";
        }
        HttpServletRequest req = attrs.getRequest();
        if (req == null) {
            return "unknown";
        }
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // XFF 可能是 "client, proxy1, proxy2"
            int idx = xff.indexOf(',');
            return (idx > 0 ? xff.substring(0, idx) : xff).trim();
        }
        String realIp = req.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String remote = req.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote.trim();
    }
}

