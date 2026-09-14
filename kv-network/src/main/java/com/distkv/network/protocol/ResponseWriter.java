package com.distkv.network.protocol;

import java.util.Collection;

/**
 * Serializes store responses into protocol text strings.
 */
public class ResponseWriter {

    public static final String CRLF = "\r\n";
    public static final String OK = "OK" + CRLF;
    public static final String DELETED = "DELETED" + CRLF;
    public static final String NOT_FOUND = "NOT_FOUND" + CRLF;
    public static final String PONG = "PONG" + CRLF;
    public static final String BYE = "BYE" + CRLF;

    public static String ok() {
        return OK;
    }

    public static String value(String value) {
        if (value == null) {
            return NOT_FOUND;
        }
        return "VALUE " + value + CRLF;
    }

    public static String deleted() {
        return DELETED;
    }

    public static String notFound() {
        return NOT_FOUND;
    }

    public static String exists(boolean exists) {
        return "EXISTS " + (exists ? "1" : "0") + CRLF;
    }

    public static String keys(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return "KEYS (empty)" + CRLF;
        }
        return "KEYS " + String.join(" ", keys) + CRLF;
    }

    public static String pong(String message) {
        if (message == null || message.trim().isEmpty()) {
            return PONG;
        }
        return "PONG " + message + CRLF;
    }

    public static String error(String message) {
        return "ERR " + (message != null ? message : "internal error") + CRLF;
    }

    public static String bye() {
        return BYE;
    }
}
