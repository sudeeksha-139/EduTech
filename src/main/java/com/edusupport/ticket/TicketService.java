package com.edusupport.ticket;

import java.time.Instant;
import java.util.List;

import jakarta.validation.ValidationException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.beans.factory.annotation.Autowired;

import com.edusupport.security.UserPrincipal;
import com.edusupport.user.User;
import com.edusupport.user.UserRepository;
import com.edusupport.user.UserRole;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final TicketHistoryRepository ticketHistoryRepository;
    private final TicketCommentRepository ticketCommentRepository;
    private final SlaService slaService;

    public TicketService(TicketRepository ticketRepository, UserRepository userRepository) {
        this(ticketRepository, userRepository, null, null, new SlaService(java.time.Clock.systemUTC()));
    }

    public TicketService(TicketRepository ticketRepository, UserRepository userRepository,
                         TicketHistoryRepository ticketHistoryRepository) {
        this(ticketRepository, userRepository, ticketHistoryRepository, null, new SlaService(java.time.Clock.systemUTC()));
    }

    public TicketService(TicketRepository ticketRepository, UserRepository userRepository,
                         TicketHistoryRepository ticketHistoryRepository,
                         TicketCommentRepository ticketCommentRepository) {
        this(ticketRepository, userRepository, ticketHistoryRepository, ticketCommentRepository,
            new SlaService(java.time.Clock.systemUTC()));
        }

        @Autowired
        public TicketService(TicketRepository ticketRepository, UserRepository userRepository,
                 TicketHistoryRepository ticketHistoryRepository,
                 TicketCommentRepository ticketCommentRepository, SlaService slaService) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.ticketHistoryRepository = ticketHistoryRepository;
        this.ticketCommentRepository = ticketCommentRepository;
        this.slaService = slaService;
    }

    @Transactional
    public Ticket create(String subject, String description, TicketPriority priority, Long studentId) {
        return create(subject, description, priority, studentId, null);
    }

    @Transactional
    public Ticket create(String subject, String description, TicketPriority priority, Long studentId,
                         UserPrincipal principal) {
        if (principal != null && principal.getRole() == UserRole.STAFF) {
            throw new AccessDeniedException("Staff cannot create student tickets");
        }
        if (principal != null && principal.getRole() == UserRole.STUDENT
                && !principal.getId().equals(studentId)) {
            throw new AccessDeniedException("Students can only create their own tickets");
        }
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student was not found"));
        Instant dueAt = slaService.dueAt(slaService.now(), priority);
        Ticket saved = ticketRepository.save(new Ticket(subject, description, priority, student, dueAt));
        recordHistory(saved, principal, TicketHistoryAction.CREATED, null, TicketStatus.NEW.name(), null, null);
        return saved;
    }

    @Transactional
    public Ticket changeStatus(Long ticketId, TicketStatus nextStatus, UserPrincipal principal) {
        return changeStatus(ticketId, nextStatus, principal, null);
    }

    @Transactional
    public Ticket changeStatus(Long ticketId, TicketStatus nextStatus, UserPrincipal principal, String reason) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket was not found"));
        assertCanAccess(ticket, principal);
        if (principal.getRole() == UserRole.STUDENT) {
            throw new AccessDeniedException("Students cannot change ticket status");
        }
        if (nextStatus == TicketStatus.PENDING_STUDENT) {
            validatePendingReason(reason);
        }
        TicketStatus previousStatus = ticket.getStatus();
        ticket.changeStatus(nextStatus);
        Ticket saved = ticketRepository.save(ticket);
        recordHistory(saved, principal, TicketHistoryAction.STATUS_CHANGED,
            previousStatus.name(), nextStatus.name(), null, null);
        if (nextStatus == TicketStatus.PENDING_STUDENT) {
            saveComment(ticket, principal, reason);
        }
        return saved;
    }

        @Transactional
        public Ticket assign(Long ticketId, Long staffId, UserPrincipal principal) {
        requireRole(principal, UserRole.ADMIN, "Only administrators can assign tickets");
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new IllegalArgumentException("Ticket was not found"));
        User staff = userRepository.findById(staffId)
            .orElseThrow(() -> new IllegalArgumentException("Staff user was not found"));
        if (staff.getRole() != UserRole.STAFF) {
            throw new InvalidStaffAssignmentException("Ticket ownership can only be assigned to staff users");
        }
        User actor = userRepository.findById(principal.getId())
            .orElseThrow(() -> new IllegalArgumentException("Acting user was not found"));
        User previousStaff = ticket.getAssignedStaff();
        TicketHistoryAction action = previousStaff == null
            ? TicketHistoryAction.ASSIGNED
            : TicketHistoryAction.REASSIGNED;
        ticket.assignTo(staff);
        Ticket saved = ticketRepository.save(ticket);
        if (ticketHistoryRepository != null) {
            ticketHistoryRepository.save(new TicketHistory(ticket, actor, action, previousStaff, staff));
        }
        return saved;
        }

    @Transactional
    public TicketComment addComment(Long ticketId, String content, UserPrincipal principal) {
        validateComment(content);
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket was not found"));
        assertCanAccess(ticket, principal);
        return saveComment(ticket, principal, content);
    }

    @Transactional
    public Ticket respondToPending(Long ticketId, String content, UserPrincipal principal) {
        validateComment(content);
        if (principal.getRole() != UserRole.STUDENT) {
            throw new AccessDeniedException("Only the student owner can respond to a pending request");
        }
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket was not found"));
        assertCanAccess(ticket, principal);
        if (ticket.getStatus() != TicketStatus.PENDING_STUDENT) {
            throw new InvalidPendingActionException("Ticket is not waiting for student action");
        }
        saveComment(ticket, principal, content);
        TicketStatus previousStatus = ticket.getStatus();
        ticket.changeStatus(TicketStatus.IN_PROGRESS);
        Ticket saved = ticketRepository.save(ticket);
        recordHistory(saved, principal, TicketHistoryAction.STATUS_CHANGED,
                previousStatus.name(), TicketStatus.IN_PROGRESS.name(), null, null);
        return saved;
    }

    private TicketComment saveComment(Ticket ticket, UserPrincipal principal, String content) {
        User author = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("Comment author was not found"));
        TicketComment saved = ticketCommentRepository.save(new TicketComment(ticket, author, content.trim()));
        recordHistory(ticket, principal, TicketHistoryAction.COMMENTED, null, "comment:" + saved.getId(), null, null);
        return saved;
    }

    private void validateComment(String content) {
        if (content == null || content.isBlank()) {
            throw new ValidationException("Comment content must not be blank");
        }
        if (content.length() > TicketComment.MAX_CONTENT_LENGTH) {
            throw new ValidationException("Comment content must not exceed " + TicketComment.MAX_CONTENT_LENGTH + " characters");
        }
    }

    private void validatePendingReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("A reason is required when requesting student action");
        }
        validateComment(reason);
    }

    @Transactional(readOnly = true)
    public Ticket findById(Long ticketId, UserPrincipal principal) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket was not found"));
        assertCanAccess(ticket, principal);
        return ticket;
    }

    @Transactional(readOnly = true)
    public List<Ticket> findMine(UserPrincipal principal) {
        requireRole(principal, UserRole.STUDENT, "Only students can access their student ticket list");
        return ticketRepository.findByStudentIdOrderByCreatedAtDesc(principal.getId());
    }

    @Transactional(readOnly = true)
    public List<Ticket> findAssignedToMe(UserPrincipal principal) {
        requireRole(principal, UserRole.STAFF, "Only staff can access their assigned ticket list");
        return ticketRepository.findByAssignedStaffIdOrderBySlaDueAtAsc(principal.getId());
    }

    @Transactional(readOnly = true)
    public List<Ticket> findAll(UserPrincipal principal) {
        requireRole(principal, UserRole.ADMIN, "Only administrators can access all tickets");
        return ticketRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<TicketHistory> findHistory(Long ticketId, UserPrincipal principal) {
        findById(ticketId, principal);
        return ticketHistoryRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }

    @Transactional(readOnly = true)
    public List<TicketComment> findComments(Long ticketId, UserPrincipal principal) {
        findById(ticketId, principal);
        return ticketCommentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }

    @Transactional(readOnly = true)
    public SlaAssessment getSla(Long ticketId, UserPrincipal principal) {
        return slaService.assess(findById(ticketId, principal));
    }

    @Transactional(readOnly = true)
    public List<Ticket> findOverdue(UserPrincipal principal) {
        return visibleTickets(principal).stream()
                .filter(ticket -> slaService.assess(ticket).overdue())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Ticket> findApproachingSla(UserPrincipal principal) {
        return visibleTickets(principal).stream()
                .filter(ticket -> slaService.assess(ticket).approachingSla())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Ticket> findPendingStudent(UserPrincipal principal) {
        return visibleTickets(principal).stream()
                .filter(ticket -> ticket.getStatus() == TicketStatus.PENDING_STUDENT)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Ticket> findEscalated(UserPrincipal principal) {
        return visibleTickets(principal).stream()
                .filter(Ticket::isEscalated)
                .toList();
    }

    @Transactional
    public Ticket escalate(Long ticketId, String reason, UserPrincipal principal) {
        requireRole(principal, UserRole.ADMIN, "Only administrators can escalate tickets");
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket was not found"));
        User actor = actor(principal);
        ticket.escalate(reason, actor);
        Ticket saved = ticketRepository.save(ticket);
        recordHistory(saved, principal, TicketHistoryAction.ESCALATED, "false", reason, null, null);
        return saved;
    }

    @Transactional
    public Ticket deEscalate(Long ticketId, String reason, UserPrincipal principal) {
        requireRole(principal, UserRole.ADMIN, "Only administrators can de-escalate tickets");
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket was not found"));
        User actor = actor(principal);
        ticket.deEscalate(reason, actor);
        Ticket saved = ticketRepository.save(ticket);
        recordHistory(saved, principal, TicketHistoryAction.DE_ESCALATED, "true", reason, null, null);
        return saved;
    }

    private void recordHistory(Ticket ticket, UserPrincipal principal, TicketHistoryAction action,
                               String beforeValue, String afterValue, User previousStaff, User newStaff) {
        if (ticketHistoryRepository == null || principal == null) {
            return;
        }
        User actor = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("History actor was not found"));
        if (previousStaff != null || newStaff != null) {
            ticketHistoryRepository.save(new TicketHistory(ticket, actor, action, previousStaff, newStaff));
        } else {
            ticketHistoryRepository.save(new TicketHistory(ticket, actor, action, beforeValue, afterValue));
        }
    }

    private User actor(UserPrincipal principal) {
        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("Acting user was not found"));
    }

    private List<Ticket> visibleTickets(UserPrincipal principal) {
        return switch (principal.getRole()) {
            case ADMIN -> ticketRepository.findAllByOrderByCreatedAtDesc();
            case STAFF -> ticketRepository.findByAssignedStaffIdOrderBySlaDueAtAsc(principal.getId());
            case STUDENT -> ticketRepository.findByStudentIdOrderByCreatedAtDesc(principal.getId());
        };
    }

    private void assertCanAccess(Ticket ticket, UserPrincipal principal) {
        boolean allowed = switch (principal.getRole()) {
            case ADMIN -> true;
            case STUDENT -> ticket.getStudent().getId().equals(principal.getId());
            case STAFF -> ticket.getAssignedStaff() != null
                    && ticket.getAssignedStaff().getId().equals(principal.getId());
        };
        if (!allowed) {
            throw new AccessDeniedException("You do not have access to this ticket");
        }
    }

    private void requireRole(UserPrincipal principal, UserRole requiredRole, String message) {
        if (principal.getRole() != requiredRole) {
            throw new AccessDeniedException(message);
        }
    }

}
