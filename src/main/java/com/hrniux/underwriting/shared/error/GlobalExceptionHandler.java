package com.hrniux.underwriting.shared.error;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.hrniux.underwriting.model.ModelUnavailableException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> domain(DomainException error, HttpServletRequest request) {
        HttpStatus status = error instanceof ResourceNotFoundException
                ? HttpStatus.NOT_FOUND
                : error instanceof ConflictException ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        return response(status, error.errorCode(), error.getMessage(), request);
    }

    @ExceptionHandler(ModelUnavailableException.class)
    public ResponseEntity<ProblemDetail> model(ModelUnavailableException error, HttpServletRequest request) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, error.errorCode(), error.getMessage(), request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class,
            HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ResponseEntity<ProblemDetail> validation(Exception error, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(Exception error, HttpServletRequest request) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error", request);
    }

    private ResponseEntity<ProblemDetail> response(
            HttpStatus status,
            String errorCode,
            String detail,
            HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("traceId", traceId);
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(status).header("X-Trace-Id", traceId).body(problem);
    }
}
