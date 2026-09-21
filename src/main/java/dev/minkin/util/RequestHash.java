package dev.minkin.util;

import dev.minkin.ledger.controller.types.CreateTransferRequest;
import dev.minkin.ledger.types.CreateTransferRequestEntryLine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;

public final class RequestHash {

    private RequestHash() {}

    public static byte[] of(CreateTransferRequest req) {
        StringBuilder sb = new StringBuilder(req.reason());
        req.entries().stream()
                .sorted(Comparator.comparing(CreateTransferRequestEntryLine::accountId))
                .forEach(l -> sb.append('|')
                        .append(l.accountId())
                        .append(':')
                        .append(l.amount()));
        return sha256(sb.toString());
    }

    private static byte[] sha256(String input) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
