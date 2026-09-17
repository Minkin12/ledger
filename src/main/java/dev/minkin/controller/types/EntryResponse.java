package dev.minkin.controller.types;

public record EntryResponse(
        long entryId,
        String accountId,
        long amount
) {
}
