package com.edusupport.ticket;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.edusupport.security.UserPrincipal;
import com.edusupport.user.UserRole;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final SlaService slaService;

    public TicketController(TicketService ticketService, SlaService slaService) {
        this.ticketService = ticketService;
        this.slaService = slaService;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody CreateTicketRequest request,
                                                  @AuthenticationPrincipal UserPrincipal principal,
                                                  UriComponentsBuilder uriBuilder) {
        Long studentId = principal.getRole() == UserRole.STUDENT ? principal.getId() : request.studentId();
        Ticket ticket = ticketService.create(request.subject(), request.description(), request.priority(), studentId, principal);
        return ResponseEntity.created(uriBuilder.path("/api/tickets/{id}").build(ticket.getId()))
                .body(response(ticket));
    }

    @GetMapping("/{ticketId}")
    public TicketResponse get(@PathVariable Long ticketId, @AuthenticationPrincipal UserPrincipal principal) {
        return response(ticketService.findById(ticketId, principal));
    }

    @GetMapping("/mine")
    public List<TicketResponse> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return ticketService.findMine(principal).stream().map(this::response).toList();
    }

    @GetMapping("/assigned-to-me")
    public List<TicketResponse> assignedToMe(@AuthenticationPrincipal UserPrincipal principal) {
        return ticketService.findAssignedToMe(principal).stream().map(this::response).toList();
    }

    @GetMapping
    public List<TicketResponse> all(@AuthenticationPrincipal UserPrincipal principal) {
        return ticketService.findAll(principal).stream().map(this::response).toList();
    }

    @PutMapping("/{ticketId}/assignment")
    @PreAuthorize("hasRole('ADMIN')")
    public TicketResponse assign(@PathVariable Long ticketId, @Valid @RequestBody AssignTicketRequest request,
                                 @AuthenticationPrincipal UserPrincipal principal) {
        return response(ticketService.assign(ticketId, request.staffId(), principal));
    }

    @GetMapping("/{ticketId}/history")
    public List<TicketHistoryResponse> history(@PathVariable Long ticketId,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        return ticketService.findHistory(ticketId, principal).stream().map(TicketHistoryResponse::from).toList();
    }

    @PostMapping("/{ticketId}/comments")
    public ResponseEntity<CommentResponse> addComment(@PathVariable Long ticketId,
                                                      @Valid @RequestBody AddCommentRequest request,
                                                      @AuthenticationPrincipal UserPrincipal principal,
                                                      UriComponentsBuilder uriBuilder) {
        TicketComment comment = ticketService.addComment(ticketId, request.content(), principal);
        return ResponseEntity.created(uriBuilder.path("/api/tickets/{ticketId}/comments/{commentId}")
                        .build(ticketId, comment.getId()))
                .body(CommentResponse.from(comment));
    }

    @GetMapping("/{ticketId}/comments")
    public List<CommentResponse> comments(@PathVariable Long ticketId,
                                          @AuthenticationPrincipal UserPrincipal principal) {
        return ticketService.findComments(ticketId, principal).stream().map(CommentResponse::from).toList();
    }

    @PutMapping("/{ticketId}/status")
    public TicketResponse changeStatus(@PathVariable Long ticketId, @Valid @RequestBody ChangeStatusRequest request,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        return response(ticketService.changeStatus(ticketId, request.status(), principal, request.reason()));
    }

    @PostMapping("/{ticketId}/student-response")
    public TicketResponse studentResponse(@PathVariable Long ticketId,
                                          @Valid @RequestBody AddCommentRequest request,
                                          @AuthenticationPrincipal UserPrincipal principal) {
        return response(ticketService.respondToPending(ticketId, request.content(), principal));
    }

    @GetMapping("/{ticketId}/sla")
    public SlaResponse sla(@PathVariable Long ticketId, @AuthenticationPrincipal UserPrincipal principal) {
        return SlaResponse.from(ticketService.getSla(ticketId, principal));
    }

    @GetMapping("/overdue")
    public List<TicketResponse> overdue(@AuthenticationPrincipal UserPrincipal principal) {
        return ticketService.findOverdue(principal).stream().map(this::response).toList();
    }

    @GetMapping("/approaching-sla")
    public List<TicketResponse> approachingSla(@AuthenticationPrincipal UserPrincipal principal) {
        return ticketService.findApproachingSla(principal).stream().map(this::response).toList();
    }

    @GetMapping("/pending-student")
    public List<TicketResponse> pendingStudent(@AuthenticationPrincipal UserPrincipal principal) {
        return ticketService.findPendingStudent(principal).stream().map(this::response).toList();
    }

    @GetMapping("/escalated")
    public List<TicketResponse> escalated(@AuthenticationPrincipal UserPrincipal principal) {
        return ticketService.findEscalated(principal).stream().map(this::response).toList();
    }

    @PostMapping("/{ticketId}/escalation")
    @PreAuthorize("hasRole('ADMIN')")
    public TicketResponse escalate(@PathVariable Long ticketId, @Valid @RequestBody EscalationRequest request,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        return response(ticketService.escalate(ticketId, request.reason(), principal));
    }

    @DeleteMapping("/{ticketId}/escalation")
    @PreAuthorize("hasRole('ADMIN')")
    public TicketResponse deEscalate(@PathVariable Long ticketId,
                                     @RequestBody(required = false) DeEscalationRequest request,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        String reason = request == null ? null : request.reason();
        return response(ticketService.deEscalate(ticketId, reason, principal));
    }

    private TicketResponse response(Ticket ticket) {
        return TicketResponse.from(ticket, slaService.assess(ticket));
    }

    public record CreateTicketRequest(
            @NotBlank String subject,
            @NotBlank String description,
            @NotNull TicketPriority priority,
            @NotNull Long studentId) {
    }

    public record ChangeStatusRequest(@NotNull TicketStatus status, @Size(max = TicketComment.MAX_CONTENT_LENGTH) String reason) {
    }

    public record AssignTicketRequest(@NotNull Long staffId) {
    }

    public record EscalationRequest(@NotBlank @Size(max = 1000) String reason) {
    }

    public record DeEscalationRequest(@Size(max = 1000) String reason) {
    }

    public record AddCommentRequest(@NotBlank @Size(max = TicketComment.MAX_CONTENT_LENGTH) String content) {
    }

    public record TicketResponse(Long id, String ticketNumber, String subject, TicketStatus status,
                                 TicketPriority priority, String category, InstantResponse slaDueAt,
                                 Long assignedStaffId, String assignedStaffName, Instant createdAt,
                                 SlaResponse sla, boolean escalated, String escalationReason,
                                 Instant escalatedAt, Instant deEscalatedAt,
                                 boolean pendingStudentAction, String pendingActionDescription) {
        static TicketResponse from(Ticket ticket, SlaAssessment assessment) {
            return new TicketResponse(ticket.getId(), ticket.getTicketNumber(), ticket.getSubject(),
                    ticket.getStatus(), ticket.getPriority(),
                    ticket.getCategory() == null ? null : ticket.getCategory().getName(),
                    new InstantResponse(ticket.getSlaDueAt().toString()),
                    ticket.getAssignedStaff() == null ? null : ticket.getAssignedStaff().getId(),
                    ticket.getAssignedStaff() == null ? null : ticket.getAssignedStaff().getName(),
                    ticket.getCreatedAt(), SlaResponse.from(assessment), ticket.isEscalated(),
                        ticket.getEscalationReason(), ticket.getEscalatedAt(), ticket.getDeEscalatedAt(),
                        ticket.getStatus() == TicketStatus.PENDING_STUDENT,
                        ticket.getStatus() == TicketStatus.PENDING_STUDENT
                            ? "Student information or action is required"
                            : null);
        }
    }

    public record SlaResponse(Instant createdAt, Instant dueAt, long ageSeconds, Long remainingSeconds,
                              boolean approachingSla, boolean overdue) {
        static SlaResponse from(SlaAssessment assessment) {
            return new SlaResponse(assessment.createdAt(), assessment.dueAt(), assessment.ageSeconds(),
                    assessment.remainingSeconds(), assessment.approachingSla(), assessment.overdue());
        }
    }

    public record TicketHistoryResponse(Long id, TicketHistoryAction action, Long actorId, Long previousStaffId,
                                        Long newStaffId, String beforeValue, String afterValue, Instant createdAt) {
        static TicketHistoryResponse from(TicketHistory history) {
            return new TicketHistoryResponse(history.getId(), history.getAction(), history.getActor().getId(),
                    history.getPreviousStaff() == null ? null : history.getPreviousStaff().getId(),
                    history.getNewStaff() == null ? null : history.getNewStaff().getId(),
                    history.getBeforeValue(), history.getAfterValue(), history.getCreatedAt());
        }
    }

    public record CommentResponse(Long id, Long authorId, String authorName, String content, Instant createdAt) {
        static CommentResponse from(TicketComment comment) {
            return new CommentResponse(comment.getId(), comment.getAuthor().getId(), comment.getAuthor().getName(),
                    comment.getContent(), comment.getCreatedAt());
        }
    }

    public record InstantResponse(String value) {
    }
}
