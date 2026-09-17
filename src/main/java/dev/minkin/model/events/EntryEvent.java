package dev.minkin.model.events;

public record EntryEvent(Long accountId,
                         Long entryId,
                         Long amount,
                         String eventType,
                         Integer version) implements Event {
}