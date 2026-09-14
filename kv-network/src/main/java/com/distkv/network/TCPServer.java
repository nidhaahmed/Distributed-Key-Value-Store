package com.distkv.network;

import com.distkv.core.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Multi-threaded TCP server listening for client key-value requests.
 */
public class TCPServer {

    private static final Logger log = LoggerFactory.getLogger(TCPServer.class);

    private final String host;
    private int port;
    private final KeyValueStore store;
    private final int threadPoolSize;

    private ServerSocket serverSocket;
    private ExecutorService clientExecutor;
    private Thread acceptThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public TCPServer(int port, KeyValueStore store) {
        this("0.0.0.0", port, store, Math.max(8, Runtime.getRuntime().availableProcessors() * 4));
    }

    public TCPServer(String host, int port, KeyValueStore store, int threadPoolSize) {
        this.host = host;
        this.port = port;
        this.store = store;
        this.threadPoolSize = threadPoolSize;
    }

    /**
     * Starts the TCP server synchronously.
     */
    public synchronized void start() throws IOException {
        if (running.get()) {
            return;
        }

        this.serverSocket = new ServerSocket();
        this.serverSocket.setReuseAddress(true);
        this.serverSocket.bind(new InetSocketAddress(host, port));
        this.port = serverSocket.getLocalPort(); // In case ephemeral port (0) was passed

        this.clientExecutor = new ThreadPoolExecutor(
                threadPoolSize / 2,
                threadPoolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                new ThreadFactory() {
                    private int count = 0;
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "kv-client-" + (++count));
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        this.running.set(true);

        this.acceptThread = new Thread(this::acceptLoop, "kv-acceptor-" + port);
        this.acceptThread.start();

        log.info("TCPServer started and listening on {}:{}", host, port);
    }

    private void acceptLoop() {
        while (running.get() && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                clientSocket.setTcpNoDelay(true);
                clientSocket.setKeepAlive(true);

                clientExecutor.submit(new ClientHandler(clientSocket, store));
            } catch (IOException e) {
                if (!running.get() || serverSocket.isClosed()) {
                    break;
                }
                log.warn("Error accepting connection: {}", e.getMessage());
            }
        }
    }

    /**
     * Gracefully stops the server.
     */
    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        log.info("Stopping TCPServer on port {}", port);

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.warn("Error closing server socket: {}", e.getMessage());
        }

        if (clientExecutor != null) {
            clientExecutor.shutdown();
            try {
                if (!clientExecutor.awaitTermination(3, TimeUnit.SECONDS)) {
                    clientExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                clientExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (acceptThread != null) {
            try {
                acceptThread.join(2000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        log.info("TCPServer on port {} stopped successfully", port);
    }

    public int getPort() {
        return port;
    }

    public boolean isRunning() {
        return running.get();
    }
}
