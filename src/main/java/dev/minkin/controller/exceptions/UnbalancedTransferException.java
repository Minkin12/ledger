package dev.minkin.controller.exceptions;

public class UnbalancedTransferException extends LedgerException {
    private final long sum;

    public UnbalancedTransferException(long sum) {
        super("entries must sum to zero, got " + sum);
        this.sum = sum;
    }

    public long getSum() {
        return sum;
    }
}
