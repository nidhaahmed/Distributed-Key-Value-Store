package com.distkv.cluster;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ConsistentHashRingTest {

    @Test
    @DisplayName("Consistent hash ring basic node addition, removal, and lookup")
    void testBasicRingOperations() {
        ConsistentHashRing ring = new ConsistentHashRing(100);

        Node nodeA = new Node("node-A", "127.0.0.1", 7001);
        Node nodeB = new Node("node-B", "127.0.0.1", 7002);
        Node nodeC = new Node("node-C", "127.0.0.1", 7003);

        ring.addNode(nodeA);
        ring.addNode(nodeB);
        ring.addNode(nodeC);

        assertEquals(3, ring.getNodeCount());
        assertEquals(300, ring.getRingSize());

        Node owner1 = ring.getPrimaryNode("user:1001");
        assertNotNull(owner1);
        assertTrue(ring.getPhysicalNodes().contains(owner1));

        // Same key always maps to the same node deterministically
        assertEquals(owner1, ring.getPrimaryNode("user:1001"));

        // Remove a node
        ring.removeNode(nodeB);
        assertEquals(2, ring.getNodeCount());
        assertEquals(200, ring.getRingSize());

        Node ownerAfterRemoval = ring.getPrimaryNode("user:1001");
        assertNotNull(ownerAfterRemoval);
        assertNotEquals("node-B", ownerAfterRemoval.getNodeId());
    }

    @Test
    @DisplayName("Virtual nodes provide uniform key distribution across cluster")
    void testKeyDistributionUniformity() {
        ConsistentHashRing ring = new ConsistentHashRing(150);

        Node node1 = new Node("node-1", "127.0.0.1", 7001);
        Node node2 = new Node("node-2", "127.0.0.1", 7002);
        Node node3 = new Node("node-3", "127.0.0.1", 7003);

        ring.addNode(node1);
        ring.addNode(node2);
        ring.addNode(node3);

        int totalKeys = 15000;
        Map<String, Integer> counts = new HashMap<>();

        for (int i = 0; i < totalKeys; i++) {
            String key = "test:key:" + i;
            Node owner = ring.getPrimaryNode(key);
            counts.merge(owner.getNodeId(), 1, Integer::sum);
        }

        // Expected share per node: 1/3 (~33.3% = 5000 keys)
        // With 150 vnodes, each node should receive between 23% and 43% of keys
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            double percentage = (entry.getValue() * 100.0) / totalKeys;
            assertTrue(percentage >= 22.0 && percentage <= 45.0,
                    "Node " + entry.getKey() + " received " + percentage + "% of keys, outside balanced range [22%, 45%]");
        }
    }

    @Test
    @DisplayName("Minimal key disruption on node addition (DDIA consistent hashing property)")
    void testMinimalDisruptionOnNodeAddition() {
        ConsistentHashRing ring = new ConsistentHashRing(150);

        Node node1 = new Node("node-1", "127.0.0.1", 7001);
        Node node2 = new Node("node-2", "127.0.0.1", 7002);
        Node node3 = new Node("node-3", "127.0.0.1", 7003);

        ring.addNode(node1);
        ring.addNode(node2);
        ring.addNode(node3);

        int totalKeys = 10000;
        Map<String, Node> initialOwnership = new HashMap<>();
        for (int i = 0; i < totalKeys; i++) {
            String key = "key:" + i;
            initialOwnership.put(key, ring.getPrimaryNode(key));
        }

        // Add 4th node
        Node node4 = new Node("node-4", "127.0.0.1", 7004);
        ring.addNode(node4);

        int migratedKeys = 0;
        for (int i = 0; i < totalKeys; i++) {
            String key = "key:" + i;
            Node newOwner = ring.getPrimaryNode(key);
            if (!newOwner.equals(initialOwnership.get(key))) {
                migratedKeys++;
                // In consistent hashing, if a key migrates, it must move TO the newly added node!
                assertEquals(node4, newOwner, "Migrated key must be assigned to the new node");
            }
        }

        double migrationPercentage = (migratedKeys * 100.0) / totalKeys;
        // In an ideal 4-node cluster, the 4th node takes ~25% of keys.
        // With modulo hashing (hash % N), ~75% of keys would be disrupted!
        // Here, only ~18% to ~32% should move.
        assertTrue(migrationPercentage >= 15.0 && migrationPercentage <= 35.0,
                "Migration percentage " + migrationPercentage + "% should be close to 25%");
    }

    @Test
    @DisplayName("Preference list generates distinct physical replica nodes in ring order")
    void testPreferenceList() {
        ConsistentHashRing ring = new ConsistentHashRing(100);

        Node nodeA = new Node("node-A", "127.0.0.1", 7001);
        Node nodeB = new Node("node-B", "127.0.0.1", 7002);
        Node nodeC = new Node("node-C", "127.0.0.1", 7003);

        ring.addNode(nodeA);
        ring.addNode(nodeB);
        ring.addNode(nodeC);

        List<Node> prefList = ring.getPreferenceList("customer:42", 3);
        assertEquals(3, prefList.size());

        // All nodes in preference list must be distinct physical nodes
        Set<String> uniqueIds = new HashSet<>();
        for (Node n : prefList) {
            assertTrue(uniqueIds.add(n.getNodeId()), "Preference list nodes must be distinct");
        }
    }
}
