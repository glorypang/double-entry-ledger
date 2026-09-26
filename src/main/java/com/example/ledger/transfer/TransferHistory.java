package com.example.ledger.transfer;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "transfer_history")
public class TransferHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long fromWalletId;

    @Column(nullable = false, updatable = false)
    private Long toWalletId;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected TransferHistory() {
    }

    public TransferHistory(
            Long fromWalletId,
            Long toWalletId,
            long amount
    ) {
        this.fromWalletId = fromWalletId;
        this.toWalletId = toWalletId;
        this.amount = amount;
        this.createdAt = Instant.now();
    }
}