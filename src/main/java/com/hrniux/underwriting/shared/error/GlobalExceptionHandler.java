package com.hrniux.underwriting.shared.error;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.hrniux.underwriting.model.ModelUnavailableException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> domain(DomainException error, HttpServletRequest request) {
        HttpStatus status = error instanceof ResourceNotFoundException
                ? HttpStatus.NOT_FOUND
                : error instanceof ConflictException
                        ? HttpStatus.CONFLICT
                        : error instanceof ServiceCapacityException
                                ? HttpStatus.SERVICE_UNAVAILABLE
                                : HttpStatus.BAD_REQUEST;
        return response(status, error.errorCode(), error.getMessage(), request);
    }

    @ExceptionHandler(ModelUnavailableException.class)
    public ResponseEntity<ProblemDetail> model(ModelUnavailableException error, HttpServletRequest request) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, error.errorCode(), error.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> validation(
            MethodArgumentNotValidException error,
            HttpServletRequest request) {
        ResponseEntity<ProblemDetail> response = response(
                HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request);
        response.getBody().setProperty("violations", violations(error.getBindingResult().getFieldErrors()));
        return response;
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ProblemDetail> binding(BindException error, HttpServletRequest request) {
        ResponseEntity<ProblemDetail> response = response(
                HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request);
        response.getBody().setProperty("violations", violations(error.getBindingResult().getFieldErrors()));
        return response;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetail> malformed(Exception error, HttpServletRequest request) {
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
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("traceId", traceId);
        problem.setProperty("timestamp", Instant.now());
        return ResponseEntity.status(status).header("X-Trace-Id", traceId).body(problem);
    }

    private Map<String, String> violations(java.util.List<FieldError> fieldErrors) {
        return fieldErrors.stream().collect(Collectors.toMap(
                FieldError::getField,
                error -> error.getDefaultMessage() == null ? "invalid value" : error.getDefaultMessage(),
                (first, ignored) -> first,
                LinkedHashMap::new));
    }
}
