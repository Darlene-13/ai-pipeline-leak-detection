package io.github.darlene.leakdetectionapplication.alert;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.OffsetDateTime;

public record FaultAlertResponse(
        Long id,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
        OffsetDateTime readingTime,

        String faultClass,
        String faultDescription,
        String severityLevel,
        String severityDescription,
        Double confidence,
        String recommendation,
        Long latencyMs,
        String colorCode,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
        OffsetDateTime createdAt
) {
}