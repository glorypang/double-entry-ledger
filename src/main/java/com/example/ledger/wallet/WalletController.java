package com.example.ledger.wallet;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/wallets")
public class WalletController {

    private final WalletRepository walletRepository;

    public WalletController(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WalletResponse create(
            @Valid @RequestBody CreateWalletRequest request
    ) {
        Wallet wallet = new Wallet(request.ownerName());
        Wallet savedWallet = walletRepository.save(wallet);

        return new WalletResponse(
                savedWallet.getId(),
                savedWallet.getOwnerName(),
                savedWallet.getBalance()
        );
    }
    @GetMapping("/{id}")
    public WalletResponse getWallet(@PathVariable("id") Long id) {
        Wallet wallet = walletRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "지갑을 찾을 수 없습니다."
                ));

        return new WalletResponse(
                wallet.getId(),
                wallet.getOwnerName(),
                wallet.getBalance()
        );
    }

}