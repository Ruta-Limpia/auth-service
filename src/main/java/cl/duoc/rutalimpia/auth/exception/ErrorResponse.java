package cl.duoc.rutalimpia.auth.exception;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.http.HttpStatus;

public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String mensaje,
        String path) {

    public static ErrorResponse of(HttpStatus status, String error, String mensaje, String path) {
        return new ErrorResponse(
                LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
                status.value(),
                error,
                mensaje,
                path);
    }
}
