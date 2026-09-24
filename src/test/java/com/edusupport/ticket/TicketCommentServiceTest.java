package com.edusupport.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
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
class TicketCommentServiceTest {

    @Mock TicketRepository ticketRepository;
    @Mock TicketHistoryRepository historyRepository;
    @Mock TicketCommentRepository commentRepository;
    @Mock UserRepository userRepository;
    @Mock Ticket ticket;

    private User student;
    private User otherStudent;
    private User assignedStaff;
    private User otherStaff;
    private User admin;
    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(ticketRepository, userRepository, historyRepository, commentRepository);
        student = user(10L, UserRole.STUDENT);
        otherStudent = user(11L, UserRole.STUDENT);
        assignedStaff = user(20L, UserRole.STAFF);
        otherStaff = user(21L, UserRole.STAFF);
        admin = user(30L, UserRole.ADMIN);

        lenient().when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        lenient().when(ticketRepository.save(ticket)).thenReturn(ticket);
        lenient().when(ticket.getStudent()).thenReturn(student);
        lenient().when(ticket.getAssignedStaff()).thenReturn(assignedStaff);
        lenient().when(commentRepository.save(any(TicketComment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(userRepository.findById(10L)).thenReturn(Optional.of(student));
        lenient().when(userRepository.findById(11L)).thenReturn(Optional.of(otherStudent));
        lenient().when(userRepository.findById(20L)).thenReturn(Optional.of(assignedStaff));
        lenient().when(userRepository.findById(21L)).thenReturn(Optional.of(otherStaff));
        lenient().when(userRepository.findById(30L)).thenReturn(Optional.of(admin));
    }

    @Test
    void studentCanCommentOnOwnTicket() {
        TicketComment comment = ticketService.addComment(100L, "I have attached the receipt.", principal(student));

        assertThat(comment.getContent()).isEqualTo("I have attached the receipt.");
        assertThat(comment.getAuthor()).isSameAs(student);
        verify(commentRepository).save(any(TicketComment.class));
    }

    @Test
    void studentCannotCommentOnAnotherStudentsTicket() {
        when(ticket.getStudent()).thenReturn(otherStudent);

        assertThatThrownBy(() -> ticketService.addComment(100L, "Not my ticket.", principal(student)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assignedStaffCanComment() {
        TicketComment comment = ticketService.addComment(100L, "The request is being processed.", principal(assignedStaff));

        assertThat(comment.getAuthor()).isSameAs(assignedStaff);
    }

    @Test
    void unassignedStaffCannotComment() {
        assertThatThrownBy(() -> ticketService.addComment(100L, "I should not access this.", principal(otherStaff)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminCanCommentOnAnyTicket() {
        lenient().when(ticket.getStudent()).thenReturn(otherStudent);
        lenient().when(ticket.getAssignedStaff()).thenReturn(otherStaff);

        TicketComment comment = ticketService.addComment(100L, "Manager review completed.", principal(admin));

        assertThat(comment.getAuthor()).isSameAs(admin);
    }

    @Test
    void rejectsBlankComment() {
        assertThatThrownBy(() -> ticketService.addComment(100L, "  ", principal(student)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsCommentOverMaximumLength() {
        String content = "x".repeat(TicketComment.MAX_CONTENT_LENGTH + 1);

        assertThatThrownBy(() -> ticketService.addComment(100L, content, principal(student)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void persistsCommentAndCreatesCommentHistory() {
        ticketService.addComment(100L, "Please review this update.", principal(student));

        ArgumentCaptor<TicketComment> comment = ArgumentCaptor.forClass(TicketComment.class);
        verify(commentRepository).save(comment.capture());
        assertThat(comment.getValue().getTicket()).isSameAs(ticket);
        assertThat(comment.getValue().getAuthor()).isSameAs(student);
        assertThat(comment.getValue().getContent()).isEqualTo("Please review this update.");

        ArgumentCaptor<TicketHistory> history = ArgumentCaptor.forClass(TicketHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getAction()).isEqualTo(TicketHistoryAction.COMMENTED);
        assertThat(history.getValue().getActor()).isSameAs(student);
    }

    @Test
    void returnsCommentsInRepositoryChronologicalOrder() {
        TicketComment first = new TicketComment(ticket, student, "First");
        TicketComment second = new TicketComment(ticket, assignedStaff, "Second");
        when(commentRepository.findByTicketIdOrderByCreatedAtAsc(100L)).thenReturn(List.of(first, second));

        List<TicketComment> comments = ticketService.findComments(100L, principal(student));

        assertThat(comments).containsExactly(first, second);
        verify(commentRepository).findByTicketIdOrderByCreatedAtAsc(100L);
    }

    @Test
    void createsCreationHistory() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(student));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ticketService.create("New request", "Details", TicketPriority.MEDIUM, 10L, principal(student));

        ArgumentCaptor<TicketHistory> history = ArgumentCaptor.forClass(TicketHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getAction()).isEqualTo(TicketHistoryAction.CREATED);
        assertThat(history.getValue().getAfterValue()).isEqualTo(TicketStatus.NEW.name());
    }

    @Test
    void createsStatusChangeHistory() {
        when(ticket.getStatus()).thenReturn(TicketStatus.NEW);

        ticketService.changeStatus(100L, TicketStatus.TRIAGED, principal(assignedStaff));

        verify(ticket).changeStatus(TicketStatus.TRIAGED);
        ArgumentCaptor<TicketHistory> history = ArgumentCaptor.forClass(TicketHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getAction()).isEqualTo(TicketHistoryAction.STATUS_CHANGED);
        assertThat(history.getValue().getBeforeValue()).isEqualTo(TicketStatus.NEW.name());
        assertThat(history.getValue().getAfterValue()).isEqualTo(TicketStatus.TRIAGED.name());
    }

    @Test
    void missingTicketIsRejectedBeforeCommentPersistence() {
        when(ticketRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.addComment(404L, "Missing ticket.", principal(student)))
                .isInstanceOf(IllegalArgumentException.class);
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
