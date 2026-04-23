package ru.fsp.casino.app.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.fsp.casino.game.service.BalanceService;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BalanceService.InsufficientBalanceException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientBalance(BalanceService.InsufficientBalanceException ex) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), "INSUFFICIENT_BALANCE");
    }

    @ExceptionHandler(RoomFullException.class)
    public ResponseEntity<Map<String, Object>> handleRoomFull(RoomFullException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "ROOM_NOT_JOINABLE");
    }

    @ExceptionHandler(RoomNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleRoomNotFound(RoomNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "ROOM_NOT_FOUND");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), "OPERATION_NOT_ALLOWED");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "Access denied", "ACCESS_DENIED");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuth(AuthenticationException ex) {
        return error(HttpStatus.UNAUTHORIZED, "Invalid or expired token", "UNAUTHORIZED");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .toList();
        return ResponseEntity.badRequest().body(Map.of(
            "error", "Validation failed",
            "code", "VALIDATION_ERROR",
            "errors", errors,
            "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "INTERNAL_ERROR");
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message, String code) {
        return ResponseEntity.status(status).body(Map.of(
            "error", message,
            "code", code,
            "timestamp", Instant.now().toString()
        ));
    }
}
