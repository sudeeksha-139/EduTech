package com.edusupport.ticket;

import java.time.Instant;

public record SlaAssessment(
        Instant createdAt,
        Instant dueAt,
        long ageSeconds,
        Long remainingSeconds,
        boolean approachingSla,
        boolean overdue) {
}
