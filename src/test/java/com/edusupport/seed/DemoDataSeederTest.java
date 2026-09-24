package com.edusupport.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.edusupport.category.Category;
import com.edusupport.category.CategoryRepository;
import com.edusupport.ticket.TicketRepository;
import com.edusupport.user.User;
import com.edusupport.user.UserRepository;
import com.edusupport.user.UserRole;

@ExtendWith(MockitoExtension.class)
class DemoDataSeederTest {

    @Mock UserRepository userRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock TicketRepository ticketRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void runsFromSpringStartupHookAndCreatesHashedDemoUsersWhenRecordsAreMissing() throws Exception {
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(categoryRepository.findByNameIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ticketRepository.existsBySubjectAndStudentId(anyString(), any())).thenReturn(false);
        when(ticketRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DemoDataSeeder seeder = new DemoDataSeeder(userRepository, categoryRepository, ticketRepository, passwordEncoder);
        assertThat(seeder).isInstanceOf(org.springframework.boot.CommandLineRunner.class);
        seeder.run();

        ArgumentCaptor<User> users = ArgumentCaptor.forClass(User.class);
        verify(userRepository, org.mockito.Mockito.times(3)).save(users.capture());
        assertThat(users.getAllValues()).allSatisfy(user -> {
            assertThat(user.getPasswordHash()).isNotEqualTo(DemoDataSeeder.DEMO_PASSWORD);
            assertThat(passwordEncoder.matches(DemoDataSeeder.DEMO_PASSWORD, user.getPasswordHash())).isTrue();
        });
    }

    @Test
    void doesNotOverwriteExistingUsersOrCreateDuplicateTickets() {
        User student = new User("Existing Student", "student@edusupport.com",
                passwordEncoder.encode("existing-password"), UserRole.STUDENT);
        User staff = new User("Existing Staff", "staff@edusupport.com",
                passwordEncoder.encode("existing-password"), UserRole.STAFF);
        User admin = new User("Existing Admin", "admin@edusupport.com",
                passwordEncoder.encode("existing-password"), UserRole.ADMIN);
        when(userRepository.findByEmailIgnoreCase("student@edusupport.com")).thenReturn(Optional.of(student));
        when(userRepository.findByEmailIgnoreCase("staff@edusupport.com")).thenReturn(Optional.of(staff));
        when(userRepository.findByEmailIgnoreCase("admin@edusupport.com")).thenReturn(Optional.of(admin));
        when(categoryRepository.findByNameIgnoreCase(anyString())).thenAnswer(invocation ->
                Optional.of(new Category(invocation.getArgument(0))));
        when(ticketRepository.existsBySubjectAndStudentId(anyString(), any())).thenReturn(true);

        DemoDataSeeder seeder = new DemoDataSeeder(userRepository, categoryRepository, ticketRepository, passwordEncoder);
        seeder.seed();

        verify(userRepository, never()).save(any(User.class));
        verify(categoryRepository, never()).save(any(Category.class));
        verify(ticketRepository, never()).save(any());
        assertThat(passwordEncoder.matches("existing-password", student.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("existing-password", staff.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("existing-password", admin.getPasswordHash())).isTrue();
    }
}
