package com.distkv.cluster;

import com.distkv.core.KeyValueStore;
import com.distkv.network.TCPClient;
import com.distkv.network.protocol.Command;
import com.distkv.network.protocol.Request;
import com.distkv.network.protocol.ResponseWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes requests to the appropriate cluster node using consistent hashing.
 * Supports both transparent Proxy mode (forwarding request to owner) and Redirect mode (-MOVED).
 */
public class ClusterRouter implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ClusterRouter.class);

    public enum RoutingMode {
        PROXY,
        REDIRECT
    }

    private final Node selfNode;
    private final ConsistentHashRing hashRing;
    private final RoutingMode routingMode;
    private final Map<String, TCPClient> remoteClients = new ConcurrentHashMap<>();

    public ClusterRouter(Node selfNode, ConsistentHashRing hashRing) {
        this(selfNode, hashRing, RoutingMode.PROXY);
    }

    public ClusterRouter(Node selfNode, ConsistentHashRing hashRing, RoutingMode routingMode) {
        this.selfNode = selfNode;
        this.hashRing = hashRing;
        this.routingMode = routingMode != null ? routingMode : RoutingMode.PROXY;
    }

    /**
     * Determines whether the current node owns the specified key.
     */
    public boolean isLocalKey(String key) {
        if (selfNode == null) return true;
        Node owner = hashRing.getPrimaryNode(key);
        return owner == null || owner.equals(selfNode);
    }

    /**
     * Returns the physical node owning the specified key.
     */
    public Node getOwnerNode(String key) {
        return hashRing.getPrimaryNode(key);
    }

    /**
     * Executes a client request with cluster routing awareness.
     *
     * @param request    the parsed client request
     * @param localStore the local storage engine
     * @return response string to send to client
     */
    public String executeRoutedCommand(Request request, KeyValueStore localStore) {
        Command cmd = request.getCommand();

        // Non-key commands or global metadata executed locally
        if (cmd == Command.PING) {
            String msg = request.argCount() > 0 ? request.getArg(0) : null;
            return ResponseWriter.pong(msg);
        } else if (cmd == Command.QUIT) {
            return ResponseWriter.bye();
        } else if (cmd == Command.INFO) {
            return "INFO node=" + (selfNode != null ? selfNode.getNodeId() : "standalone")
                    + " keys=" + localStore.size()
                    + " cluster_size=" + hashRing.getNodeCount() + ResponseWriter.CRLF;
        } else if (cmd == Command.KEYS) {
            // In cluster mode, KEYS on one node returns keys owned by this node
            return ResponseWriter.keys(localStore.keys());
        }

        // Key-based commands: GET, SET, PUT, DEL, EXISTS
        String key = request.getArg(0);
        if (isLocalKey(key)) {
            return executeLocal(request, localStore);
        }

        // Remote routing needed
        Node targetNode = getOwnerNode(key);
        if (targetNode == null) {
            return ResponseWriter.error("no active node available in cluster ring");
        }

        if (routingMode == RoutingMode.REDIRECT) {
            return "-MOVED " + targetNode.getNodeId() + " " + targetNode.getAddress() + ResponseWriter.CRLF;
        }

        // Proxy mode: forward request to target node and return its response
        try {
            return forwardToNode(targetNode, request);
        } catch (IOException e) {
            log.error("Failed to proxy request for key '{}' to target node {}: {}", key, targetNode, e.getMessage());
            return ResponseWriter.error("cluster forward failure to node " + targetNode.getNodeId() + ": " + e.getMessage());
        }
    }

    private String executeLocal(Request request, KeyValueStore store) {
        Command cmd = request.getCommand();
        String key = request.getArg(0);

        switch (cmd) {
            case SET:
                store.set(key, request.getArg(1));
                return ResponseWriter.ok();

            case PUT:
                String oldVal = store.put(key, request.getArg(1));
                return oldVal != null ? "VALUE " + oldVal + ResponseWriter.CRLF : ResponseWriter.ok();

            case GET:
                String val = store.get(key);
                return ResponseWriter.value(val);

            case DELETE:
            case DEL:
                boolean deleted = store.delete(key);
                return deleted ? ResponseWriter.deleted() : ResponseWriter.notFound();

            case EXISTS:
                return ResponseWriter.exists(store.exists(key));

            default:
                return ResponseWriter.error("unsupported command in router: " + cmd);
        }
    }

    private String forwardToNode(Node targetNode, Request request) throws IOException {
        TCPClient client = getOrCreateClient(targetNode);
        StringBuilder rawCmd = new StringBuilder(request.getCommand().name());
        for (String arg : request.getArgs()) {
            rawCmd.append(" ");
            if (arg.contains(" ") || arg.contains("\"")) {
                rawCmd.append("\"").append(arg.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
            } else {
                rawCmd.append(arg);
            }
        }

        String line = client.sendCommand(rawCmd.toString());
        return (line != null ? line : "") + ResponseWriter.CRLF;
    }

    private synchronized TCPClient getOrCreateClient(Node targetNode) throws IOException {
        String key = targetNode.getAddress();
        TCPClient client = remoteClients.get(key);
        if (client == null || !client.isConnected()) {
            if (client != null) {
                try { client.close(); } catch (Exception ignored) {}
            }
            client = new TCPClient(targetNode.getHost(), targetNode.getPort());
            remoteClients.put(key, client);
        }
        return client;
    }

    public ConsistentHashRing getHashRing() {
        return hashRing;
    }

    public Node getSelfNode() {
        return selfNode;
    }

    public RoutingMode getRoutingMode() {
        return routingMode;
    }

    @Override
    public synchronized void close() {
        for (TCPClient client : remoteClients.values()) {
            try {
                client.close();
            } catch (Exception ignored) {}
        }
        remoteClients.clear();
    }
}
