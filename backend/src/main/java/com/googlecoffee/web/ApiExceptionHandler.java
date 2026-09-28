package com.googlecoffee.web;

import org.slf4j.Logger;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    public record ErrorBody(String error, String message) {}

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorBody> api(ApiException e, HttpServletRequest request) {
        // An EventSource only accepts text/event-stream, so a JSON error body can't be written.
        // Send the status alone; the browser sees the stream fail and the UI checks via REST.
        if (acceptsOnlyEventStream(request)) return ResponseEntity.status(e.status()).build();
        return ResponseEntity.status(e.status()).body(new ErrorBody(e.status().name(), e.getMessage()));
    }

    private static boolean acceptsOnlyEventStream(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/event-stream") && !accept.contains("json");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorBody> invalid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .orElse("Invalid request");
        return ResponseEntity.badRequest().body(new ErrorBody("BAD_REQUEST", msg));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            HandlerMethodValidationException.class})
    public ResponseEntity<ErrorBody> badInput(Exception e) {
        return ResponseEntity.badRequest().body(new ErrorBody("BAD_REQUEST", "Request is missing or malformed"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorBody> notFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorBody("NOT_FOUND", "Not found"));
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void clientGone() {
        // SSE client disconnected; nothing to send.
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> unexpected(Exception e) {
        log.error("Unhandled error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorBody("INTERNAL_ERROR", "Something went wrong. Please try again."));
    }
}