package com.edusupport.ticket;

import java.time.Instant;

import com.edusupport.user.User;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

@Entity
@Table(name = "ticket_history")
public class TicketHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @ManyToOne(optional = false)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    private TicketHistoryAction action;

    @ManyToOne
    @JoinColumn(name = "previous_staff_id")
    private User previousStaff;

    @ManyToOne
    @JoinColumn(name = "new_staff_id")
    private User newStaff;

    @Column(length = 1000)
    private String beforeValue;

    @Column(length = 1000)
    private String afterValue;

    private Instant createdAt;

    protected TicketHistory() {
    }

    public TicketHistory(Ticket ticket, User actor, TicketHistoryAction action, User previousStaff, User newStaff) {
        this.ticket = ticket;
        this.actor = actor;
        this.action = action;
        this.previousStaff = previousStaff;
        this.newStaff = newStaff;
    }

    public TicketHistory(Ticket ticket, User actor, TicketHistoryAction action,
                         String beforeValue, String afterValue) {
        this.ticket = ticket;
        this.actor = actor;
        this.action = action;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Ticket getTicket() { return ticket; }
    public User getActor() { return actor; }
    public TicketHistoryAction getAction() { return action; }
    public User getPreviousStaff() { return previousStaff; }
    public User getNewStaff() { return newStaff; }
    public String getBeforeValue() { return beforeValue; }
    public String getAfterValue() { return afterValue; }
    public Instant getCreatedAt() { return createdAt; }
}
