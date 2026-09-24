package com.edusupport.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
class TicketAssignmentServiceTest {

    @Mock TicketRepository ticketRepository;
    @Mock TicketHistoryRepository historyRepository;
    @Mock UserRepository userRepository;
    @Mock Ticket ticket;

    private User admin;
    private User student;
    private User firstStaff;
    private User secondStaff;
    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(ticketRepository, userRepository, historyRepository);
        admin = user(1L, UserRole.ADMIN);
        student = user(2L, UserRole.STUDENT);
        firstStaff = user(3L, UserRole.STAFF);
        secondStaff = user(4L, UserRole.STAFF);
        lenient().when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        lenient().when(ticketRepository.save(ticket)).thenReturn(ticket);
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
    }

    @Test
    void adminCanAssignTicketAndCreatesAssignmentHistory() {
        when(ticket.getAssignedStaff()).thenReturn(null);
        when(userRepository.findById(3L)).thenReturn(Optional.of(firstStaff));

        ticketService.assign(100L, 3L, principal(admin));

        verify(ticket).assignTo(firstStaff);
        ArgumentCaptor<TicketHistory> history = ArgumentCaptor.forClass(TicketHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getAction()).isEqualTo(TicketHistoryAction.ASSIGNED);
        assertThat(history.getValue().getPreviousStaff()).isNull();
        assertThat(history.getValue().getNewStaff()).isSameAs(firstStaff);
    }

    @Test
    void adminCanReassignTicketAndCreatesReassignmentHistory() {
        when(ticket.getAssignedStaff()).thenReturn(firstStaff);
        when(userRepository.findById(4L)).thenReturn(Optional.of(secondStaff));

        ticketService.assign(100L, 4L, principal(admin));

        verify(ticket).assignTo(secondStaff);
        ArgumentCaptor<TicketHistory> history = ArgumentCaptor.forClass(TicketHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getAction()).isEqualTo(TicketHistoryAction.REASSIGNED);
        assertThat(history.getValue().getPreviousStaff()).isSameAs(firstStaff);
        assertThat(history.getValue().getNewStaff()).isSameAs(secondStaff);
    }

    @Test
    void staffCannotAssignTickets() {
        assertThatThrownBy(() -> ticketService.assign(100L, 3L, principal(firstStaff)))
                .isInstanceOf(AccessDeniedException.class);

        verify(ticketRepository, never()).findById(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void studentCannotAssignTickets() {
        assertThatThrownBy(() -> ticketService.assign(100L, 3L, principal(student)))
                .isInstanceOf(AccessDeniedException.class);

        verify(ticketRepository, never()).findById(any());
        verify(historyRepository, never()).save(any());
    }

    @Test
    void rejectsNonexistentTicket() {
        when(ticketRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.assign(404L, 3L, principal(admin)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ticket was not found");
    }

    @Test
    void rejectsNonexistentStaffUser() {
        when(userRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.assign(100L, 3L, principal(admin)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Staff user was not found");
    }

    @Test
    void rejectsAssignmentToNonStaffUser() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> ticketService.assign(100L, 3L, principal(admin)))
                .isInstanceOf(InvalidStaffAssignmentException.class);
    }

    @Test
    void staffCanReadAssignedTicketsAndAdminCanReadAllTickets() {
        when(ticketRepository.findByAssignedStaffIdOrderBySlaDueAtAsc(3L)).thenReturn(java.util.List.of(ticket));
        when(ticketRepository.findAllByOrderByCreatedAtDesc()).thenReturn(java.util.List.of(ticket));

        assertThat(ticketService.findAssignedToMe(principal(firstStaff))).containsExactly(ticket);
        assertThat(ticketService.findAll(principal(admin))).containsExactly(ticket);
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
