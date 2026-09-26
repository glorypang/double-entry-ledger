package com.example.ledger.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalTransactionRepository
        extends JpaRepository<JournalTransaction, Long> {
}