package com.edusupport.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import jakarta.validation.ValidationException;

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
class PendingActionWorkflowTest {

    private static final Instant NOW = Instant.parse("2026-09-24T12:00:00Z");

    @Mock TicketRepository ticketRepository;
    @Mock TicketHistoryRepository historyRepository;
    @Mock TicketCommentRepository commentRepository;
    @Mock UserRepository userRepository;
    @Mock Ticket ticket;

    private User student;
    private User otherStudent;
    private User staff;
    private User otherStaff;
    private User admin;
    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        student = user(10L, UserRole.STUDENT);
        otherStudent = user(11L, UserRole.STUDENT);
        staff = user(20L, UserRole.STAFF);
        otherStaff = user(21L, UserRole.STAFF);
        admin = user(30L, UserRole.ADMIN);
        ticketService = new TicketService(ticketRepository, userRepository, historyRepository, commentRepository,
                new SlaService(Clock.fixed(NOW, ZoneOffset.UTC)));

        lenient().when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        lenient().when(ticketRepository.save(ticket)).thenReturn(ticket);
        lenient().when(ticket.getStudent()).thenReturn(student);
        lenient().when(ticket.getAssignedStaff()).thenReturn(staff);
        lenient().when(userRepository.findById(10L)).thenReturn(Optional.of(student));
        lenient().when(userRepository.findById(20L)).thenReturn(Optional.of(staff));
        lenient().when(userRepository.findById(30L)).thenReturn(Optional.of(admin));
        lenient().when(commentRepository.save(any(TicketComment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void staffMustProvideAReasonWhenRequestingStudentAction() {
        lenient().when(ticket.getStatus()).thenReturn(TicketStatus.IN_PROGRESS);

        assertThatThrownBy(() -> ticketService.changeStatus(100L, TicketStatus.PENDING_STUDENT, principal(staff), " "))
                .isInstanceOf(ValidationException.class);
        verify(ticket, never()).changeStatus(TicketStatus.PENDING_STUDENT);
    }

    @Test
    void assignedStaffCanMoveTicketToPendingAndReasonBecomesComment() {
        when(ticket.getStatus()).thenReturn(TicketStatus.IN_PROGRESS);

        ticketService.changeStatus(100L, TicketStatus.PENDING_STUDENT, principal(staff), "Please upload the signed form.");

        verify(ticket).changeStatus(TicketStatus.PENDING_STUDENT);
        ArgumentCaptor<TicketComment> comment = ArgumentCaptor.forClass(TicketComment.class);
        verify(commentRepository).save(comment.capture());
        assertThat(comment.getValue().getContent()).isEqualTo("Please upload the signed form.");
        List<TicketHistory> history = capturedHistory();
        assertThat(history).extracting(TicketHistory::getAction)
                .containsExactly(TicketHistoryAction.STATUS_CHANGED, TicketHistoryAction.COMMENTED);
        assertThat(history.get(0).getBeforeValue()).isEqualTo(TicketStatus.IN_PROGRESS.name());
        assertThat(history.get(0).getAfterValue()).isEqualTo(TicketStatus.PENDING_STUDENT.name());
    }

    @Test
    void unassignedStaffCannotPutAnotherStaffTicketPending() {
        lenient().when(ticket.getStatus()).thenReturn(TicketStatus.IN_PROGRESS);
        lenient().when(ticket.getAssignedStaff()).thenReturn(otherStaff);

        assertThatThrownBy(() -> ticketService.changeStatus(100L, TicketStatus.PENDING_STUDENT,
                principal(staff), "Please provide information."))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void studentsCannotChangeStatusToPending() {
        lenient().when(ticket.getStatus()).thenReturn(TicketStatus.IN_PROGRESS);

        assertThatThrownBy(() -> ticketService.changeStatus(100L, TicketStatus.PENDING_STUDENT,
                principal(student), "Please provide information."))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void owningStudentResponseStoresCommentAndReturnsTicketToInProgress() {
        lenient().when(ticket.getStatus()).thenReturn(TicketStatus.PENDING_STUDENT);

        Ticket result = ticketService.respondToPending(100L, "The signed form is attached.", principal(student));

        assertThat(result).isSameAs(ticket);
        verify(ticket).changeStatus(TicketStatus.IN_PROGRESS);
        ArgumentCaptor<TicketComment> comment = ArgumentCaptor.forClass(TicketComment.class);
        verify(commentRepository).save(comment.capture());
        assertThat(comment.getValue().getAuthor()).isSameAs(student);
        assertThat(comment.getValue().getContent()).isEqualTo("The signed form is attached.");
        assertThat(capturedHistory()).extracting(TicketHistory::getAction)
                .containsExactly(TicketHistoryAction.COMMENTED, TicketHistoryAction.STATUS_CHANGED);
    }

    @Test
    void anotherStudentCannotRespond() {
        lenient().when(ticket.getStatus()).thenReturn(TicketStatus.PENDING_STUDENT);
        lenient().when(ticket.getStudent()).thenReturn(otherStudent);

        assertThatThrownBy(() -> ticketService.respondToPending(100L, "Unauthorized response.", principal(student)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void staffCannotUseStudentResponseEndpoint() {
        lenient().when(ticket.getStatus()).thenReturn(TicketStatus.PENDING_STUDENT);

        assertThatThrownBy(() -> ticketService.respondToPending(100L, "Wrong actor.", principal(staff)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void responseOnNonPendingTicketIsRejected() {
        when(ticket.getStatus()).thenReturn(TicketStatus.IN_PROGRESS);

        assertThatThrownBy(() -> ticketService.respondToPending(100L, "Not pending.", principal(student)))
                .isInstanceOf(InvalidPendingActionException.class);
        verify(commentRepository, never()).save(any(TicketComment.class));
    }

    @Test
    void repeatedResponseAfterTransitionIsRejected() {
        when(ticket.getStatus()).thenReturn(TicketStatus.IN_PROGRESS);

        assertThatThrownBy(() -> ticketService.respondToPending(100L, "Second response.", principal(student)))
                .isInstanceOf(InvalidPendingActionException.class);
    }

    @Test
    void resolvedClosedAndReopenedTicketsCannotUsePendingResponse() {
        for (TicketStatus status : List.of(TicketStatus.RESOLVED, TicketStatus.CLOSED, TicketStatus.REOPENED)) {
            when(ticket.getStatus()).thenReturn(status);
            assertThatThrownBy(() -> ticketService.respondToPending(100L, "Response.", principal(student)))
                    .isInstanceOf(InvalidPendingActionException.class);
        }
    }

    @Test
    void blankStudentResponseIsRejected() {
        assertThatThrownBy(() -> ticketService.respondToPending(100L, "  ", principal(student)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void missingTicketIsRejected() {
        when(ticketRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.respondToPending(404L, "Response.", principal(student)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pendingTicketResponseExposesActionRequired() {
        Ticket pendingTicket = mock(Ticket.class);
        when(pendingTicket.getStatus()).thenReturn(TicketStatus.PENDING_STUDENT);
        when(pendingTicket.getSlaDueAt()).thenReturn(NOW.plusSeconds(3600));
        when(pendingTicket.getCreatedAt()).thenReturn(NOW.minusSeconds(3600));
        when(pendingTicket.getId()).thenReturn(100L);
        when(pendingTicket.isEscalated()).thenReturn(false);

        TicketController.TicketResponse response = TicketController.TicketResponse.from(pendingTicket,
                new SlaAssessment(NOW.minusSeconds(3600), NOW.plusSeconds(3600), 3600, 3600L, false, false));

        assertThat(response.pendingStudentAction()).isTrue();
        assertThat(response.pendingActionDescription()).isNotBlank();
    }

    @Test
    void pendingStudentSlaContinuesToAge() {
        Ticket pendingTicket = mock(Ticket.class);
        when(pendingTicket.getStatus()).thenReturn(TicketStatus.PENDING_STUDENT);
        when(pendingTicket.getCreatedAt()).thenReturn(NOW.minusSeconds(100));
        when(pendingTicket.getSlaDueAt()).thenReturn(NOW.minusSeconds(1));

        SlaAssessment assessment = new SlaService(Clock.fixed(NOW, ZoneOffset.UTC)).assess(pendingTicket);

        assertThat(assessment.overdue()).isTrue();
    }

    private List<TicketHistory> capturedHistory() {
        ArgumentCaptor<TicketHistory> history = ArgumentCaptor.forClass(TicketHistory.class);
        verify(historyRepository, org.mockito.Mockito.atLeastOnce()).save(history.capture());
        return history.getAllValues();
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
