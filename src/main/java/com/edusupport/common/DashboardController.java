package com.edusupport.common;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.edusupport.security.UserPrincipal;
import com.edusupport.ticket.TicketPriority;
import com.edusupport.ticket.TicketStatus;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(
            DashboardService dashboardService) {

        this.dashboardService = dashboardService;
    }

    /**
     * Admin-only summary endpoint.
     */
    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> summary() {

        return Map.of(
            "openTickets", 0,
            "slaAtRisk", 0,
            "awaitingStudents", 0
        );
    }

    /**
     * Student dashboard.
     */
    @GetMapping("/student")
    @PreAuthorize("hasRole('STUDENT')")
    public DashboardResponse student(
            @AuthenticationPrincipal UserPrincipal principal) {

        return dashboardService.student(principal);
    }

    /**
     * Staff dashboard.
     */
    @GetMapping("/staff")
    @PreAuthorize("hasRole('STAFF')")
    public DashboardResponse staff(
            @AuthenticationPrincipal UserPrincipal principal) {

        return dashboardService.staff(principal);
    }

    /**
     * Admin dashboard.
     */
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public DashboardResponse admin(
            @AuthenticationPrincipal UserPrincipal principal,

            @RequestParam(required = false)
            TicketStatus status,

            @RequestParam(required = false)
            TicketPriority priority,

            @RequestParam(required = false)
            String category,

            @RequestParam(required = false)
            Long assignedStaffId,

            @RequestParam(required = false)
            Boolean overdue,

            @RequestParam(required = false)
            Boolean escalated,

            @RequestParam(required = false)
            Boolean pendingStudent) {

        return dashboardService.admin(
            principal,
            status,
            priority,
            category,
            assignedStaffId,
            overdue,
            escalated,
            pendingStudent
        );
    }
}