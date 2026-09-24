package com.edusupport.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SlaServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-24T12:00:00Z");
    private SlaService slaService;

    @BeforeEach
    void setUp() {
        slaService = new SlaService(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void usesTheConfiguredPrioritySlaWindows() {
        assertThat(SlaPolicy.targetFor(TicketPriority.CRITICAL)).isEqualTo(Duration.ofHours(8));
        assertThat(SlaPolicy.targetFor(TicketPriority.HIGH)).isEqualTo(Duration.ofHours(24));
        assertThat(SlaPolicy.targetFor(TicketPriority.MEDIUM)).isEqualTo(Duration.ofHours(48));
        assertThat(SlaPolicy.targetFor(TicketPriority.LOW)).isEqualTo(Duration.ofHours(72));
    }

    @Test
    void calculatesAgeAndRemainingTime() {
        Ticket ticket = ticket(TicketStatus.IN_PROGRESS, NOW.minusSeconds(3600), NOW.plusSeconds(3600), TicketPriority.MEDIUM);

        SlaAssessment assessment = slaService.assess(ticket);

        assertThat(assessment.ageSeconds()).isEqualTo(3600);
        assertThat(assessment.remainingSeconds()).isEqualTo(3600L);
        assertThat(assessment.overdue()).isFalse();
    }

    @Test
    void marksTheFinalTwentyPercentAsApproaching() {
        Instant created = NOW.minus(Duration.ofHours(20));
        Ticket ticket = ticket(TicketStatus.IN_PROGRESS, created, created.plus(Duration.ofHours(24)), TicketPriority.HIGH);

        assertThat(slaService.assess(ticket).approachingSla()).isTrue();
        assertThat(slaService.assess(ticket).overdue()).isFalse();
    }

    @Test
    void marksExactDeadlineAndPastDeadlineAsOverdue() {
        Ticket exact = ticket(TicketStatus.IN_PROGRESS, NOW.minus(Duration.ofHours(24)), NOW, TicketPriority.HIGH);
        Ticket past = ticket(TicketStatus.IN_PROGRESS, NOW.minus(Duration.ofHours(25)), NOW.minusSeconds(1), TicketPriority.HIGH);

        assertThat(slaService.assess(exact).overdue()).isTrue();
        assertThat(slaService.assess(past).overdue()).isTrue();
        assertThat(slaService.assess(exact).remainingSeconds()).isNull();
    }

    @Test
    void resolvedAndClosedTicketsAreNotActivelyOverdue() {
        Ticket resolved = ticket(TicketStatus.RESOLVED, NOW.minus(Duration.ofHours(48)), NOW.minus(Duration.ofHours(1)), TicketPriority.MEDIUM);
        Ticket closed = ticket(TicketStatus.CLOSED, NOW.minus(Duration.ofHours(48)), NOW.minus(Duration.ofHours(1)), TicketPriority.MEDIUM);

        assertThat(slaService.assess(resolved).overdue()).isFalse();
        assertThat(slaService.assess(closed).overdue()).isFalse();
        assertThat(slaService.assess(resolved).approachingSla()).isFalse();
    }

    @Test
    void reopenedTicketBecomesActiveAgain() {
        Ticket reopened = ticket(TicketStatus.REOPENED, NOW.minus(Duration.ofHours(24)), NOW.minusSeconds(1), TicketPriority.HIGH);

        assertThat(slaService.assess(reopened).overdue()).isTrue();
    }

    @Test
    void futureTicketHasPositiveRemainingTime() {
        Ticket ticket = ticket(TicketStatus.NEW, NOW.minusSeconds(60), NOW.plus(Duration.ofHours(8)), TicketPriority.CRITICAL);

        SlaAssessment assessment = slaService.assess(ticket);

        assertThat(assessment.remainingSeconds()).isEqualTo(Duration.ofHours(8).getSeconds());
        assertThat(assessment.approachingSla()).isFalse();
    }

    private Ticket ticket(TicketStatus status, Instant createdAt, Instant dueAt, TicketPriority priority) {
        Ticket ticket = mock(Ticket.class);
        when(ticket.getStatus()).thenReturn(status);
        when(ticket.getCreatedAt()).thenReturn(createdAt);
        when(ticket.getSlaDueAt()).thenReturn(dueAt);
        when(ticket.getPriority()).thenReturn(priority);
        return ticket;
    }
}
