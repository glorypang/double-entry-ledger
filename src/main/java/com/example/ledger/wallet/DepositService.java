package com.example.ledger.wallet;

import com.example.ledger.ledger.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class DepositService {

    private final WalletRepository walletRepository;
    private final JournalTransactionRepository transactionRepository;
    private final LedgerEntryRepository entryRepository;

    public DepositService(
            WalletRepository walletRepository,
            JournalTransactionRepository transactionRepository,
            LedgerEntryRepository entryRepository
    ) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
    }

    @Transactional
    public void deposit(Long walletId, long amount) {
        if (amount <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "금액은 0보다 커야 합니다."
            );
        }

        Wallet wallet = walletRepository.findByIdForUpdate(walletId)                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "지갑을 찾을 수 없습니다."
                ));

        wallet.deposit(amount);

        JournalTransaction transaction = transactionRepository.save(
                new JournalTransaction(TransactionType.DEPOSIT)
        );

        LedgerEntry cashDebit = LedgerEntry.cash(
                transaction,
                EntryType.DEBIT,
                amount
        );

        LedgerEntry walletCredit = new LedgerEntry(
                transaction,
                wallet.getId(),
                EntryType.CREDIT,
                amount
        );

        entryRepository.saveAll(List.of(cashDebit, walletCredit));
    }
}