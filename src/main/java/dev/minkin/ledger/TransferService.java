package dev.minkin.ledger;

import dev.minkin.ledger.controller.exceptions.DuplicateAccountException;
import dev.minkin.ledger.controller.exceptions.UnbalancedTransferException;
import dev.minkin.ledger.controller.exceptions.UnknownAccountException;
import dev.minkin.ledger.controller.exceptions.ZeroAmountException;
import dev.minkin.ledger.controller.types.CreateTransferRequest;
import dev.minkin.ledger.controller.types.TransferResponse;
import dev.minkin.ledger.types.CreateTransferRequestEntryLine;
import dev.minkin.util.RequestHash;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class TransferService {
    private final LedgerRepository ledger;
    private final TransferWriter writer;

    public TransferService(LedgerRepository ledgerRepository, TransferWriter writer) {
        this.ledger = ledgerRepository;
        this.writer = writer;
    }

    public TransferResponse createTransfer(String idempotencyKey, CreateTransferRequest createTransferRequest) {

        // validate semantics of the request
        validateZeroSum(createTransferRequest);
        validateNoDuplicateAccounts(createTransferRequest);

        // resolve external ids -> internal
        Map<String, Long> accounts = ledger.resolveAccounts(externalIdsOf(createTransferRequest));
        requireAllResolved(accounts, createTransferRequest);

        byte[] hash = RequestHash.of(createTransferRequest);

        try {
            return writer.write(idempotencyKey, hash, createTransferRequest, accounts);
        } catch (DuplicateKeyException e) {
            return writer.replay(idempotencyKey, hash);
        }

    }

    private void validateZeroSum(CreateTransferRequest req) {
        long sum = 0;
        for (CreateTransferRequestEntryLine createTransferRequestEntryLine : req.entries()) {
            if (createTransferRequestEntryLine.amount() == 0) throw new ZeroAmountException(createTransferRequestEntryLine.accountId());
            sum = Math.addExact(sum, createTransferRequestEntryLine.amount());
        }
        if (sum != 0) throw new UnbalancedTransferException(sum);
    }

    private void validateNoDuplicateAccounts(CreateTransferRequest req) {
        Set<String> seen = new HashSet<>();
        for (CreateTransferRequestEntryLine createTransferRequestEntryLine : req.entries()) {
            if (!seen.add(createTransferRequestEntryLine.accountId())) throw new DuplicateAccountException(createTransferRequestEntryLine.accountId());
        }
    }

    private void requireAllResolved(Map<String, Long> resolved, CreateTransferRequest req) {
        Set<String> missing = externalIdsOf(req);
        missing.removeAll(resolved.keySet());
        if (!missing.isEmpty()) throw new UnknownAccountException(missing);
    }

    private Set<String> externalIdsOf(CreateTransferRequest req) {
        Set<String> externalIds = new HashSet<>();
        for  (CreateTransferRequestEntryLine createTransferRequestEntryLine : req.entries()) {
            externalIds.add(createTransferRequestEntryLine.accountId());
        }
        return externalIds;
    }
}
