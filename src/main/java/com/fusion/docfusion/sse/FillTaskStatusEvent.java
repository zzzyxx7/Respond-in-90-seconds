package com.fusion.docfusion.sse;

import java.time.LocalDateTime;

public record FillTaskStatusEvent(
        String status,
        String errorMessage,
        LocalDateTime finishedAt
) {
}

