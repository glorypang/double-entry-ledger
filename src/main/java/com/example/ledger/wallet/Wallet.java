package com.example.ledger.wallet;

import jakarta.persistence.*;

@Entity
@Table(name = "wallet")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String ownerName;

    @Column(nullable = false)
    private long balance;

    protected Wallet() {
        // JPA가 객체를 만들 때 사용하는 기본 생성자
    }

    public Wallet(String ownerName) {
        this.ownerName = ownerName;
        this.balance = 0L;
    }

    public Long getId() {
        return id;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public long getBalance() {
        return balance;
    }

    public void withdraw(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("금액은 0보다 커야 합니다.");
        }
        if (balance < amount) {
            throw new IllegalArgumentException("잔액이 부족합니다.");
        }

        balance -= amount;
    }

    public void deposit(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("금액은 0보다 커야 합니다.");
        }

        balance = Math.addExact(balance, amount);
    }
}