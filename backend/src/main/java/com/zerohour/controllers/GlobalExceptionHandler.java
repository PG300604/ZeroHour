package com.zerohour.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle all unhandled exceptions — return clean JSON error response.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(
            Exception ex, HttpServletRequest request) {

        System.err.println("[ZeroHour] Unhandled error at "
            + request.getRequestURI() + ": " + ex.getMessage());

        Map<String, Object> error = new HashMap<>();
        error.put("error", "Something went wrong");
        error.put("message", ex.getMessage());
        error.put("path", request.getRequestURI());
        error.put("timestamp", System.currentTimeMillis());

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(error);
    }

    /**
     * Handle IllegalArgumentException — 400 Bad Request.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(
            IllegalArgumentException ex) {

        Map<String, Object> error = new HashMap<>();
        error.put("error", "Bad request");
        error.put("message", ex.getMessage());

        return ResponseEntity.badRequest().body(error);
    }

    /**
     * Handle access denied — 403.
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException ex) {

        Map<String, Object> error = new HashMap<>();
        error.put("error", "Access denied");
        error.put("message", "You don't have permission to access this resource");

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }
}
