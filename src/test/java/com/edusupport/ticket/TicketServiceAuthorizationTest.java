package com.edusupport.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.edusupport.security.UserPrincipal;
import com.edusupport.user.User;
import com.edusupport.user.UserRepository;
import com.edusupport.user.UserRole;

@ExtendWith(MockitoExtension.class)
class TicketServiceAuthorizationTest {

    @Mock TicketRepository ticketRepository;
    @Mock UserRepository userRepository;
    @Mock Ticket ticket;

    private TicketService ticketService;
    private User student;
    private User otherStudent;
    private User assignedStaff;
    private User otherStaff;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(ticketRepository, userRepository);
        student = user(10L, UserRole.STUDENT);
        otherStudent = user(11L, UserRole.STUDENT);
        assignedStaff = user(20L, UserRole.STAFF);
        otherStaff = user(21L, UserRole.STAFF);
        lenient().when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        lenient().when(ticket.getStudent()).thenReturn(student);
        lenient().when(ticket.getAssignedStaff()).thenReturn(assignedStaff);
    }

    @Test
    void deniesStudentAccessToAnotherStudentsTicket() {
        assertThatThrownBy(() -> ticketService.findById(1L, principal(otherStudent)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deniesStaffAccessToUnassignedTicket() {
        assertThatThrownBy(() -> ticketService.findById(1L, principal(otherStaff)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void allowsAssignedStaffAccess() {
        assertThat(ticketService.findById(1L, principal(assignedStaff))).isSameAs(ticket);
    }

    @Test
    void allowsAdminAccess() {
        assertThat(ticketService.findById(1L, principal(user(99L, UserRole.ADMIN)))).isSameAs(ticket);
    }

    @Test
    void deniesStaffFromCreatingStudentTickets() {
        assertThatThrownBy(() -> ticketService.create("Subject", "Details", TicketPriority.MEDIUM,
                10L, principal(assignedStaff)))
                .isInstanceOf(AccessDeniedException.class);
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
