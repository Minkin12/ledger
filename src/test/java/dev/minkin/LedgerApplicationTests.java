package dev.minkin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class LedgerApplicationTests {

    @Test
    void applicationClass_shouldBeConstructible() {
        assertDoesNotThrow(LedgerApplication::new);
    }
}
