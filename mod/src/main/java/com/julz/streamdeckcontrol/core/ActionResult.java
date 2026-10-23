package com.julz.streamdeckcontrol.core;

/** The outcome of one action request, sent back to the client as a {@code result} message. */
public record ActionResult(boolean ok, String error, String message) {
    public static final ActionResult OK = new ActionResult(true, null, null);

    public static ActionResult fail(String error, String message) {
        return new ActionResult(false, error, message);
    }
}
