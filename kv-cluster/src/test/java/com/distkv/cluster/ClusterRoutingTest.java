package com.distkv.cluster;

import com.distkv.core.HashTableStore;
import com.distkv.core.KeyValueStore;
import com.distkv.network.TCPClient;
import com.distkv.network.TCPServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ClusterRoutingTest {

    private final List<TCPServer> servers = new ArrayList<>();
    private final List<ClusterRouter> routers = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (ClusterRouter router : routers) {
            router.close();
        }
        for (TCPServer server : servers) {
            server.stop();
        }
        servers.clear();
        routers.clear();
    }

    @Test
    @DisplayName("3-node cluster transparent proxy routing across distributed nodes")
    void testThreeNodeClusterProxyRouting() throws IOException {
        // 1. Create stores and servers on ephemeral ports
        KeyValueStore storeA = new HashTableStore();
        KeyValueStore storeB = new HashTableStore();
        KeyValueStore storeC = new HashTableStore();

        TCPServer serverA = new TCPServer("127.0.0.1", 0, storeA, 16);
        TCPServer serverB = new TCPServer("127.0.0.1", 0, storeB, 16);
        TCPServer serverC = new TCPServer("127.0.0.1", 0, storeC, 16);

        serverA.start();
        serverB.start();
        serverC.start();

        servers.add(serverA);
        servers.add(serverB);
        servers.add(serverC);

        Node nodeA = new Node("node-A", "127.0.0.1", serverA.getPort());
        Node nodeB = new Node("node-B", "127.0.0.1", serverB.getPort());
        Node nodeC = new Node("node-C", "127.0.0.1", serverC.getPort());

        // 2. Setup shared Consistent Hash Ring with 100 vnodes
        ConsistentHashRing ring = new ConsistentHashRing(100);
        ring.addNode(nodeA);
        ring.addNode(nodeB);
        ring.addNode(nodeC);

        // 3. Setup routers
        ClusterRouter routerA = new ClusterRouter(nodeA, ring, ClusterRouter.RoutingMode.PROXY);
        ClusterRouter routerB = new ClusterRouter(nodeB, ring, ClusterRouter.RoutingMode.PROXY);
        ClusterRouter routerC = new ClusterRouter(nodeC, ring, ClusterRouter.RoutingMode.PROXY);

        routers.add(routerA);
        routers.add(routerB);
        routers.add(routerC);

        // Restart servers with cluster routing attached
        serverA.stop();
        serverB.stop();
        serverC.stop();
        servers.clear();

        TCPServer clusterServerA = new TCPServer("127.0.0.1", nodeA.getPort(), storeA, 16, req -> routerA.executeRoutedCommand(req, storeA));
        TCPServer clusterServerB = new TCPServer("127.0.0.1", nodeB.getPort(), storeB, 16, req -> routerB.executeRoutedCommand(req, storeB));
        TCPServer clusterServerC = new TCPServer("127.0.0.1", nodeC.getPort(), storeC, 16, req -> routerC.executeRoutedCommand(req, storeC));

        clusterServerA.start();
        clusterServerB.start();
        clusterServerC.start();

        servers.add(clusterServerA);
        servers.add(clusterServerB);
        servers.add(clusterServerC);

        // 4. Client connects ONLY to Node A
        int totalKeys = 30;
        try (TCPClient client = new TCPClient("127.0.0.1", nodeA.getPort())) {
            for (int i = 0; i < totalKeys; i++) {
                assertTrue(client.set("cluster:item:" + i, "val-" + i));
            }

            // Read back all keys from Node A (even though keys are distributed across B and C)
            for (int i = 0; i < totalKeys; i++) {
                assertEquals("val-" + i, client.get("cluster:item:" + i));
            }
        }

        // 5. Verify physical distribution across all three underlying stores
        assertTrue(storeA.size() > 0, "Store A should own keys");
        assertTrue(storeB.size() > 0, "Store B should own keys");
        assertTrue(storeC.size() > 0, "Store C should own keys");
        assertEquals(totalKeys, storeA.size() + storeB.size() + storeC.size());

        // Verify each key was stored on the exact node indicated by the hash ring
        for (int i = 0; i < totalKeys; i++) {
            String key = "cluster:item:" + i;
            Node expectedOwner = ring.getPrimaryNode(key);
            if (expectedOwner.equals(nodeA)) {
                assertTrue(storeA.exists(key));
            } else if (expectedOwner.equals(nodeB)) {
                assertTrue(storeB.exists(key));
            } else {
                assertTrue(storeC.exists(key));
            }
        }
    }

    @Test
    @DisplayName("Redirect routing mode emits -MOVED response for remote keys")
    void testRedirectRoutingMode() {
        ConsistentHashRing ring = new ConsistentHashRing(50);
        Node node1 = new Node("node-1", "127.0.0.1", 7001);
        Node node2 = new Node("node-2", "127.0.0.1", 7002);
        ring.addNode(node1);
        ring.addNode(node2);

        ClusterRouter router1 = new ClusterRouter(node1, ring, ClusterRouter.RoutingMode.REDIRECT);
        routers.add(router1);

        KeyValueStore localStore = new HashTableStore();

        // Find a key owned by node-2
        String remoteKey = null;
        for (int i = 0; i < 100; i++) {
            if (node2.equals(ring.getPrimaryNode("k:" + i))) {
                remoteKey = "k:" + i;
                break;
            }
        }
        assertNotNull(remoteKey);

        com.distkv.network.protocol.Request req = new com.distkv.network.protocol.Request(
                com.distkv.network.protocol.Command.GET, List.of(remoteKey)
        );
        String response = router1.executeRoutedCommand(req, localStore);

        assertTrue(response.startsWith("-MOVED node-2 127.0.0.1:7002"),
                "Expected -MOVED redirect response but got: " + response);
    }

    @Test
    @DisplayName("Rebalance data migration when a new node joins the cluster")
    void testRebalancingOnJoin() {
        ConsistentHashRing ring = new ConsistentHashRing(100);
        Node nodeA = new Node("node-A", "127.0.0.1", 7001);
        Node nodeB = new Node("node-B", "127.0.0.1", 7002);
        ring.addNode(nodeA);
        ring.addNode(nodeB);

        KeyValueStore storeA = new HashTableStore();
        KeyValueStore storeB = new HashTableStore();
        Map<Node, KeyValueStore> clusterStores = new HashMap<>();
        clusterStores.put(nodeA, storeA);
        clusterStores.put(nodeB, storeB);

        // Populate initial data across 2 nodes
        int totalKeys = 300;
        for (int i = 0; i < totalKeys; i++) {
            String key = "user:" + i;
            String val = "v:" + i;
            Node owner = ring.getPrimaryNode(key);
            clusterStores.get(owner).set(key, val);
        }

        assertEquals(totalKeys, storeA.size() + storeB.size());

        // Now Node C joins the cluster!
        Node nodeC = new Node("node-C", "127.0.0.1", 7003);
        KeyValueStore storeC = new HashTableStore();
        clusterStores.put(nodeC, storeC);

        ring.addNode(nodeC);

        // Execute rebalance
        Rebalancer.RebalanceReport report = Rebalancer.rebalanceOnNodeJoin(ring, nodeC, clusterStores);

        assertTrue(report.getKeysMigrated() > 0, "Some keys must migrate to new Node C");
        assertTrue(storeC.size() > 0, "Node C store should now hold migrated keys");
        assertEquals(totalKeys, storeA.size() + storeB.size() + storeC.size(), "Total keys must be conserved");

        // Verify every key is now on its correct owner according to the 3-node ring
        for (int i = 0; i < totalKeys; i++) {
            String key = "user:" + i;
            Node expectedOwner = ring.getPrimaryNode(key);
            assertTrue(clusterStores.get(expectedOwner).exists(key), "Key " + key + " must exist on owner " + expectedOwner);
        }
    }
}
