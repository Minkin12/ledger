package dev.minkin.util;

import dev.minkin.controller.types.CreateTransferRequest;
import dev.minkin.model.CreateTransferRequestEntryLine;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class RequestHashTest {

    @Test
    void of_shouldSortEntriesByAccountIdBeforeHashing() throws Exception {
        CreateTransferRequest request = new CreateTransferRequest(
                "PAYMENT",
                List.of(
                        new CreateTransferRequestEntryLine("zzz", -50L),
                        new CreateTransferRequestEntryLine("aaa", 50L)
                )
        );

        String canonical = "PAYMENT|aaa:50|zzz:-50";
        byte[] expected = MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8));

        assertArrayEquals(expected, RequestHash.of(request));
    }
}
