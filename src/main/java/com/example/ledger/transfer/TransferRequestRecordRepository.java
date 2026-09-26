package com.example.ledger.transfer;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRequestRecordRepository
        extends JpaRepository<TransferRequestRecord, String> {
}