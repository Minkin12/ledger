package dev.minkin.ledger.types;

import java.time.OffsetDateTime;

public record InsertedTransfer(long id, OffsetDateTime createdAt) {
}