package com.fusion.docfusion.sse;

import java.time.LocalDateTime;

public record FillTaskStepEvent(
        String stepCode,
        String stepName,
        String status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Long durationMs,
        String message,
        String errorMessage
) {
}

