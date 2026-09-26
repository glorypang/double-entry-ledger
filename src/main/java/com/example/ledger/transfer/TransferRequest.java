package com.example.ledger.transfer;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TransferRequest(
        @NotNull Long fromWalletId,
        @NotNull Long toWalletId,
        @Positive long amount
) {
}