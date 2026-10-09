package cl.duoc.rutalimpia.auth.exception;

import java.util.Comparator;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> manejarNoEncontrado(RecursoNoEncontradoException ex,
            HttpServletRequest request) {
        return construir(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ErrorResponse> manejarReglaNegocio(ReglaNegocioException ex, HttpServletRequest request) {
        return construir(HttpStatus.CONFLICT, "CONFLICT", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> manejarValidacion(MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("Los datos enviados no son válidos");
        return construir(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", mensaje, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> manejarJsonInvalido(HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        String mensaje = "El cuerpo de la solicitud no es un JSON válido";
        if (ex.getCause() instanceof JacksonException jackson && !jackson.getPath().isEmpty()) {
            String campo = jackson.getPath().getLast().getPropertyName();
            if (campo != null && campo.matches("[A-Za-z][A-Za-z0-9_]{0,39}")) {
                mensaje = "Valor no válido para el campo " + campo;
            }
        }
        return construir(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", mensaje, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> manejarParametroInvalido(MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Valor no válido para el parámetro " + ex.getName(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> manejarAutenticacion(AuthenticationException ex,
            HttpServletRequest request) {
        String mensaje = ex instanceof BadCredentialsException
                ? "Email o contraseña incorrectos"
                : "Debe autenticarse para acceder a este recurso";
        return construir(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", mensaje, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> manejarAccesoDenegado(AccessDeniedException ex, HttpServletRequest request) {
        return construir(HttpStatus.FORBIDDEN, "FORBIDDEN", "No tiene permisos para realizar esta acción", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> manejarGeneral(Exception ex, HttpServletRequest request) {
        // 404, 405, 415 de spring mvc, para que no salgan como 500
        if (ex instanceof org.springframework.web.ErrorResponse errorSpring) {
            HttpStatus status = HttpStatus.resolve(errorSpring.getStatusCode().value());
            if (status != null && status.is4xxClientError()) {
                return construir(status, status.name(), mensajePara(status), request);
            }
        }
        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Error interno del servidor", request);
    }

    private String mensajePara(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "El recurso solicitado no existe";
            case METHOD_NOT_ALLOWED -> "Método HTTP no permitido para esta ruta";
            case UNSUPPORTED_MEDIA_TYPE -> "Tipo de contenido no soportado, use application/json";
            case NOT_ACCEPTABLE -> "Formato de respuesta no soportado";
            default -> "La solicitud no es válida";
        };
    }

    private ResponseEntity<ErrorResponse> construir(HttpStatus status, String error, String mensaje,
            HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(status, error, mensaje, request.getRequestURI()));
    }
}
