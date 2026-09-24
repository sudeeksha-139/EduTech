package com.edusupport.ticket;

import java.time.Duration;
import java.time.Instant;

public final class SlaPolicy {

    private SlaPolicy() {
    }

    public static Duration targetFor(TicketPriority priority) {
        return switch (priority) {
            case CRITICAL -> Duration.ofHours(8);
            case HIGH -> Duration.ofHours(24);
            case MEDIUM -> Duration.ofHours(48);
            case LOW -> Duration.ofHours(72);
        };
    }

    public static Instant dueAt(Instant createdAt, TicketPriority priority) {
        return createdAt.plus(targetFor(priority));
    }

    public static boolean isActive(TicketStatus status) {
        return status != TicketStatus.RESOLVED
                && status != TicketStatus.CLOSED
                && status != TicketStatus.CANCELLED;
    }
}
