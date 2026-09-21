package dev.minkin.ledger.controller.types;

import java.time.OffsetDateTime;
import java.util.List;

public record TransferResponse(
        long transferId,
        String reason,
        OffsetDateTime createdAt,
        List<EntryResponse> entries
) {}
