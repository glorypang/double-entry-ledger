package com.example.ledger.ledger;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "journal_transaction")
public class JournalTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TransactionType type;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected JournalTransaction() {
    }

    public JournalTransaction(TransactionType type) {
        if (type == null) {
            throw new IllegalArgumentException("거래 종류가 필요합니다.");
        }

        this.type = type;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }
}