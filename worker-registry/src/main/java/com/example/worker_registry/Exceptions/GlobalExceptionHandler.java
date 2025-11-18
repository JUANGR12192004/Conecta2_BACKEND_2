package com.example.worker_registry.exceptions;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.*;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // ======= Helpers comunes (se conserva tu formato de salida) =======
    private ResponseEntity<Map<String, Object>> build(
            HttpStatus status, String mensaje, List<String> errores) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("mensaje", mensaje);
        if (errores != null && !errores.isEmpty()) {
            body.put("errores", errores);
        }
        return ResponseEntity.status(status).body(body);
    }

    // HU005/HU006: ADDED - Validación de @Valid en @RequestBody
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<String> errores = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.toList());
        return build(HttpStatus.BAD_REQUEST, "Errores de validación encontrados", errores);
    }

    // HU005/HU006: ADDED - Validación en parámetros (path/query)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> errores = ex.getConstraintViolations()
                .stream()
                .map(this::formatConstraintViolation)
                .collect(Collectors.toList());
        return build(HttpStatus.BAD_REQUEST, "Errores de validación encontrados", errores);
    }

    private String formatConstraintViolation(ConstraintViolation<?> cv) {
        String field = cv.getPropertyPath() != null ? cv.getPropertyPath().toString() : "param";
        return field + ": " + cv.getMessage();
    }

    // HU005/HU006: ADDED - tipos inválidos en path variables / query params
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String msg = String.format("Parámetro '%s' con valor '%s' no es del tipo esperado",
                ex.getName(), ex.getValue());
        return build(HttpStatus.BAD_REQUEST, "Petición inválida", List.of(msg));
    }

    // HU005/HU006: ADDED - Falta un parámetro requerido
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        String msg = String.format("Falta el parámetro requerido '%s'", ex.getParameterName());
        return build(HttpStatus.BAD_REQUEST, "Petición inválida", List.of(msg));
    }

    // HU005/HU006: ADDED - JSON mal formado o campos con tipo incompatible
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "Cuerpo de la solicitud no legible o con formato inválido",
                List.of(Optional.ofNullable(ex.getMostSpecificCause())
                        .map(Throwable::getMessage)
                        .orElse(ex.getMessage())));
    }

    // 404 común cuando no se encuentra un recurso (servicio, cliente, etc.)
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(EntityNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage() != null ? ex.getMessage() : "Recurso no encontrado", null);
    }

    // 400 para reglas de negocio inválidas (fecha anterior, estado no permitido, etc.)
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(RuntimeException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage() != null ? ex.getMessage() : "Petición inválida", null);
    }

    // 409 conflictos por llaves duplicadas/uniqueness (correo, celular, etc.)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(DataIntegrityViolationException ex) {
        return build(HttpStatus.CONFLICT, "Conflicto de datos (posible duplicado/relación inválida)", 
                List.of(Optional.ofNullable(ex.getMostSpecificCause()).map(Throwable::getMessage).orElse(ex.getMessage())));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage() != null ? ex.getMessage() : "Acceso denegado", null);
    }

    // Genérico 500
    // 404: ruta no encontrada o recurso no localizado
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Map<String, Object>> handleNoHandler(Exception ex) {
        return build(HttpStatus.NOT_FOUND, "Ruta no encontrada", List.of(ex.getMessage()));
    }

    // 405: método HTTP no soportado
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        String allowed = ex.getSupportedHttpMethods() != null ? ex.getSupportedHttpMethods().toString() : "";
        String msg = String.format("Método no permitido. Usa uno de: %s", allowed);
        return build(HttpStatus.METHOD_NOT_ALLOWED, msg, List.of(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Error interno. Intenta de nuevo o contacta soporte.", List.of(ex.getMessage()));
    }
}
