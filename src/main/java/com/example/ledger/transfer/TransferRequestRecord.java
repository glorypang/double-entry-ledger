package com.example.ledger.transfer;

import jakarta.persistence.*;

@Entity
@Table(name = "transfer_request_record")
public class TransferRequestRecord {

    @Id
    @Column(name = "request_key", length = 128, nullable = false)
    private String requestKey;

    @Column(nullable = false)
    private Long fromWalletId;

    @Column(nullable = false)
    private Long toWalletId;

    @Column(nullable = false)
    private long amount;

    protected TransferRequestRecord() {
    }

    public TransferRequestRecord(String key, TransferRequest request) {
        this.requestKey = key;
        this.fromWalletId = request.fromWalletId();
        this.toWalletId = request.toWalletId();
        this.amount = request.amount();
    }

    public boolean matches(TransferRequest request) {
        return fromWalletId.equals(request.fromWalletId())
                && toWalletId.equals(request.toWalletId())
                && amount == request.amount();
    }
}