package dev.minkin.service;

import dev.minkin.controller.exceptions.DuplicateAccountException;
import dev.minkin.controller.exceptions.UnbalancedTransferException;
import dev.minkin.controller.exceptions.UnknownAccountException;
import dev.minkin.controller.exceptions.ZeroAmountException;
import dev.minkin.controller.types.CreateTransferRequest;
import dev.minkin.model.CreateTransferRequestEntryLine;
import dev.minkin.repository.LedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;

class TransferServiceTest {

    private LedgerRepository ledgerRepository;
    private TransferWriter transferWriter;
    private TransferService service;

    @BeforeEach
    void setUp() {
        ledgerRepository = Mockito.mock(LedgerRepository.class);
        transferWriter = Mockito.mock(TransferWriter.class);
        service = new TransferService(ledgerRepository, transferWriter);
    }

    @Test
    void createTransfer_shouldRejectZeroAmountEntries() {
        CreateTransferRequest request = new CreateTransferRequest(
                "REFUND",
                List.of(
                        new CreateTransferRequestEntryLine("cash", 0L),
                        new CreateTransferRequestEntryLine("bank", 0L)
                )
        );

        assertThrows(ZeroAmountException.class, () -> service.createTransfer("entryId-1", request));
    }

    @Test
    void createTransfer_shouldRejectDuplicateAccounts() {
        CreateTransferRequest request = new CreateTransferRequest(
                "TRANSFER",
                List.of(
                        new CreateTransferRequestEntryLine("acct-1", 100L),
                        new CreateTransferRequestEntryLine("acct-1", -100L)
                )
        );

        assertThrows(DuplicateAccountException.class, () -> service.createTransfer("entryId-2", request));
    }

    @Test
    void createTransfer_shouldRejectUnbalancedTransfers() {
        CreateTransferRequest request = new CreateTransferRequest(
                "TRANSFER",
                List.of(
                        new CreateTransferRequestEntryLine("acct-a", 100L),
                        new CreateTransferRequestEntryLine("acct-b", 25L)
                )
        );

        assertThrows(UnbalancedTransferException.class, () -> service.createTransfer("entryId-3", request));
    }

    @Test
    void createTransfer_shouldRejectUnknownAccounts() {
        when(ledgerRepository.resolveAccounts(anySet())).thenReturn(Map.of("acct-a", 1L));

        CreateTransferRequest request = new CreateTransferRequest(
                "TRANSFER",
                List.of(
                        new CreateTransferRequestEntryLine("acct-a", 50L),
                        new CreateTransferRequestEntryLine("acct-b", -50L)
                )
        );

        assertThrows(UnknownAccountException.class, () -> service.createTransfer("entryId-4", request));
    }

    @Test
    void createTransfer_shouldAcceptBalancedTransfersForKnownAccounts() {
        when(ledgerRepository.resolveAccounts(anySet())).thenReturn(Map.of(
                "acct-a", 1L,
                "acct-b", 2L
        ));

        CreateTransferRequest request = new CreateTransferRequest(
                "TRANSFER",
                List.of(
                        new CreateTransferRequestEntryLine("acct-a", 100L),
                        new CreateTransferRequestEntryLine("acct-b", -100L)
                )
        );

        assertDoesNotThrow(() -> service.createTransfer("entryId-5", request));
        assertNull(service.createTransfer("entryId-5", request));
    }
}
