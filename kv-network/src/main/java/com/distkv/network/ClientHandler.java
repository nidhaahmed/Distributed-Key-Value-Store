package com.distkv.network;

import com.distkv.core.KeyValueStore;
import com.distkv.network.protocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

/**
 * Handles communication with a connected TCP client socket.
 * Executes protocol commands sequentially per connection.
 */
public class ClientHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);

    private final Socket socket;
    private final KeyValueStore store;
    private volatile boolean running = true;

    public ClientHandler(Socket socket, KeyValueStore store) {
        this.socket = socket;
        this.store = store;
    }

    @Override
    public void run() {
        log.debug("Client connected: {}", socket.getRemoteSocketAddress());
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
        ) {
            String line;
            while (running && (line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    Request request = RequestParser.parse(line);
                    String response = executeCommand(request);
                    writer.write(response);
                    writer.flush();

                    if (request.getCommand() == Command.QUIT) {
                        break;
                    }
                } catch (MalformedRequestException e) {
                    writer.write(ResponseWriter.error(e.getMessage()));
                    writer.flush();
                }
            }
        } catch (SocketException e) {
            log.debug("Socket closed or reset for client: {}", socket.getRemoteSocketAddress());
        } catch (IOException e) {
            log.error("I/O error handling client: {}", socket.getRemoteSocketAddress(), e);
        } finally {
            closeSocket();
            log.debug("Client disconnected: {}", socket.getRemoteSocketAddress());
        }
    }

    private String executeCommand(Request request) {
        Command cmd = request.getCommand();
        switch (cmd) {
            case SET: {
                String key = request.getArg(0);
                String value = request.getArg(1);
                store.set(key, value);
                return ResponseWriter.ok();
            }
            case GET: {
                String key = request.getArg(0);
                String val = store.get(key);
                return ResponseWriter.value(val);
            }
            case DELETE:
            case DEL: {
                String key = request.getArg(0);
                boolean deleted = store.delete(key);
                return deleted ? ResponseWriter.deleted() : ResponseWriter.notFound();
            }
            case EXISTS: {
                String key = request.getArg(0);
                return ResponseWriter.exists(store.exists(key));
            }
            case KEYS: {
                return ResponseWriter.keys(store.keys());
            }
            case PING: {
                String msg = request.argCount() > 0 ? request.getArg(0) : null;
                return ResponseWriter.pong(msg);
            }
            case QUIT: {
                return ResponseWriter.bye();
            }
            case INFO: {
                return "INFO keys=" + store.size() + ResponseWriter.CRLF;
            }
            default:
                return ResponseWriter.error("unimplemented command " + cmd);
        }
    }

    public void stop() {
        this.running = false;
        closeSocket();
    }

    private void closeSocket() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}
    }
}
