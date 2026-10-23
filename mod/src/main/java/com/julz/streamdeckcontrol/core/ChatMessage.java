package com.julz.streamdeckcontrol.core;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fills a saved chat message's placeholders, such as "{coords}", and checks it fits in one chat line. */
public final class ChatMessage {
    /** Longest message the server accepts. */
    public static final int MAX_LENGTH = 256;

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z]+)}");

    private ChatMessage() {
    }

    /** Replaces each known {name} with its value; unknown placeholders stay as typed. */
    public static String fill(String template, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(value != null ? value : matcher.group()));
        }
        matcher.appendTail(out);
        return out.toString().strip();
    }

    /** Why the message can't be sent, or null when it can. */
    public static String problem(String message) {
        if (message.isEmpty()) {
            return "No message given";
        }
        if (message.startsWith("/")) {
            return "That's a command; use a Chat command key";
        }
        if (message.length() > MAX_LENGTH) {
            return "Too long for chat (" + message.length() + " of " + MAX_LENGTH + " characters)";
        }
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (c == '§' || c < ' ' || c == 127) {
                return "Chat doesn't allow formatting codes or control characters";
            }
        }
        return null;
    }
}
