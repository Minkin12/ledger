package dev.minkin.controller;

import dev.minkin.controller.exceptions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class TransferControllerAdvice {

    private static final Logger log = LoggerFactory.getLogger(TransferControllerAdvice.class);

    @ExceptionHandler({
            UnbalancedTransferException.class,
            ZeroAmountException.class,
            DuplicateAccountException.class,
            UnknownAccountException.class,
            IdempotencyConflictException.class
    })
    ResponseEntity<String> onUnprocessable(LedgerException e) {
        return text(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<String> onMissingHeader(MissingRequestHeaderException e) {
        return text(HttpStatus.BAD_REQUEST, "missing required header: " + e.getHeaderName());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<String> onInvalidBody(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return text(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<String> onUnexpected(Exception e) {
        log.error("unhandled exception", e);
        return text(HttpStatus.INTERNAL_SERVER_ERROR, "internal error");
    }

    private static ResponseEntity<String> text(HttpStatus status, String body) {
        return ResponseEntity.status(status)
                .contentType(MediaType.TEXT_PLAIN)
                .body(body);
    }


}
