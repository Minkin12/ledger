package dev.minkin.ledger.controller.types;

public record EntryResponse(
        long entryId,
        String accountId,
        long amount
) {
}
