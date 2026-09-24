package com.edusupport.common;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.edusupport.ticket.SlaAssessment;
import com.edusupport.ticket.Ticket;
import com.edusupport.ticket.TicketPriority;
import com.edusupport.ticket.TicketStatus;

public record DashboardResponse(
        long totalTickets,
        long activeTickets,
        long assignedCount,
        long newTickets,
        long assignedTickets,
        long inProgressTickets,
        long pendingStudentTickets,
        long resolvedTickets,
        long closedTickets,
        long overdueTickets,
        long approachingSlaTickets,
        long escalatedTickets,
        long pendingActionCount,
        Map<String, Long> ticketsByPriority,
        Map<String, Long> ticketsByCategory,
        Map<String, Long> ticketsByStatus,
        List<StaffWorkloadResponse> staffWorkload,
        List<DashboardTicketResponse> recentTickets,
        List<DashboardTicketResponse> ticketsRequiringStudentAction) {

    public static DashboardResponse empty() {
        return new DashboardResponse(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                Map.of(), Map.of(), Map.of(), List.of(), List.of(), List.of());
    }

    public record StaffWorkloadResponse(Long staffId, String staffName, long assignedTickets) {
    }

    public record DashboardTicketResponse(Long id, String ticketNumber, String subject,
                                          TicketStatus status, TicketPriority priority,
                                          String category, Long assignedStaffId,
                                          String assignedStaffName, Instant createdAt,
                                          Instant slaDueAt, long ageSeconds,
                                          Long remainingSeconds, boolean approachingSla,
                                          boolean overdue, boolean pendingStudentAction,
                                          boolean escalated) {
        static DashboardTicketResponse from(Ticket ticket, SlaAssessment assessment) {
            return new DashboardTicketResponse(
                    ticket.getId(),
                    ticket.getTicketNumber(),
                    ticket.getSubject(),
                    ticket.getStatus(),
                    ticket.getPriority(),
                    ticket.getCategory() == null ? null : ticket.getCategory().getName(),
                    ticket.getAssignedStaff() == null ? null : ticket.getAssignedStaff().getId(),
                    ticket.getAssignedStaff() == null ? null : ticket.getAssignedStaff().getName(),
                    ticket.getCreatedAt(),
                    assessment.dueAt(),
                    assessment.ageSeconds(),
                    assessment.remainingSeconds(),
                    assessment.approachingSla(),
                    assessment.overdue(),
                    ticket.getStatus() == TicketStatus.PENDING_STUDENT,
                    ticket.isEscalated());
        }
    }
}
