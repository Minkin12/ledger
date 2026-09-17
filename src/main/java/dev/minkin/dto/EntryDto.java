package dev.minkin.dto;

import java.time.OffsetDateTime;

public record EntryDto(Long id,
                       Long transferId,
                       Long accountId,
                       Long amount,
                       OffsetDateTime createdAt) {
}
