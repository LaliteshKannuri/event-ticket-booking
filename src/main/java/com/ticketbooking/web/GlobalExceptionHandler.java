package com.ticketbooking.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ticketbooking.exception.BadRequestException;
import com.ticketbooking.exception.ConflictException;
import com.ticketbooking.exception.IdempotencyKeyReuseException;
import com.ticketbooking.exception.ResourceNotFoundException;

/**
 * One place that turns exceptions into consistent JSON error bodies (RFC 7807 ProblemDetail).
 * Anything not listed here falls through to Spring Boot's default 500 handling.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** Seat already taken, booking expired, illegal state change. */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ProblemDetail> conflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** Includes optimistic-lock failures: two requests tried to change the same row. */
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ProblemDetail> concurrentUpdate(ConcurrencyFailureException ex) {
        log.debug("Concurrent update rejected: {}", ex.toString());
        return problem(HttpStatus.CONFLICT, "Another request changed the same data at the same time. Please retry.");
    }

    /** A database constraint (such as one booking per seat) stopped the write. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> constraintViolation(DataIntegrityViolationException ex) {
        log.debug("Constraint violation: {}", ex.toString());
        return problem(HttpStatus.CONFLICT, "The request conflicts with existing data.");
    }

    @ExceptionHandler(IdempotencyKeyReuseException.class)
    public ResponseEntity<ProblemDetail> keyReuse(IdempotencyKeyReuseException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ProblemDetail> badRequest(BadRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> invalid(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        body.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
