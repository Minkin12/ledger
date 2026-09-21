package dev.minkin.ledger.types;

import java.util.UUID;

public record OutboxDto(UUID eventId, String subject, String payload) {
}
