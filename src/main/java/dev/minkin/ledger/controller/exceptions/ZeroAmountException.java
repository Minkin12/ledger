package dev.minkin.ledger.controller.exceptions;

public class ZeroAmountException extends LedgerException {
    public ZeroAmountException(String accountId) {
        super("entry amount must not be zero, account: " + accountId);
    }
}
