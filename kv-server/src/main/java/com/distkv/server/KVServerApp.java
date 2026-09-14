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
    private com.distkv.cluster.ClusterRouter clusterRouter;

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

        // 3. Setup Cluster Router if cluster mode is enabled
        java.util.function.Function<com.distkv.network.protocol.Request, String> commandExecutor = null;
        if (config.isClusterEnabled() && config.getClusterNodes() != null) {
            log.info("Configuring cluster mode for node '{}'", config.getNodeId());
            com.distkv.cluster.ConsistentHashRing ring = new com.distkv.cluster.ConsistentHashRing(config.getVirtualNodes());
            com.distkv.cluster.Node selfNode = null;

            String[] nodeDefs = config.getClusterNodes().split(",");
            for (String def : nodeDefs) {
                String[] parts = def.trim().split("@");
                if (parts.length == 2) {
                    String id = parts[0].trim();
                    String[] addr = parts[1].trim().split(":");
                    com.distkv.cluster.Node node = new com.distkv.cluster.Node(id, addr[0].trim(), Integer.parseInt(addr[1].trim()));
                    ring.addNode(node);
                    if (id.equalsIgnoreCase(config.getNodeId())) {
                        selfNode = node;
                    }
                }
            }

            if (selfNode == null) {
                selfNode = new com.distkv.cluster.Node(config.getNodeId(), config.getHost(), config.getPort());
                ring.addNode(selfNode);
            }

            com.distkv.cluster.ClusterRouter.RoutingMode mode = "REDIRECT".equalsIgnoreCase(config.getRoutingMode())
                    ? com.distkv.cluster.ClusterRouter.RoutingMode.REDIRECT
                    : com.distkv.cluster.ClusterRouter.RoutingMode.PROXY;

            this.clusterRouter = new com.distkv.cluster.ClusterRouter(selfNode, ring, mode);
            commandExecutor = req -> clusterRouter.executeRoutedCommand(req, store);
            log.info("Cluster router initialized: self={}, mode={}, clusterSize={}", selfNode, mode, ring.getNodeCount());
        }

        // 4. Start Multi-Threaded TCP Server
        this.tcpServer = new TCPServer(config.getHost(), config.getPort(), store, config.getThreadPoolSize(), commandExecutor);
        this.tcpServer.start();

        // 5. Register JVM Graceful Shutdown Hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "kv-shutdown-hook"));

        log.info("Distributed Key-Value Store node is ready to accept client connections on port {}", config.getPort());
    }

    public synchronized void stop() {
        log.info("Initiating graceful server shutdown...");
        if (clusterRouter != null) {
            try {
                clusterRouter.close();
            } catch (Exception e) {
                log.warn("Error closing cluster router: {}", e.getMessage());
            }
        }
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
            } else if ("--cluster".equals(args[i])) {
                config.setClusterEnabled(true);
            } else if ("--node-id".equals(args[i]) && i + 1 < args.length) {
                config.setNodeId(args[++i]);
            } else if ("--cluster-nodes".equals(args[i]) && i + 1 < args.length) {
                config.setClusterNodes(args[++i]);
                config.setClusterEnabled(true);
            } else if ("--routing-mode".equals(args[i]) && i + 1 < args.length) {
                config.setRoutingMode(args[++i]);
            } else if ("--vnodes".equals(args[i]) && i + 1 < args.length) {
                config.setVirtualNodes(Integer.parseInt(args[++i]));
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
