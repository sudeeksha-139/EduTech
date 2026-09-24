package com.edusupport.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TicketTransitionPolicyTest {

    @Test
    void permitsNormalProgression() {
        assertThat(TicketTransitionPolicy.isAllowed(TicketStatus.NEW, TicketStatus.TRIAGED)).isTrue();
        assertThat(TicketTransitionPolicy.isAllowed(TicketStatus.NEW, TicketStatus.ASSIGNED)).isTrue();
        assertThat(TicketTransitionPolicy.isAllowed(TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED)).isTrue();
        assertThat(TicketTransitionPolicy.isAllowed(TicketStatus.RESOLVED, TicketStatus.REOPENED)).isTrue();
    }

    @Test
    void rejectsSkippingRequiredWorkflowStates() {
        assertThat(TicketTransitionPolicy.isAllowed(TicketStatus.NEW, TicketStatus.RESOLVED)).isFalse();
        assertThat(TicketTransitionPolicy.isAllowed(TicketStatus.CLOSED, TicketStatus.IN_PROGRESS)).isFalse();
        assertThat(TicketTransitionPolicy.isAllowed(TicketStatus.PENDING_STUDENT, TicketStatus.RESOLVED)).isFalse();
    }
}
