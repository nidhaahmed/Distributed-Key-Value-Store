package com.distkv.network.protocol;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses raw text input lines into structured {@link Request} instances.
 * Supports tokenization with quotation marks (e.g., SET msg "Hello World").
 */
public class RequestParser {

    /**
     * Parses an input line into a Request.
     *
     * @param line the raw line from the client socket
     * @return parsed Request
     * @throws MalformedRequestException if the line syntax is invalid or command arguments don't match
     */
    public static Request parse(String line) throws MalformedRequestException {
        if (line == null || line.trim().isEmpty()) {
            throw new MalformedRequestException("empty command");
        }

        List<String> tokens = tokenize(line.trim());
        if (tokens.isEmpty()) {
            throw new MalformedRequestException("empty command");
        }

        String cmdName = tokens.get(0);
        Command command = Command.fromString(cmdName);
        if (command == null) {
            throw new MalformedRequestException("unknown command '" + cmdName + "'");
        }

        List<String> args = tokens.subList(1, tokens.size());
        if (args.size() < command.getMinArgs() || args.size() > command.getMaxArgs()) {
            throw new MalformedRequestException(
                    "wrong number of arguments for '" + cmdName.toLowerCase() + "' command. Usage: " + command.getSyntax()
            );
        }

        return new Request(command, args);
    }

    /**
     * Tokenizes a line respecting single and double quotes.
     */
    public static List<String> tokenize(String input) throws MalformedRequestException {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inDoubleQuotes = false;
        boolean inSingleQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
                continue;
            }

            if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
                continue;
            }

            if (Character.isWhitespace(c) && !inDoubleQuotes && !inSingleQuotes) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        if (inDoubleQuotes || inSingleQuotes) {
            throw new MalformedRequestException("unclosed quotation mark in input");
        }

        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }

        return tokens;
    }
}
