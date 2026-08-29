package cc.superbaby.happy4j.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public final class ApiExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<?> badRequest(Exception failure) {
        return response(HttpStatus.BAD_REQUEST, failure);
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<?> conflict(Exception failure) {
        return response(HttpStatus.CONFLICT, failure);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> serverError(Exception failure) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, failure);
    }

    private static ResponseEntity<?> response(HttpStatus status, Exception failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) cause = cause.getCause();
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "error", status.getReasonPhrase(),
                "message", cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage(),
                "timestamp", Instant.now().toString()));
    }
}
