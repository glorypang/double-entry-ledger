package com.example.ledger.ledger;

import jakarta.persistence.*;

@Entity
@Table(name = "journal_entry")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "transaction_id",
            nullable = false,
            updatable = false
    )
    private JournalTransaction transaction;

    @Column(updatable = false)
    private Long walletId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private EntryType entryType;

    @Column(nullable = false, updatable = false)
    private long amount;

    protected LedgerEntry() {
    }

    // 기존 송금 코드가 사용하는 고객 지갑 기록
    public LedgerEntry(
            JournalTransaction transaction,
            Long walletId,
            EntryType entryType,
            long amount
    ) {
        this(transaction, AccountType.WALLET, walletId, entryType, amount);
    }

    private LedgerEntry(
            JournalTransaction transaction,
            AccountType accountType,
            Long walletId,
            EntryType entryType,
            long amount
    ) {
        if (transaction == null || accountType == null || entryType == null) {
            throw new IllegalArgumentException("원장 기록 정보가 필요합니다.");
        }
        if (accountType == AccountType.WALLET && walletId == null) {
            throw new IllegalArgumentException("지갑 ID가 필요합니다.");
        }
        if (accountType == AccountType.CASH && walletId != null) {
            throw new IllegalArgumentException("현금 계정에는 지갑 ID를 사용하지 않습니다.");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("금액은 0보다 커야 합니다.");
        }

        this.transaction = transaction;
        this.accountType = accountType;
        this.walletId = walletId;
        this.entryType = entryType;
        this.amount = amount;
    }

    // 회사 현금 기록
    public static LedgerEntry cash(
            JournalTransaction transaction,
            EntryType entryType,
            long amount
    ) {
        return new LedgerEntry(
                transaction, AccountType.CASH, null, entryType, amount
        );
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AccountType accountType;
}