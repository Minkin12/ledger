package dev.minkin.ledger.types;

public record ResolvedEntry(long accountId, String externalId, long amount) {
}
