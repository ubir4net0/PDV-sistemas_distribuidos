package dev.pdv.api.access.http;

import java.io.IOException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class SafeErrors {
    public record ErrorBody(String code) { }

    public static void write(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        if (status == 401) {
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        response.getWriter().write("{\"code\":\"" + code + "\"}");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorBody> invalidInput(Exception exception) {
        return ResponseEntity.badRequest().body(new ErrorBody("invalid_request"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorBody> invalidCredentials(AuthenticationException exception) {
        return ResponseEntity.status(401).header("WWW-Authenticate", "Bearer")
                .body(new ErrorBody("unauthorized"));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorBody> unavailable(DataAccessException exception) {
        return ResponseEntity.status(503).body(new ErrorBody("service_unavailable"));
    }
}
