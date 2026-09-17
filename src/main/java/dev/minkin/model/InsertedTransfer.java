package dev.minkin.model;

import java.time.OffsetDateTime;

public record InsertedTransfer(long id, OffsetDateTime createdAt) {
}