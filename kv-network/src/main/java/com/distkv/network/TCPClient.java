package com.distkv.network;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Lightweight, fluent TCP client for communicating with the Distributed Key-Value Store.
 * Implements {@link AutoCloseable} for idiomatic try-with-resources usage.
 */
public class TCPClient implements AutoCloseable {

    private final String host;
    private final int port;
    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;

    public TCPClient(String host, int port) throws IOException {
        this.host = host;
        this.port = port;
        connect();
    }

    public void connect() throws IOException {
        this.socket = new Socket(host, port);
        this.socket.setTcpNoDelay(true);
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    public synchronized String sendCommand(String commandLine) throws IOException {
        if (socket == null || socket.isClosed()) {
            throw new IOException("Socket is closed");
        }
        writer.write(commandLine + "\r\n");
        writer.flush();
        return reader.readLine();
    }

    private static String quote(String s) {
        if (s == null) return "";
        if (s.contains(" ") || s.contains("\t") || s.contains("\"")) {
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return s;
    }

    public boolean set(String key, String value) throws IOException {
        String response = sendCommand("SET " + quote(key) + " " + quote(value));
        return "OK".equalsIgnoreCase(response);
    }

    public String put(String key, String value) throws IOException {
        String response = sendCommand("PUT " + quote(key) + " " + quote(value));
        if (response != null && response.startsWith("VALUE ")) {
            return response.substring(6);
        }
        return null;
    }

    public String get(String key) throws IOException {
        String response = sendCommand("GET " + quote(key));
        if (response == null || "NOT_FOUND".equalsIgnoreCase(response)) {
            return null;
        }
        if (response.startsWith("VALUE ")) {
            return response.substring(6);
        }
        return null;
    }

    public boolean delete(String key) throws IOException {
        String response = sendCommand("DELETE " + quote(key));
        return "DELETED".equalsIgnoreCase(response);
    }

    public boolean exists(String key) throws IOException {
        String response = sendCommand("EXISTS " + quote(key));
        return "EXISTS 1".equalsIgnoreCase(response);
    }

    public Set<String> keys() throws IOException {
        String response = sendCommand("KEYS");
        if (response == null || "KEYS (empty)".equalsIgnoreCase(response)) {
            return Collections.emptySet();
        }
        if (response.startsWith("KEYS ")) {
            String[] parts = response.substring(5).split("\\s+");
            return new HashSet<>(Arrays.asList(parts));
        }
        return Collections.emptySet();
    }

    public String ping() throws IOException {
        return sendCommand("PING");
    }

    public String ping(String message) throws IOException {
        return sendCommand("PING " + message);
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public synchronized void close() throws IOException {
        try {
            if (socket != null && !socket.isClosed()) {
                sendCommand("QUIT");
            }
        } catch (IOException ignored) {
        } finally {
            if (reader != null) try { reader.close(); } catch (IOException ignored) {}
            if (writer != null) try { writer.close(); } catch (IOException ignored) {}
            if (socket != null) try { socket.close(); } catch (IOException ignored) {}
        }
    }
}
