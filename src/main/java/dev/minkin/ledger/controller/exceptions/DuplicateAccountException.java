package dev.minkin.ledger.controller.exceptions;

public class DuplicateAccountException extends LedgerException {
    public DuplicateAccountException(String accountId) {
        super("account appears more than once in one transfer: " + accountId);
    }
}
