package dev.minkin.ledger.controller;

import dev.minkin.ledger.controller.types.CreateTransferRequest;
import dev.minkin.ledger.controller.types.TransferResponse;
import dev.minkin.ledger.TransferService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Profile("!projector")
@RestController
@RequestMapping("api/v1")
public class TransferController {
    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransferResponse> transfer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid CreateTransferRequest createTransferRequest) {

        TransferResponse response = transferService.createTransfer(idempotencyKey, createTransferRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);

    }


}
