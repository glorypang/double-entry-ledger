package com.example.ledger.transfer;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final IdempotentTransferService transferService;

    public TransferController(IdempotentTransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void transfer(
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody TransferRequest request
    ) {
        transferService.transfer(key, request);
    }
}