package com.edusupport.ticket;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    List<Ticket> findByAssignedStaffIdOrderBySlaDueAtAsc(Long staffId);
    List<Ticket> findAllByOrderByCreatedAtDesc();
    boolean existsBySubjectAndStudentId(String subject, Long studentId);
}
