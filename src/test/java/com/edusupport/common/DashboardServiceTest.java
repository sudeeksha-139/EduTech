package com.edusupport.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import com.edusupport.category.Category;
import com.edusupport.security.UserPrincipal;
import com.edusupport.ticket.SlaService;
import com.edusupport.ticket.Ticket;
import com.edusupport.ticket.TicketPriority;
import com.edusupport.ticket.TicketService;
import com.edusupport.ticket.TicketStatus;
import com.edusupport.user.User;
import com.edusupport.user.UserRole;

class DashboardServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-24T12:00:00Z");

    private TicketService ticketService;
    private DashboardService dashboardService;
    private User student;
    private User staff;
    private User admin;

    @BeforeEach
    void setUp() {
        ticketService = mock(TicketService.class);
        dashboardService = new DashboardService(ticketService,
                new SlaService(Clock.fixed(NOW, ZoneOffset.UTC)));
        student = user(10L, UserRole.STUDENT);
        staff = user(20L, UserRole.STAFF);
        admin = user(30L, UserRole.ADMIN);
    }

    @Test
    void studentDashboardContainsOnlyOwnTicketMetrics() {
        Ticket pending = ticket(TicketStatus.PENDING_STUDENT, TicketPriority.MEDIUM, student, staff,
                NOW.minusSeconds(100), NOW.plusSeconds(100));
        Ticket resolved = ticket(TicketStatus.RESOLVED, TicketPriority.LOW, student, staff,
                NOW.minusSeconds(200), NOW.minusSeconds(100));
        when(ticketService.findMine(any(UserPrincipal.class))).thenReturn(List.of(pending, resolved));

        DashboardResponse response = dashboardService.student(principal(student));

        assertThat(response.totalTickets()).isEqualTo(2);
        assertThat(response.activeTickets()).isEqualTo(1);
        assertThat(response.pendingStudentTickets()).isEqualTo(1);
        assertThat(response.resolvedTickets()).isEqualTo(1);
        assertThat(response.pendingActionCount()).isEqualTo(1);
        assertThat(response.ticketsRequiringStudentAction()).hasSize(1);
    }

    @Test
    void zeroTicketStudentGetsEmptyCollectionsAndZeroCounts() {
        when(ticketService.findMine(any(UserPrincipal.class))).thenReturn(List.of());

        DashboardResponse response = dashboardService.student(principal(student));

        assertThat(response.totalTickets()).isZero();
        assertThat(response.recentTickets()).isEmpty();
        assertThat(response.ticketsByStatus()).isEmpty();
        assertThat(response.ticketsRequiringStudentAction()).isEmpty();
    }

        @Test
        void emptyAdminDashboardReturnsZeroCountsAndEmptyCollections() {
                when(ticketService.findAll(any(UserPrincipal.class))).thenReturn(List.of());

                DashboardResponse response = dashboardService.admin(principal(admin), null, null, null, null,
                                null, null, null);

                assertThat(response.totalTickets()).isZero();
                assertThat(response.overdueTickets()).isZero();
                assertThat(response.staffWorkload()).isEmpty();
                assertThat(response.recentTickets()).isEmpty();
        }

    @Test
    void staffDashboardUsesOnlyAssignedTicketsAndCountsSlaStates() {
        Ticket overdue = ticket(TicketStatus.IN_PROGRESS, TicketPriority.HIGH, student, staff,
                NOW.minusSeconds(1000), NOW.minusSeconds(1));
        Ticket approaching = ticket(TicketStatus.IN_PROGRESS, TicketPriority.HIGH, student, staff,
                NOW.minusSeconds(800), NOW.plusSeconds(100));
        when(ticketService.findAssignedToMe(any(UserPrincipal.class))).thenReturn(List.of(overdue, approaching));

        DashboardResponse response = dashboardService.staff(principal(staff));

        assertThat(response.assignedCount()).isEqualTo(2);
        assertThat(response.overdueTickets()).isEqualTo(1);
        assertThat(response.approachingSlaTickets()).isEqualTo(1);
        assertThat(response.staffWorkload()).singleElement().satisfies(workload ->
                assertThat(workload.assignedTickets()).isEqualTo(2));
    }

    @Test
    void adminDashboardAggregatesOrganizationMetricsAndSupportsFilters() {
        Ticket newTicket = ticket(TicketStatus.NEW, TicketPriority.HIGH, student, staff,
                NOW.minusSeconds(100), NOW.plusSeconds(100));
        Ticket escalated = ticket(TicketStatus.IN_PROGRESS, TicketPriority.CRITICAL, student, staff,
                NOW.minusSeconds(1000), NOW.minusSeconds(1));
        when(escalated.isEscalated()).thenReturn(true);
        when(ticketService.findAll(any(UserPrincipal.class))).thenReturn(List.of(newTicket, escalated));

        DashboardResponse response = dashboardService.admin(principal(admin), null, TicketPriority.HIGH,
                "Fees", null, null, null, null);

        assertThat(response.totalTickets()).isEqualTo(1);
        assertThat(response.newTickets()).isEqualTo(1);
        assertThat(response.ticketsByPriority()).containsEntry("HIGH", 1L);
        assertThat(response.ticketsByCategory()).containsEntry("Fees", 1L);
        assertThat(response.staffWorkload()).hasSize(1);
    }

    @Test
    void adminDashboardCountsOverdueEscalatedAndPending() {
        Ticket overdue = ticket(TicketStatus.PENDING_STUDENT, TicketPriority.HIGH, student, staff,
                NOW.minusSeconds(1000), NOW.minusSeconds(1));
        when(overdue.isEscalated()).thenReturn(true);
        when(ticketService.findAll(any(UserPrincipal.class))).thenReturn(List.of(overdue));

        DashboardResponse response = dashboardService.admin(principal(admin), null, null, null, null,
                null, null, null);

        assertThat(response.overdueTickets()).isEqualTo(1);
        assertThat(response.escalatedTickets()).isEqualTo(1);
        assertThat(response.pendingActionCount()).isEqualTo(1);
    }

    @Test
    void dashboardAuthorizationIsEnforcedInService() {
        assertThatThrownBy(() -> dashboardService.student(principal(staff)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> dashboardService.staff(principal(student)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> dashboardService.admin(principal(staff), null, null, null, null, null, null, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    private Ticket ticket(TicketStatus status, TicketPriority priority, User owner, User assigned,
                          Instant createdAt, Instant dueAt) {
        Ticket ticket = mock(Ticket.class);
        Category category = new Category("Fees");
        lenient().when(ticket.getId()).thenReturn((long) status.ordinal() + 1);
        lenient().when(ticket.getTicketNumber()).thenReturn("ES-" + status.name());
        lenient().when(ticket.getSubject()).thenReturn(status.name() + " ticket");
        lenient().when(ticket.getStatus()).thenReturn(status);
        lenient().when(ticket.getPriority()).thenReturn(priority);
        lenient().when(ticket.getCategory()).thenReturn(category);
        lenient().when(ticket.getStudent()).thenReturn(owner);
        lenient().when(ticket.getAssignedStaff()).thenReturn(assigned);
        lenient().when(ticket.getCreatedAt()).thenReturn(createdAt);
        lenient().when(ticket.getSlaDueAt()).thenReturn(dueAt);
        lenient().when(ticket.isEscalated()).thenReturn(false);
        return ticket;
    }

    private User user(Long id, UserRole role) {
        User user = mock(User.class);
        lenient().when(user.getId()).thenReturn(id);
        lenient().when(user.getName()).thenReturn(role.name());
        lenient().when(user.getEmail()).thenReturn(role.name().toLowerCase() + "@example.edu");
        lenient().when(user.getRole()).thenReturn(role);
        lenient().when(user.getPasswordHash()).thenReturn("hash");
        lenient().when(user.isActive()).thenReturn(true);
        return user;
    }

    private UserPrincipal principal(User user) {
        return UserPrincipal.from(user);
    }
}
