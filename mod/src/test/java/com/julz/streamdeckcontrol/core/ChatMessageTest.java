package com.julz.streamdeckcontrol.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChatMessageTest {
    @Test
    void fillsKnownPlaceholders() {
        Map<String, String> values = Map.of("coords", "10 64 -3", "x", "10");
        assertEquals("I'm at 10 64 -3 (x 10)", ChatMessage.fill("I'm at {coords} (x {x})", values));
    }

    @Test
    void keepsUnknownPlaceholdersAndDollarSigns() {
        assertEquals("{nope} costs $5", ChatMessage.fill(" {nope} costs $5 ", Map.of()));
        assertEquals("a $1 b", ChatMessage.fill("a {x} b", Map.of("x", "$1")));
    }

    @Test
    void acceptsNormalMessages() {
        assertNull(ChatMessage.problem("gg"));
        assertNull(ChatMessage.problem("x".repeat(ChatMessage.MAX_LENGTH)));
    }

    @Test
    void rejectsWhatChatWouldRefuse() {
        assertNotNull(ChatMessage.problem(""));
        assertNotNull(ChatMessage.problem("/gamemode creative"));
        assertNotNull(ChatMessage.problem("x".repeat(ChatMessage.MAX_LENGTH + 1)));
        assertNotNull(ChatMessage.problem("§cred"));
        assertNotNull(ChatMessage.problem("line\nbreak"));
    }
}
