package com.example.ledger.wallet;

public record WalletResponse(
        Long id,
        String ownerName,
        long balance
) {
}