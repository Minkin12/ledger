package dev.minkin.ledger.controller.exceptions;

public abstract class LedgerException extends RuntimeException {
    protected LedgerException(String message) {
        super(message);
    }
}
