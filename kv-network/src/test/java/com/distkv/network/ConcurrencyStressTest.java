package com.distkv.network;

import com.distkv.core.ConcurrentSegmentedStore;
import com.distkv.core.KeyValueStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConcurrencyStressTest {

    private static final Logger log = LoggerFactory.getLogger(ConcurrencyStressTest.class);

    private TCPServer server;
    private KeyValueStore store;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        store = new ConcurrentSegmentedStore(32, 16);
        server = new TCPServer("127.0.0.1", 0, store, 64);
        server.start();
        port = server.getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @DisplayName("Single-client sequential baseline throughput")
    void testSingleClientThroughput() throws IOException {
        int ops = 2000;
        long start = System.nanoTime();
        try (TCPClient client = new TCPClient("127.0.0.1", port)) {
            for (int i = 0; i < ops; i++) {
                client.set("key:" + i, "val:" + i);
            }
        }
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        double opsPerSec = (ops * 1000.0) / Math.max(1, durationMs);
        log.info("1 client sequential: {} ops in {} ms ({} ops/sec)", ops, durationMs, String.format("%.2f", opsPerSec));
        assertEquals(ops, store.size());
    }

    @Test
    @DisplayName("Concurrent request handling with 10 parallel clients")
    void test10ClientsConcurrent() throws Exception {
        runConcurrentBenchmark(10, 500);
    }

    @Test
    @DisplayName("Concurrent request handling with 50 parallel clients")
    void test50ClientsConcurrent() throws Exception {
        runConcurrentBenchmark(50, 200);
    }

    @Test
    @DisplayName("High lock-contention test on shared keys across 20 clients")
    void testContendedWritesAndReads() throws Exception {
        int clientCount = 20;
        int opsPerClient = 200;
        String sharedKey = "shared:counter";
        store.set(sharedKey, "0");

        ExecutorService executor = Executors.newFixedThreadPool(clientCount);
        CountDownLatch latch = new CountDownLatch(clientCount);
        AtomicInteger successfulOps = new AtomicInteger(0);

        for (int i = 0; i < clientCount; i++) {
            final int clientId = i;
            executor.submit(() -> {
                try (TCPClient client = new TCPClient("127.0.0.1", port)) {
                    for (int j = 0; j < opsPerClient; j++) {
                        client.set(sharedKey, "v-" + clientId + "-" + j);
                        String read = client.get(sharedKey);
                        if (read != null && read.startsWith("v-")) {
                            successfulOps.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    log.error("Client error", e);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(15, TimeUnit.SECONDS), "Contended test should complete within timeout");
        executor.shutdown();
        assertEquals(clientCount * opsPerClient, successfulOps.get());
    }

    private void runConcurrentBenchmark(int clientCount, int opsPerClient) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(clientCount);
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(clientCount);
        AtomicInteger errorCount = new AtomicInteger(0);

        long start = System.nanoTime();

        for (int c = 0; c < clientCount; c++) {
            final int clientId = c;
            executor.submit(() -> {
                try {
                    startSignal.await(); // Synchronize start of all clients
                    try (TCPClient client = new TCPClient("127.0.0.1", port)) {
                        for (int i = 0; i < opsPerClient; i++) {
                            String k = "client:" + clientId + ":key:" + i;
                            String v = "value-" + i;
                            if (!client.set(k, v)) {
                                errorCount.incrementAndGet();
                            }
                            String fetched = client.get(k);
                            if (!v.equals(fetched)) {
                                errorCount.incrementAndGet();
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Client {} encountered error: {}", clientId, e.getMessage());
                    errorCount.incrementAndGet();
                } finally {
                    doneSignal.countDown();
                }
            });
        }

        startSignal.countDown(); // Fire!
        boolean finishedInTime = doneSignal.await(30, TimeUnit.SECONDS);
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        executor.shutdown();

        assertTrue(finishedInTime, "Concurrent benchmark timed out");
        assertEquals(0, errorCount.get(), "No errors should occur during concurrent requests");

        int totalOps = clientCount * opsPerClient * 2; // SET + GET
        double throughput = (totalOps * 1000.0) / Math.max(1, durationMs);
        log.info("{} clients: {} total ops in {} ms ({} ops/sec)",
                clientCount, totalOps, durationMs, String.format("%.2f", throughput));
        assertEquals(clientCount * opsPerClient, store.size());
    }
}
