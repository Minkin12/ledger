package dev.minkin.ledger.types;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateTransferRequestEntryLine(
        @NotBlank @Size(max = 64) String accountId,
        @NotNull Long amount
) {}
