package com.example.ledger.transfer;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferHistoryRepository
        extends JpaRepository<TransferHistory, Long> {
}