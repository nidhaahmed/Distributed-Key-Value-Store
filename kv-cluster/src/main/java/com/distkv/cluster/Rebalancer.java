package com.distkv.cluster;

import com.distkv.core.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Manages partition rebalancing and key migrations during dynamic cluster topology changes
 * (node additions and node removals) based on consistent hashing.
 */
public class Rebalancer {

    private static final Logger log = LoggerFactory.getLogger(Rebalancer.class);

    public static class RebalanceReport {
        private final int totalKeysScanned;
        private final int keysMigrated;
        private final Map<String, Integer> migrationCountsByNode;

        public RebalanceReport(int totalKeysScanned, int keysMigrated, Map<String, Integer> migrationCountsByNode) {
            this.totalKeysScanned = totalKeysScanned;
            this.keysMigrated = keysMigrated;
            this.migrationCountsByNode = migrationCountsByNode;
        }

        public int getTotalKeysScanned() {
            return totalKeysScanned;
        }

        public int getKeysMigrated() {
            return keysMigrated;
        }

        public double getMigrationPercentage() {
            return totalKeysScanned == 0 ? 0.0 : (keysMigrated * 100.0) / totalKeysScanned;
        }

        public Map<String, Integer> getMigrationCountsByNode() {
            return migrationCountsByNode;
        }

        @Override
        public String toString() {
            return String.format("RebalanceReport[scanned=%d, migrated=%d (%.2f%%), byNode=%s]",
                    totalKeysScanned, keysMigrated, getMigrationPercentage(), migrationCountsByNode);
        }
    }

    /**
     * Rebalances keys from existing node stores to the new node when a new node joins the cluster.
     * Only keys whose consistent hash ownership moves to the newNode are migrated.
     *
     * @param ring      the updated consistent hash ring (with newNode already added)
     * @param newNode   the newly joined node
     * @param nodeStores mapping of active nodes to their local KeyValueStore instances
     * @return report detailing key migration metrics
     */
    public static RebalanceReport rebalanceOnNodeJoin(ConsistentHashRing ring, Node newNode, Map<Node, KeyValueStore> nodeStores) {
        log.info("Starting cluster rebalancing for joining node: {}", newNode.getNodeId());
        int totalScanned = 0;
        int totalMigrated = 0;
        Map<String, Integer> migrationCounts = new HashMap<>();

        KeyValueStore newStore = nodeStores.get(newNode);
        if (newStore == null) {
            throw new IllegalArgumentException("Target store for newNode cannot be null");
        }

        for (Map.Entry<Node, KeyValueStore> entry : nodeStores.entrySet()) {
            Node existingNode = entry.getKey();
            if (existingNode.equals(newNode)) {
                continue;
            }

            KeyValueStore existingStore = entry.getValue();
            Set<String> keys = new HashSet<>(existingStore.keys());
            totalScanned += keys.size();

            int migratedFromNode = 0;
            for (String key : keys) {
                Node currentOwner = ring.getPrimaryNode(key);
                if (newNode.equals(currentOwner)) {
                    // Key ownership has transitioned to the new node
                    String val = existingStore.get(key);
                    newStore.set(key, val);
                    existingStore.delete(key);
                    totalMigrated++;
                    migratedFromNode++;
                }
            }
            migrationCounts.put(existingNode.getNodeId() + "->" + newNode.getNodeId(), migratedFromNode);
        }

        RebalanceReport report = new RebalanceReport(totalScanned, totalMigrated, migrationCounts);
        log.info("Node join rebalancing completed: {}", report);
        return report;
    }

    /**
     * Rebalances keys when a node leaves or is decommissioned from the cluster.
     * Migrates all keys owned by leavingNode to their new primary owners according to the updated ring.
     *
     * @param updatedRing the ring after leavingNode has been removed
     * @param leavingNode the decommissioned node
     * @param leavingStore the store containing leavingNode's keys
     * @param survivingStores mapping of surviving nodes to their stores
     * @return report detailing migration metrics
     */
    public static RebalanceReport rebalanceOnNodeLeave(ConsistentHashRing updatedRing, Node leavingNode,
                                                      KeyValueStore leavingStore, Map<Node, KeyValueStore> survivingStores) {
        log.info("Starting cluster rebalancing for leaving node: {}", leavingNode.getNodeId());
        Set<String> keys = new HashSet<>(leavingStore.keys());
        int totalScanned = keys.size();
        int totalMigrated = 0;
        Map<String, Integer> migrationCounts = new HashMap<>();

        for (String key : keys) {
            Node newOwner = updatedRing.getPrimaryNode(key);
            if (newOwner != null && survivingStores.containsKey(newOwner)) {
                String val = leavingStore.get(key);
                survivingStores.get(newOwner).set(key, val);
                leavingStore.delete(key);
                totalMigrated++;
                migrationCounts.merge(leavingNode.getNodeId() + "->" + newOwner.getNodeId(), 1, Integer::sum);
            }
        }

        RebalanceReport report = new RebalanceReport(totalScanned, totalMigrated, migrationCounts);
        log.info("Node leave rebalancing completed: {}", report);
        return report;
    }
}
