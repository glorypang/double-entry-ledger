package com.example.ledger.wallet;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/wallets")
public class DepositController {

    private final DepositService depositService;

    public DepositController(DepositService depositService) {
        this.depositService = depositService;
    }

    @PostMapping("/{id}/deposits")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deposit(
            @PathVariable("id") Long id,
            @Valid @RequestBody DepositRequest request
    ) {
        depositService.deposit(id, request.amount());
    }
}