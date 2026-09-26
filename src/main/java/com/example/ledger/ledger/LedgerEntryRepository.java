package com.example.ledger.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository
        extends JpaRepository<LedgerEntry, Long> {
}