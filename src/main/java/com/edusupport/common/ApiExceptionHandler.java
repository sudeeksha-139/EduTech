package com.edusupport.common;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import jakarta.validation.ValidationException;

import com.edusupport.ticket.InvalidTicketTransitionException;
import com.edusupport.ticket.InvalidStaffAssignmentException;
import com.edusupport.ticket.InvalidPendingActionException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public org.springframework.http.ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("status", 403, "message", exception.getMessage()));
    }

    @ExceptionHandler(InvalidTicketTransitionException.class)
    public org.springframework.http.ResponseEntity<Map<String, Object>> handleInvalidTransition(
            InvalidTicketTransitionException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("status", 409, "message", exception.getMessage()));
    }

    @ExceptionHandler(InvalidStaffAssignmentException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidStaffAssignment(
            InvalidStaffAssignmentException exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("status", 422, "message", exception.getMessage()));
    }

    @ExceptionHandler(InvalidPendingActionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidPendingAction(
            InvalidPendingActionException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("status", 409, "message", exception.getMessage()));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(ValidationException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("status", 400, "message", exception.getMessage()));
    }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<Map<String, Object>> handleRequestValidation(MethodArgumentNotValidException exception) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(Map.of("status", 400, "message", "Request validation failed"));
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<Map<String, Object>> handleUnreadableRequest(HttpMessageNotReadableException exception) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(Map.of("status", 400, "message", "Request body is invalid"));
        }

    @ExceptionHandler(IllegalArgumentException.class)
    public org.springframework.http.ResponseEntity<Map<String, Object>> handleNotFound(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("status", 404, "message", exception.getMessage()));
    }
}
