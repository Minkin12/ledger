package dev.minkin.ledger.types;

import java.time.OffsetDateTime;

public record TransferDto(Long id,
                          String idempotencyKey,
                          byte[] requestHash,
                          String reason,
                          OffsetDateTime createdAt) {
}
