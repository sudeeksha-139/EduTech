package com.edusupport.ticket;

public class InvalidTicketTransitionException extends RuntimeException {

    public InvalidTicketTransitionException(TicketStatus current, TicketStatus next) {
        super("Cannot move ticket from %s to %s".formatted(current, next));
    }
}
