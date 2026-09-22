package dev.minkin.ledger.types;

public record ResolvedEntry(long internalAccountId, String externalId, long amount) {
}
