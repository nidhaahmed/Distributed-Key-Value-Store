package com.distkv.server;

import com.distkv.core.ConcurrentSegmentedStore;
import com.distkv.core.KeyValueStore;
import com.distkv.core.LRUCache;
import com.distkv.network.TCPServer;
import com.distkv.persistence.FsyncPolicy;
import com.distkv.persistence.PersistentKeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Main application bootstrap for the Distributed Key-Value Store node.
 */
public class KVServerApp {

    private static final Logger log = LoggerFactory.getLogger(KVServerApp.class);

    private final ServerConfig config;
    private KeyValueStore store;
    private TCPServer tcpServer;

    public KVServerApp(ServerConfig config) {
        this.config = config;
    }

    public synchronized void start() throws IOException {
        printBanner();

        // 1. Initialize Base In-Memory Storage Engine
        if (config.isLruEnabled()) {
            log.info("Initializing LRU Cache storage (capacity: {})", config.getLruCapacity());
            this.store = new LRUCache(config.getLruCapacity());
        } else {
            log.info("Initializing Concurrent Segmented Hash Store (32 segments)");
            this.store = new ConcurrentSegmentedStore(32, 16);
        }

        // 2. Wrap with Append-Only File Persistence if enabled
        if (config.isPersistenceEnabled()) {
            File aofFile = new File(config.getAofFilePath());
            log.info("Attaching AOF persistence engine: path={}, policy={}", aofFile.getAbsolutePath(), config.getFsyncPolicy());
            this.store = PersistentKeyValueStore.create(store, aofFile, config.getFsyncPolicy());
        }

        // 3. Start Multi-Threaded TCP Server
        this.tcpServer = new TCPServer(config.getHost(), config.getPort(), store, config.getThreadPoolSize());
        this.tcpServer.start();

        // 4. Register JVM Graceful Shutdown Hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "kv-shutdown-hook"));

        log.info("Distributed Key-Value Store node is ready to accept client connections on port {}", config.getPort());
    }

    public synchronized void stop() {
        log.info("Initiating graceful server shutdown...");
        if (tcpServer != null) {
            tcpServer.stop();
        }
        if (store instanceof AutoCloseable autoCloseable) {
            try {
                autoCloseable.close();
            } catch (Exception e) {
                log.warn("Error closing persistent store during shutdown: {}", e.getMessage());
            }
        }
        log.info("Server shutdown complete.");
    }

    public KeyValueStore getStore() {
        return store;
    }

    public TCPServer getTcpServer() {
        return tcpServer;
    }

    private void printBanner() {
        System.out.println("""
                 =============================================================
                 *             DISTRIBUTED KEY-VALUE STORE                   *
                 *        In-Memory Engine | TCP Networking | AOF Durability *
                 =============================================================
                """);
    }

    public static void main(String[] args) {
        ServerConfig config = new ServerConfig();

        // Optional CLI argument parsing: --port 7379 --aof data/store.aof --fsync ALWAYS --lru 50000
        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                config.setPort(Integer.parseInt(args[++i]));
            } else if ("--host".equals(args[i]) && i + 1 < args.length) {
                config.setHost(args[++i]);
            } else if ("--aof".equals(args[i]) && i + 1 < args.length) {
                config.setAofFilePath(args[++i]);
            } else if ("--fsync".equals(args[i]) && i + 1 < args.length) {
                config.setFsyncPolicy(FsyncPolicy.valueOf(args[++i].toUpperCase()));
            } else if ("--lru".equals(args[i]) && i + 1 < args.length) {
                config.setLruEnabled(true);
                config.setLruCapacity(Integer.parseInt(args[++i]));
            } else if ("--no-aof".equals(args[i])) {
                config.setPersistenceEnabled(false);
            }
        }

        try {
            KVServerApp app = new KVServerApp(config);
            app.start();
        } catch (Exception e) {
            log.error("Fatal error starting server: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
