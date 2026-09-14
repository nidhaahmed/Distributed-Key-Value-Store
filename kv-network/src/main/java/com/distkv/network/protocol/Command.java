package com.distkv.network.protocol;

/**
 * Supported commands in the key-value store protocol.
 */
public enum Command {
    SET(2, 2, "SET <key> <value>"),
    GET(1, 1, "GET <key>"),
    DELETE(1, 1, "DELETE <key>"),
    DEL(1, 1, "DEL <key>"),
    EXISTS(1, 1, "EXISTS <key>"),
    KEYS(0, 0, "KEYS"),
    PING(0, 1, "PING [message]"),
    QUIT(0, 0, "QUIT"),
    INFO(0, 0, "INFO");

    private final int minArgs;
    private final int maxArgs;
    private final String syntax;

    Command(int minArgs, int maxArgs, String syntax) {
        this.minArgs = minArgs;
        this.maxArgs = maxArgs;
        this.syntax = syntax;
    }

    public int getMinArgs() {
        return minArgs;
    }

    public int getMaxArgs() {
        return maxArgs;
    }

    public String getSyntax() {
        return syntax;
    }

    public static Command fromString(String name) {
        if (name == null) return null;
        try {
            return Command.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
