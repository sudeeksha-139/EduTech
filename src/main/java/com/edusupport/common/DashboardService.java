package com.edusupport.common;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edusupport.security.UserPrincipal;
import com.edusupport.ticket.SlaAssessment;
import com.edusupport.ticket.SlaService;
import com.edusupport.ticket.Ticket;
import com.edusupport.ticket.TicketPriority;
import com.edusupport.ticket.TicketService;
import com.edusupport.ticket.TicketStatus;
import com.edusupport.user.UserRole;

@Service
public class DashboardService {

    private static final int RECENT_TICKET_LIMIT = 10;

    private final TicketService ticketService;
    private final SlaService slaService;

    public DashboardService(TicketService ticketService, SlaService slaService) {
        this.ticketService = ticketService;
        this.slaService = slaService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse student(UserPrincipal principal) {
        requireRole(principal, UserRole.STUDENT, "Only students can access the student dashboard");
        return build(ticketService.findMine(principal), principal, false);
    }

    @Transactional(readOnly = true)
    public DashboardResponse staff(UserPrincipal principal) {
        requireRole(principal, UserRole.STAFF, "Only staff can access the staff dashboard");
        return build(ticketService.findAssignedToMe(principal), principal, false);
    }

    @Transactional(readOnly = true)
    public DashboardResponse admin(UserPrincipal principal, TicketStatus status, TicketPriority priority,
                                   String category, Long assignedStaffId, Boolean overdue,
                                   Boolean escalated, Boolean pendingStudent) {
        requireRole(principal, UserRole.ADMIN, "Only administrators can access the admin dashboard");
        List<Ticket> tickets = ticketService.findAll(principal).stream()
                .filter(ticket -> status == null || ticket.getStatus() == status)
                .filter(ticket -> priority == null || ticket.getPriority() == priority)
                .filter(ticket -> category == null || ticket.getCategory() != null
                        && ticket.getCategory().getName().equalsIgnoreCase(category))
                .filter(ticket -> assignedStaffId == null || ticket.getAssignedStaff() != null
                        && assignedStaffId.equals(ticket.getAssignedStaff().getId()))
                .filter(ticket -> overdue == null || slaService.assess(ticket).overdue() == overdue)
                .filter(ticket -> escalated == null || ticket.isEscalated() == escalated)
                .filter(ticket -> pendingStudent == null
                        || (ticket.getStatus() == TicketStatus.PENDING_STUDENT) == pendingStudent)
                .toList();
        return build(tickets, principal, true);
    }

    private DashboardResponse build(List<Ticket> tickets, UserPrincipal principal, boolean organizationWide) {
        List<TicketAssessment> assessed = tickets.stream()
                .map(ticket -> new TicketAssessment(ticket, slaService.assess(ticket)))
                .toList();
        long active = assessed.stream().filter(item -> isActive(item.ticket().getStatus())).count();
        long assigned = assessed.stream().filter(item -> item.ticket().getAssignedStaff() != null).count();
        long newTickets = countStatus(assessed, TicketStatus.NEW);
        long assignedTickets = countStatus(assessed, TicketStatus.ASSIGNED);
        long inProgress = countStatus(assessed, TicketStatus.IN_PROGRESS);
        long pending = countStatus(assessed, TicketStatus.PENDING_STUDENT);
        long resolved = countStatus(assessed, TicketStatus.RESOLVED);
        long closed = countStatus(assessed, TicketStatus.CLOSED);
        long overdue = assessed.stream().filter(item -> item.assessment().overdue()).count();
        long approaching = assessed.stream().filter(item -> item.assessment().approachingSla()).count();
        long escalated = assessed.stream().filter(item -> item.ticket().isEscalated()).count();

        List<DashboardResponse.DashboardTicketResponse> recent = recent(assessed);
        List<DashboardResponse.DashboardTicketResponse> pendingTickets = assessed.stream()
                .filter(item -> item.ticket().getStatus() == TicketStatus.PENDING_STUDENT)
                .sorted(byCreatedAt())
                .limit(RECENT_TICKET_LIMIT)
                .map(item -> DashboardResponse.DashboardTicketResponse.from(item.ticket(), item.assessment()))
                .toList();

        List<DashboardResponse.StaffWorkloadResponse> workload = organizationWide
                ? assessed.stream()
                    .filter(item -> item.ticket().getAssignedStaff() != null)
                    .collect(Collectors.groupingBy(item -> item.ticket().getAssignedStaff().getId(),
                            LinkedHashMap::new, Collectors.counting()))
                    .entrySet().stream()
                    .map(entry -> {
                        Ticket matching = assessed.stream()
                                .map(TicketAssessment::ticket)
                                .filter(ticket -> ticket.getAssignedStaff() != null
                                        && entry.getKey().equals(ticket.getAssignedStaff().getId()))
                                .findFirst().orElse(null);
                        return new DashboardResponse.StaffWorkloadResponse(entry.getKey(),
                                matching == null ? null : matching.getAssignedStaff().getName(), entry.getValue());
                    }).toList()
                : principal.getRole() == UserRole.STAFF
                    ? List.of(new DashboardResponse.StaffWorkloadResponse(principal.getId(), principal.getName(), assigned))
                    : List.of();

        return new DashboardResponse(
                assessed.size(), active, assigned, newTickets, assignedTickets, inProgress, pending,
                resolved, closed, overdue, approaching, escalated, pending,
                countBy(assessed, item -> item.ticket().getPriority().name()),
                countBy(assessed, item -> item.ticket().getCategory() == null
                        ? "UNCATEGORIZED" : item.ticket().getCategory().getName()),
                countBy(assessed, item -> item.ticket().getStatus().name()),
                workload, recent, pendingTickets);
    }

    private List<DashboardResponse.DashboardTicketResponse> recent(List<TicketAssessment> assessed) {
        return assessed.stream().sorted(byCreatedAt()).limit(RECENT_TICKET_LIMIT)
                .map(item -> DashboardResponse.DashboardTicketResponse.from(item.ticket(), item.assessment()))
                .toList();
    }

    private Comparator<TicketAssessment> byCreatedAt() {
        return Comparator.comparing(item -> item.ticket().getCreatedAt(),
                Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private long countStatus(List<TicketAssessment> assessed, TicketStatus status) {
        return assessed.stream().filter(item -> item.ticket().getStatus() == status).count();
    }

    private boolean isActive(TicketStatus status) {
        return status != TicketStatus.RESOLVED
                && status != TicketStatus.CLOSED
                && status != TicketStatus.CANCELLED;
    }

    private <T> Map<String, Long> countBy(List<TicketAssessment> assessed,
                                          Function<TicketAssessment, String> keyFunction) {
        return assessed.stream().collect(Collectors.groupingBy(keyFunction, LinkedHashMap::new, Collectors.counting()));
    }

    private void requireRole(UserPrincipal principal, UserRole role, String message) {
        if (principal.getRole() != role) {
            throw new AccessDeniedException(message);
        }
    }

    private record TicketAssessment(Ticket ticket, SlaAssessment assessment) {
    }
}
