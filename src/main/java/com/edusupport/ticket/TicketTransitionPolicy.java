package com.edusupport.ticket;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

final class TicketTransitionPolicy {

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = Map.of(
            TicketStatus.NEW, EnumSet.of(TicketStatus.TRIAGED, TicketStatus.ASSIGNED, TicketStatus.CANCELLED),
            TicketStatus.TRIAGED, EnumSet.of(TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED),
            TicketStatus.ASSIGNED, EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.TRIAGED, TicketStatus.CANCELLED),
            TicketStatus.IN_PROGRESS, EnumSet.of(TicketStatus.PENDING_STUDENT, TicketStatus.PENDING_STAFF, TicketStatus.RESOLVED),
            TicketStatus.PENDING_STUDENT, EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.CANCELLED),
            TicketStatus.PENDING_STAFF, EnumSet.of(TicketStatus.IN_PROGRESS),
            TicketStatus.RESOLVED, EnumSet.of(TicketStatus.CLOSED, TicketStatus.REOPENED),
            TicketStatus.CLOSED, EnumSet.of(TicketStatus.REOPENED),
            TicketStatus.REOPENED, EnumSet.of(TicketStatus.IN_PROGRESS, TicketStatus.PENDING_STUDENT, TicketStatus.PENDING_STAFF),
            TicketStatus.CANCELLED, EnumSet.noneOf(TicketStatus.class));

    private TicketTransitionPolicy() {
    }

    static boolean isAllowed(TicketStatus current, TicketStatus next) {
        return current != next && ALLOWED.getOrDefault(current, Set.of()).contains(next);
    }
}
