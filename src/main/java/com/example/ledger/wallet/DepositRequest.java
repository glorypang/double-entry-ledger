package com.example.ledger.wallet;

import jakarta.validation.constraints.Positive;

public record DepositRequest(
        @Positive long amount
) {
}