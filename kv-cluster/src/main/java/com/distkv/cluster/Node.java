package com.distkv.cluster;

import java.util.Objects;

/**
 * Represents a physical node participating in the distributed key-value cluster.
 */
public class Node {

    public enum Status {
        ACTIVE,
        SUSPECT,
        OFFLINE
    }

    private final String nodeId;
    private final String host;
    private final int port;
    private volatile Status status;

    public Node(String nodeId, String host, int port) {
        this(nodeId, host, port, Status.ACTIVE);
    }

    public Node(String nodeId, String host, int port, Status status) {
        if (nodeId == null || nodeId.trim().isEmpty()) {
            throw new IllegalArgumentException("Node ID cannot be empty");
        }
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Host cannot be empty");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }

        this.nodeId = nodeId.trim();
        this.host = host.trim();
        this.port = port;
        this.status = status != null ? status : Status.ACTIVE;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public String getAddress() {
        return host + ":" + port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Node other)) return false;
        return port == other.port && Objects.equals(nodeId, other.nodeId) && Objects.equals(host, other.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId, host, port);
    }

    @Override
    public String toString() {
        return "Node{" + nodeId + "@" + host + ":" + port + " (" + status + ")}";
    }
}
