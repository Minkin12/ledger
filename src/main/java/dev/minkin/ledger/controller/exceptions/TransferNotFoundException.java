package dev.minkin.ledger.controller.exceptions;

public class TransferNotFoundException extends LedgerException {
    public TransferNotFoundException(String key) {
        super("no transfer found for idempotency key: " + key);
    }
}
