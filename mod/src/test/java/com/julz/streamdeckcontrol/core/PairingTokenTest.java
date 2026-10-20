package com.julz.streamdeckcontrol.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PairingTokenTest {
    @Test
    void generatesReadableCodes() {
        for (int i = 0; i < 100; i++) {
            String token = PairingToken.generate();
            assertTrue(token.matches("[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}"), token);
        }
    }

    @Test
    void matchingIgnoresCaseSpacesAndDashes() {
        assertTrue(PairingToken.matches("K7QM-2XPA", "k7qm 2xpa"));
        assertTrue(PairingToken.matches("K7QM-2XPA", "K7QM2XPA"));
        assertFalse(PairingToken.matches("K7QM-2XPA", "K7QM-2XPB"));
        assertFalse(PairingToken.matches("K7QM-2XPA", null));
        assertFalse(PairingToken.matches("", ""));
    }
}
