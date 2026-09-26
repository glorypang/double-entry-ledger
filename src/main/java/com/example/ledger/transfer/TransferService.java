package com.example.ledger.transfer;

import com.example.ledger.wallet.Wallet;
import com.example.ledger.wallet.WalletRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.example.ledger.ledger.EntryType;
import com.example.ledger.ledger.LedgerEntry;
import com.example.ledger.ledger.LedgerEntryRepository;
import java.util.List;
import com.example.ledger.ledger.JournalTransaction;
import com.example.ledger.ledger.JournalTransactionRepository;
import com.example.ledger.ledger.TransactionType;


@Service
public class TransferService {

    private final WalletRepository walletRepository;
    private final JournalTransactionRepository journalTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public TransferService(
            WalletRepository walletRepository,
            JournalTransactionRepository journalTransactionRepository,
            LedgerEntryRepository ledgerEntryRepository
    ) {
        this.walletRepository = walletRepository;
        this.journalTransactionRepository = journalTransactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public void transfer(TransferRequest request) {
        if (request.amount() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "금액은 0보다 커야 합니다."
            );
        }

        if (request.fromWalletId().equals(request.toWalletId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "같은 지갑으로 송금할 수 없습니다."
            );
        }

        Long fromId = request.fromWalletId();
        Long toId = request.toWalletId();

        Long firstId = Math.min(fromId, toId);
        Long secondId = Math.max(fromId, toId);

        Wallet first = findWalletForUpdate(firstId);
        Wallet second = findWalletForUpdate(secondId);

        Wallet from = fromId.equals(firstId) ? first : second;
        Wallet to = toId.equals(firstId) ? first : second;


        if (from.getBalance() < request.amount()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "잔액이 부족합니다."
            );
        }

        from.withdraw(request.amount());
        to.deposit(request.amount());

        JournalTransaction transaction = journalTransactionRepository.save(
                new JournalTransaction(TransactionType.TRANSFER)
        );

        LedgerEntry debit = new LedgerEntry(
                transaction,
                from.getId(),
                EntryType.DEBIT,
                request.amount()
        );

        LedgerEntry credit = new LedgerEntry(
                transaction,
                to.getId(),
                EntryType.CREDIT,
                request.amount()
        );

        ledgerEntryRepository.saveAll(List.of(debit, credit));
    }

    private Wallet findWalletForUpdate(Long id) {
        return walletRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "지갑을 찾을 수 없습니다."
                ));
    }
}