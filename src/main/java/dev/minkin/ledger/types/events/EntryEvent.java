package dev.minkin.ledger.types.events;

public record EntryEvent(Long accountId,
                         Long entryId,
                         Long amount,
                         String eventType,
                         Integer version) implements Event {
}