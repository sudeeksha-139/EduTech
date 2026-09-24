package com.edusupport.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.edusupport.security.UserPrincipal;
import com.edusupport.user.User;
import com.edusupport.user.UserRepository;
import com.edusupport.user.UserRole;

@ExtendWith(MockitoExtension.class)
class TicketSlaAndEscalationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-24T12:00:00Z");

    @Mock TicketRepository ticketRepository;
    @Mock TicketHistoryRepository historyRepository;
    @Mock TicketCommentRepository commentRepository;
    @Mock UserRepository userRepository;

    private User student;
    private User staff;
    private User admin;
    private Ticket overdue;
    private Ticket approaching;
    private Ticket pending;
    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        student = user(10L, UserRole.STUDENT);
        staff = user(20L, UserRole.STAFF);
        admin = user(30L, UserRole.ADMIN);
        overdue = ticket(TicketStatus.IN_PROGRESS, student, staff, NOW.minus(Duration.ofHours(25)), NOW.minusSeconds(1));
        approaching = ticket(TicketStatus.IN_PROGRESS, student, staff, NOW.minus(Duration.ofHours(20)), NOW.plus(Duration.ofHours(4)));
        pending = ticket(TicketStatus.PENDING_STUDENT, student, staff, NOW.minus(Duration.ofHours(2)), NOW.plus(Duration.ofHours(22)));
        ticketService = new TicketService(ticketRepository, userRepository, historyRepository, commentRepository,
                new SlaService(Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    @Test
    void overdueFilteringUsesBackendAssessment() {
        when(ticketRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(overdue, approaching));

        assertThat(ticketService.findOverdue(principal(admin))).containsExactly(overdue);
    }

    @Test
    void approachingFilteringUsesBackendAssessment() {
        when(ticketRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(overdue, approaching));

        assertThat(ticketService.findApproachingSla(principal(admin))).containsExactly(approaching);
    }

    @Test
    void pendingStudentTicketsAreVisibleToStudentAndAssignedStaff() {
        when(ticketRepository.findByStudentIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(pending));
        when(ticketRepository.findByAssignedStaffIdOrderBySlaDueAtAsc(20L)).thenReturn(List.of(pending));

        assertThat(ticketService.findPendingStudent(principal(student))).containsExactly(pending);
        assertThat(ticketService.findPendingStudent(principal(staff))).containsExactly(pending);
    }

    @Test
    void staffSlaFilteringIsLimitedToAssignedTickets() {
        when(ticketRepository.findByAssignedStaffIdOrderBySlaDueAtAsc(20L)).thenReturn(List.of(overdue));

        assertThat(ticketService.findOverdue(principal(staff))).containsExactly(overdue);
    }

    @Test
    void studentSlaFilteringIsLimitedToOwnedTickets() {
        when(ticketRepository.findByStudentIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(overdue));

        assertThat(ticketService.findOverdue(principal(student))).containsExactly(overdue);
    }

    @Test
    void adminCanEscalateAndHistoryRecordsTheReason() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(overdue));
        when(userRepository.findById(30L)).thenReturn(Optional.of(admin));
        when(ticketRepository.save(overdue)).thenReturn(overdue);

        ticketService.escalate(1L, "SLA breach requires management review", principal(admin));

        verify(overdue).escalate("SLA breach requires management review", admin);
        ArgumentCaptor<TicketHistory> history = ArgumentCaptor.forClass(TicketHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getAction()).isEqualTo(TicketHistoryAction.ESCALATED);
        assertThat(history.getValue().getBeforeValue()).isEqualTo("false");
        assertThat(history.getValue().getAfterValue()).isEqualTo("SLA breach requires management review");
    }

    @Test
    void nonAdminCannotEscalate() {
        assertThatThrownBy(() -> ticketService.escalate(1L, "No", principal(staff)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminCanDeEscalateAndHistoryRecordsTheReason() {
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(overdue));
        when(userRepository.findById(30L)).thenReturn(Optional.of(admin));
        when(ticketRepository.save(overdue)).thenReturn(overdue);

        ticketService.deEscalate(1L, "Issue is now owned by the service desk", principal(admin));

        verify(overdue).deEscalate("Issue is now owned by the service desk", admin);
        ArgumentCaptor<TicketHistory> history = ArgumentCaptor.forClass(TicketHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getAction()).isEqualTo(TicketHistoryAction.DE_ESCALATED);
        assertThat(history.getValue().getBeforeValue()).isEqualTo("true");
    }

    @Test
    void nonAdminCannotDeEscalate() {
        assertThatThrownBy(() -> ticketService.deEscalate(1L, "No", principal(student)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void escalatedFilteringIsRoleScoped() {
        when(overdue.isEscalated()).thenReturn(true);
        when(ticketRepository.findByAssignedStaffIdOrderBySlaDueAtAsc(20L)).thenReturn(List.of(overdue));
        when(ticketRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(overdue));

        assertThat(ticketService.findEscalated(principal(staff))).containsExactly(overdue);
        assertThat(ticketService.findEscalated(principal(admin))).containsExactly(overdue);
    }

    private Ticket ticket(TicketStatus status, User owner, User assignedStaff, Instant createdAt, Instant dueAt) {
        Ticket ticket = mock(Ticket.class);
        lenient().when(ticket.getStatus()).thenReturn(status);
        lenient().when(ticket.getStudent()).thenReturn(owner);
        lenient().when(ticket.getAssignedStaff()).thenReturn(assignedStaff);
        lenient().when(ticket.getCreatedAt()).thenReturn(createdAt);
        lenient().when(ticket.getSlaDueAt()).thenReturn(dueAt);
        return ticket;
    }

    private User user(Long id, UserRole role) {
        User user = mock(User.class);
        lenient().when(user.getId()).thenReturn(id);
        lenient().when(user.getRole()).thenReturn(role);
        lenient().when(user.getName()).thenReturn(role.name());
        lenient().when(user.getEmail()).thenReturn(role.name().toLowerCase() + "@example.edu");
        lenient().when(user.getPasswordHash()).thenReturn("hash");
        lenient().when(user.isActive()).thenReturn(true);
        return user;
    }

    private UserPrincipal principal(User user) {
        return UserPrincipal.from(user);
    }
}
