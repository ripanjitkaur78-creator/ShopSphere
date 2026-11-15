package com.shopsphere;

import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/** Gives the frontend one consistent error shape: {"message": "..."}. */
@RestControllerAdvice
public class ApiErrors {

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, String>> status(
        ResponseStatusException exception
    ) {
        String message =
            exception.getReason() == null
                ? "Request failed"
                : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(
            Map.of("message", message)
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> validation(
        MethodArgumentNotValidException exception
    ) {
        String message = exception
            .getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .distinct()
            .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
    })
    ResponseEntity<Map<String, String>> malformed(Exception exception) {
        return ResponseEntity.badRequest().body(
            Map.of(
                "message",
                "Invalid request. Check field types and allowed values."
            )
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> conflict(Exception exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            Map.of(
                "message",
                "This record conflicts with existing data. Refresh and try again."
            )
        );
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    ResponseEntity<Map<String, String>> retry(Exception exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            Map.of(
                "message",
                "Another request changed this item. Please try again."
            )
        );
    }
}
