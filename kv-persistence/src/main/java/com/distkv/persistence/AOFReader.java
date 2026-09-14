package com.distkv.persistence;

import com.distkv.core.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Replays operations from an Append-Only File (AOF) to reconstruct database state.
 * Gracefully detects and handles partial/corrupted trailing log entries resulting from abrupt crashes.
 */
public class AOFReader {

    private static final Logger log = LoggerFactory.getLogger(AOFReader.class);

    /**
     * Replays the specified AOF file into the target store.
     *
     * @param aofFile the log file to replay
     * @param store   the store to restore into
     * @return recovery metrics
     */
    public static AOFRecoveryResult replay(File aofFile, KeyValueStore store) throws IOException {
        if (!aofFile.exists() || aofFile.length() == 0) {
            log.info("AOF file does not exist or is empty: {}", aofFile.getAbsolutePath());
            return new AOFRecoveryResult(0, 0, 0, true);
        }

        log.info("Starting AOF replay from {}", aofFile.getAbsolutePath());
        int lineCount = 0;
        int replayedOps = 0;
        int corruptedCount = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(aofFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineCount++;
                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    List<String> tokens = parseAofLine(line);
                    if (tokens.isEmpty()) {
                        continue;
                    }

                    String op = tokens.get(0).toUpperCase();
                    switch (op) {
                        case "SET":
                            if (tokens.size() != 3) {
                                throw new IllegalArgumentException("Corrupt SET entry: expected 3 tokens, got " + tokens.size());
                            }
                            store.set(tokens.get(1), tokens.get(2));
                            replayedOps++;
                            break;

                        case "DEL":
                        case "DELETE":
                            if (tokens.size() != 2) {
                                throw new IllegalArgumentException("Corrupt DEL entry: expected 2 tokens, got " + tokens.size());
                            }
                            store.delete(tokens.get(1));
                            replayedOps++;
                            break;

                        case "CLEAR":
                            store.clear();
                            replayedOps++;
                            break;

                        default:
                            throw new IllegalArgumentException("Unknown AOF mutation opcode: " + op);
                    }
                } catch (Exception e) {
                    corruptedCount++;
                    log.warn("Corrupted AOF entry on line {}: '{}' - Reason: {}", lineCount, line, e.getMessage());
                }
            }
        }

        log.info("AOF replay complete: processed {} lines, replayed {} operations, skipped {} corrupted entries",
                lineCount, replayedOps, corruptedCount);

        return new AOFRecoveryResult(lineCount, replayedOps, corruptedCount, true);
    }

    /**
     * Parses an AOF line into unescaped tokens.
     */
    public static List<String> parseAofLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (escaped) {
                switch (c) {
                    case 'n' -> current.append('\n');
                    case 'r' -> current.append('\r');
                    case 't' -> current.append('\t');
                    case '"' -> current.append('"');
                    case '\\' -> current.append('\\');
                    default -> current.append(c);
                }
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                continue;
            }

            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }

            if (Character.isWhitespace(c) && !inQuotes) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }

        if (inQuotes) {
            throw new IllegalArgumentException("Unterminated quotation in AOF line: " + line);
        }

        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }

        return tokens;
    }
}
