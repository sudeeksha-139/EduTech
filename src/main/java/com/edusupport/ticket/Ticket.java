package com.edusupport.ticket;

import java.time.Instant;
import java.util.UUID;

import com.edusupport.user.User;
import com.edusupport.category.Category;

import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String ticketNumber;
    private String subject;
    private String description;

    @Enumerated(EnumType.STRING)
    private TicketStatus status;

    @Enumerated(EnumType.STRING)
    private TicketPriority priority;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id")
    private User student;

    @ManyToOne
    @JoinColumn(name = "assigned_staff_id")
    private User assignedStaff;

    private Instant slaDueAt;
    private Instant resolvedAt;
    private Instant createdAt;
    private Instant updatedAt;

    private boolean escalated;
    private String escalationReason;
    private Instant escalatedAt;

    @ManyToOne
    @JoinColumn(name = "escalated_by")
    private User escalatedBy;

    private Instant deEscalatedAt;

    @ManyToOne
    @JoinColumn(name = "de_escalated_by")
    private User deEscalatedBy;

    private String deEscalationReason;

    @Version
    private Long version;

    protected Ticket() {
    }

    public Ticket(String subject, String description, TicketPriority priority, User student, Instant slaDueAt) {
        this(subject, description, priority, null, student, slaDueAt);
    }

    public Ticket(String subject, String description, TicketPriority priority, Category category,
                  User student, Instant slaDueAt) {
        this.subject = subject;
        this.description = description;
        this.priority = priority;
        this.category = category;
        this.student = student;
        this.slaDueAt = slaDueAt;
        this.status = TicketStatus.NEW;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
        ticketNumber = "ES-%s".formatted(UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    public void changeStatus(TicketStatus nextStatus) {
        if (!TicketTransitionPolicy.isAllowed(status, nextStatus)) {
            throw new InvalidTicketTransitionException(status, nextStatus);
        }
        status = nextStatus;
        if (nextStatus == TicketStatus.RESOLVED) {
            resolvedAt = Instant.now();
        }
        updatedAt = Instant.now();
    }

    public void assignTo(User staff) {
        this.assignedStaff = staff;
        this.updatedAt = Instant.now();
    }

    public void escalate(String reason, User actor) {
        this.escalated = true;
        this.escalationReason = reason;
        this.escalatedAt = Instant.now();
        this.escalatedBy = actor;
        this.deEscalatedAt = null;
        this.deEscalatedBy = null;
        this.deEscalationReason = null;
        this.updatedAt = Instant.now();
    }

    public void deEscalate(String reason, User actor) {
        this.escalated = false;
        this.deEscalationReason = reason;
        this.deEscalatedAt = Instant.now();
        this.deEscalatedBy = actor;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getTicketNumber() { return ticketNumber; }
    public String getSubject() { return subject; }
    public String getDescription() { return description; }
    public TicketStatus getStatus() { return status; }
    public TicketPriority getPriority() { return priority; }
    public Category getCategory() { return category; }
    public User getStudent() { return student; }
    public User getAssignedStaff() { return assignedStaff; }
    public Instant getSlaDueAt() { return slaDueAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public boolean isEscalated() { return escalated; }
    public String getEscalationReason() { return escalationReason; }
    public Instant getEscalatedAt() { return escalatedAt; }
    public User getEscalatedBy() { return escalatedBy; }
    public Instant getDeEscalatedAt() { return deEscalatedAt; }
    public User getDeEscalatedBy() { return deEscalatedBy; }
    public String getDeEscalationReason() { return deEscalationReason; }
}
