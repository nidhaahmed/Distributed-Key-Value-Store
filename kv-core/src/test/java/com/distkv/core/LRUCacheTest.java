package com.distkv.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class LRUCacheTest {

    @Test
    @DisplayName("Verify exact LRU eviction sequence and access reordering")
    void testExactLruEvictionSequence() {
        // Capacity of 3
        LRUCache cache = new LRUCache(3);
        List<String> evicted = new ArrayList<>();
        cache.setEvictionListener(evicted::add);

        // Put A, B, C
        cache.set("A", "1");
        cache.set("B", "2");
        cache.set("C", "3");
        assertEquals(3, cache.size());

        // Put D -> Capacity exceeded -> A is LRU and evicted
        cache.set("D", "4");
        assertEquals(3, cache.size());
        assertFalse(cache.exists("A"));
        assertNull(cache.get("A"));
        assertEquals(List.of("A"), evicted);

        // Access B -> B moves to MRU (head). List order MRU->LRU: B, D, C
        assertEquals("2", cache.get("B"));

        // Put E -> Capacity exceeded -> C is now LRU and evicted
        cache.set("E", "5");
        assertEquals(3, cache.size());
        assertFalse(cache.exists("C"));
        assertTrue(cache.exists("B"));
        assertTrue(cache.exists("D"));
        assertTrue(cache.exists("E"));
        assertEquals(List.of("A", "C"), evicted);

        // Verify keys order (MRU to LRU: E, B, D)
        Set<String> keys = cache.keys();
        List<String> keyList = new ArrayList<>(keys);
        assertEquals(List.of("E", "B", "D"), keyList);
    }

    @Test
    @DisplayName("Updating existing key value promotes key to MRU")
    void testUpdatePromotesToHead() {
        LRUCache cache = new LRUCache(2);
        cache.set("k1", "v1");
        cache.set("k2", "v2");

        // Update k1 -> k1 becomes MRU
        cache.set("k1", "v1-updated");

        // Put k3 -> k2 is LRU, should be evicted
        cache.set("k3", "v3");

        assertEquals(2, cache.size());
        assertTrue(cache.exists("k1"));
        assertTrue(cache.exists("k3"));
        assertFalse(cache.exists("k2"));
        assertEquals("v1-updated", cache.get("k1"));
    }

    @Test
    @DisplayName("Delete operation unlinks properly without breaking LRU chain")
    void testDeleteMaintainsChain() {
        LRUCache cache = new LRUCache(3);
        cache.set("A", "1");
        cache.set("B", "2");
        cache.set("C", "3");

        assertTrue(cache.delete("B"));
        assertEquals(2, cache.size());
        assertFalse(cache.exists("B"));

        // Add D and E -> only 1 eviction should occur because space was freed by delete
        List<String> evicted = new ArrayList<>();
        cache.setEvictionListener(evicted::add);

        cache.set("D", "4"); // size becomes 3
        assertEquals(3, cache.size());
        assertEquals(0, evicted.size());

        cache.set("E", "5"); // size was 3 -> A should be evicted
        assertEquals(3, cache.size());
        assertEquals(List.of("A"), evicted);
    }

    @Test
    @DisplayName("Concurrent multithreaded read/write under capacity bounds")
    void testConcurrentLruAccess() throws Exception {
        int capacity = 100;
        LRUCache cache = new LRUCache(capacity);

        int threads = 16;
        int opsPerThread = 500;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < opsPerThread; j++) {
                        String key = "t:" + threadId + ":k:" + (j % 50);
                        cache.set(key, "val-" + j);
                        cache.get(key);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // Ensure cache size NEVER exceeds configured capacity
        assertTrue(cache.size() <= capacity, "Cache size must never exceed capacity");
    }
}
