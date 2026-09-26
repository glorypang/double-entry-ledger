package com.example.ledger.wallet;

import jakarta.validation.constraints.NotBlank;

public record CreateWalletRequest(
        @NotBlank String ownerName
) {
}