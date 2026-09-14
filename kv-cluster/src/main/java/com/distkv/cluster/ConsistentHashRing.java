package com.distkv.cluster;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Consistent Hash Ring implementation with Virtual Nodes.
 * Partitions key spaces across physical cluster nodes using logarithmic TreeMap lookup.
 */
public class ConsistentHashRing {

    public static final int DEFAULT_VIRTUAL_NODES = 150;

    private final HashFunction hashFunction;
    private final int virtualNodes;
    private final NavigableMap<Long, Node> ring;
    private final Set<Node> physicalNodes;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public ConsistentHashRing() {
        this(new HashFunction.Murmur3(), DEFAULT_VIRTUAL_NODES);
    }

    public ConsistentHashRing(int virtualNodes) {
        this(new HashFunction.Murmur3(), virtualNodes);
    }

    public ConsistentHashRing(HashFunction hashFunction, int virtualNodes) {
        if (virtualNodes <= 0) {
            throw new IllegalArgumentException("Virtual nodes count must be positive: " + virtualNodes);
        }
        this.hashFunction = hashFunction != null ? hashFunction : new HashFunction.Murmur3();
        this.virtualNodes = virtualNodes;
        this.ring = new TreeMap<>();
        this.physicalNodes = new HashSet<>();
    }

    /**
     * Adds a physical node to the hash ring by generating tokens for its virtual nodes.
     */
    public void addNode(Node node) {
        if (node == null) return;
        lock.writeLock().lock();
        try {
            physicalNodes.add(node);
            for (int i = 0; i < virtualNodes; i++) {
                long token = hashFunction.hash(node.getNodeId() + "#vn" + i);
                ring.put(token, node);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Removes a physical node and all its virtual tokens from the ring.
     */
    public void removeNode(Node node) {
        if (node == null) return;
        lock.writeLock().lock();
        try {
            physicalNodes.remove(node);
            for (int i = 0; i < virtualNodes; i++) {
                long token = hashFunction.hash(node.getNodeId() + "#vn" + i);
                ring.remove(token);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Finds the primary node responsible for the given key.
     *
     * @param key the key to route
     * @return the owning Node, or null if the ring is empty
     */
    public Node getPrimaryNode(String key) {
        if (key == null) return null;
        lock.readLock().lock();
        try {
            if (ring.isEmpty()) {
                return null;
            }
            long hash = hashFunction.hash(key);
            Map.Entry<Long, Node> entry = ring.ceilingEntry(hash);
            if (entry == null) {
                // Wrap around to the start of the ring
                entry = ring.firstEntry();
            }
            return entry.getValue();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Returns the preference list of distinct physical nodes responsible for a key.
     * Traverses the ring clockwise starting from the key's token position.
     * Used for primary-replica placement and quorum routing.
     *
     * @param key   the key
     * @param count number of distinct replica nodes requested (e.g. replication factor N)
     * @return list of distinct physical nodes
     */
    public List<Node> getPreferenceList(String key, int count) {
        if (key == null || count <= 0) return Collections.emptyList();
        lock.readLock().lock();
        try {
            if (ring.isEmpty()) {
                return Collections.emptyList();
            }

            int targetCount = Math.min(count, physicalNodes.size());
            List<Node> result = new ArrayList<>(targetCount);
            Set<String> seenIds = new HashSet<>();

            long hash = hashFunction.hash(key);

            // Tail map from key's hash position to ring end
            NavigableMap<Long, Node> tailMap = ring.tailMap(hash, true);
            for (Node node : tailMap.values()) {
                if (node.isActive() && seenIds.add(node.getNodeId())) {
                    result.add(node);
                    if (result.size() == targetCount) {
                        return result;
                    }
                }
            }

            // Head map wrapping around from ring start
            for (Node node : ring.values()) {
                if (node.isActive() && seenIds.add(node.getNodeId())) {
                    result.add(node);
                    if (result.size() == targetCount) {
                        return result;
                    }
                }
            }

            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    public Set<Node> getPhysicalNodes() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableSet(new HashSet<>(physicalNodes));
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getNodeCount() {
        lock.readLock().lock();
        try {
            return physicalNodes.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getRingSize() {
        lock.readLock().lock();
        try {
            return ring.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getVirtualNodes() {
        return virtualNodes;
    }
}
