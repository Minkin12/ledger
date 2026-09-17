package dev.minkin.controller.exceptions;

public class IdempotencyConflictException extends LedgerException {
    public IdempotencyConflictException(String key) {
        super("idempotency key reused with a different request body: " + key);
    }
}
