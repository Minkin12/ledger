package dev.minkin.controller.exceptions;

import java.util.Collection;

public class UnknownAccountException extends LedgerException {
    public UnknownAccountException(Collection<String> accountIds) {
        super("no such account: " + String.join(", ", accountIds));
    }
}
