package com.edusupport.ticket;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;

@Service
public class SlaService {

    private final Clock clock;

    public SlaService(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return Instant.now(clock);
    }

    public Instant dueAt(Instant createdAt, TicketPriority priority) {
        return SlaPolicy.dueAt(createdAt, priority);
    }

    public SlaAssessment assess(Ticket ticket) {
        Instant now = now();
        Instant createdAt = ticket.getCreatedAt();
        Instant dueAt = ticket.getSlaDueAt();
        long ageSeconds = Math.max(0, Duration.between(createdAt, now).getSeconds());
        boolean active = SlaPolicy.isActive(ticket.getStatus());
        boolean overdue = active && !now.isBefore(dueAt);
        long targetSeconds = Math.max(1, Duration.between(createdAt, dueAt).getSeconds());
        long remaining = Duration.between(now, dueAt).getSeconds();
        boolean approaching = active && !overdue && remaining <= Math.ceil(targetSeconds * 0.20);
        return new SlaAssessment(createdAt, dueAt, ageSeconds, overdue ? null : Math.max(0, remaining),
                approaching, overdue);
    }
}
