package com.edusupport.seed;

import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.edusupport.category.Category;
import com.edusupport.category.CategoryRepository;
import com.edusupport.ticket.Ticket;
import com.edusupport.ticket.TicketPriority;
import com.edusupport.ticket.TicketRepository;
import com.edusupport.ticket.TicketStatus;
import com.edusupport.user.User;
import com.edusupport.user.UserRepository;
import com.edusupport.user.UserRole;

@Component
@Profile("demo")
public class DemoDataSeeder implements CommandLineRunner {

    public static final String DEMO_PASSWORD = "EduSupportDemo!2026";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final TicketRepository ticketRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(UserRepository userRepository, CategoryRepository categoryRepository,
                          TicketRepository ticketRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.ticketRepository = ticketRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seed();
    }

    @Transactional
    public void seed() {
        Map<String, Category> categories = seedCategories();
        User student = findOrCreateUser("Demo Student", "student@edusupport.com", UserRole.STUDENT);
        User staff = findOrCreateUser("Demo Staff", "staff@edusupport.com", UserRole.STAFF);
        findOrCreateUser("Demo Admin", "admin@edusupport.com", UserRole.ADMIN);

        seedTickets(student, staff, categories);
        log.info("EduSupport demo data is ready: demo users and sample tickets are available");
    }

    private Map<String, Category> seedCategories() {
        return Map.of(
                "Fees", findOrCreateCategory("Fees"),
                "Attendance", findOrCreateCategory("Attendance"),
                "ID Card", findOrCreateCategory("ID Card"),
                "Documents", findOrCreateCategory("Documents"),
                "Certificates", findOrCreateCategory("Certificates"),
                "Other", findOrCreateCategory("Other"));
    }

    private Category findOrCreateCategory(String name) {
        return categoryRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> categoryRepository.save(new Category(name)));
    }

    private User findOrCreateUser(String name, String email, UserRole role) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> userRepository.save(
                        new User(name, email, passwordEncoder.encode(DEMO_PASSWORD), role)));
    }

    private void seedTickets(User student, User staff, Map<String, Category> categories) {
        seedTicket("New attendance correction", "Attendance for 12 September is missing from the student portal.",
                TicketPriority.MEDIUM, categories.get("Attendance"), student, staff, TicketStatus.NEW, Instant.now().plusSeconds(72 * 3600L));
        seedTicket("High-priority fee issue", "A duplicate fee payment is showing on the student account.",
                TicketPriority.HIGH, categories.get("Fees"), student, staff, TicketStatus.IN_PROGRESS, Instant.now().minusSeconds(2 * 3600L));
        seedTicket("ID card request", "Requesting a replacement ID card after a damaged card was returned.",
                TicketPriority.MEDIUM, categories.get("ID Card"), student, staff, TicketStatus.ASSIGNED, Instant.now().plusSeconds(24 * 3600L));
        seedTicket("Certificate request", "Please confirm the documents needed for a bonafide certificate.",
                TicketPriority.LOW, categories.get("Certificates"), student, staff, TicketStatus.PENDING_STUDENT, Instant.now().plusSeconds(96 * 3600L));
        seedTicket("Resolved document request", "Transcript download access was restored for the student.",
                TicketPriority.LOW, categories.get("Documents"), student, staff, TicketStatus.RESOLVED, Instant.now().plusSeconds(120 * 3600L));
    }

    private void seedTicket(String subject, String description, TicketPriority priority, Category category,
                            User student, User staff, TicketStatus targetStatus, Instant slaDueAt) {
        if (ticketRepository.existsBySubjectAndStudentId(subject, student.getId())) {
            return;
        }

        Ticket ticket = new Ticket(subject, description, priority, category, student, slaDueAt);
        ticket.assignTo(staff);
        ticketRepository.save(ticket);
        moveToTargetStatus(ticket, targetStatus);
        ticketRepository.save(ticket);
    }

    private void moveToTargetStatus(Ticket ticket, TicketStatus targetStatus) {
        if (targetStatus == TicketStatus.NEW) {
            return;
        }
        ticket.changeStatus(TicketStatus.TRIAGED);
        if (targetStatus == TicketStatus.TRIAGED) {
            return;
        }
        ticket.changeStatus(TicketStatus.ASSIGNED);
        if (targetStatus == TicketStatus.ASSIGNED) {
            return;
        }
        ticket.changeStatus(TicketStatus.IN_PROGRESS);
        if (targetStatus == TicketStatus.IN_PROGRESS) {
            return;
        }
        if (targetStatus == TicketStatus.PENDING_STUDENT) {
            ticket.changeStatus(TicketStatus.PENDING_STUDENT);
            return;
        }
        if (targetStatus == TicketStatus.RESOLVED) {
            ticket.changeStatus(TicketStatus.RESOLVED);
            return;
        }
        throw new IllegalArgumentException("Unsupported demo ticket status: " + targetStatus);
    }
}
