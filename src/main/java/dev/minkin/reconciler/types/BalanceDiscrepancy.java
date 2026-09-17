package dev.minkin.reconciler.types;

public record BalanceDiscrepancy(Long accountId, Long computedBalance, Long storedBalance) {
}
