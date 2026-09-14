package com.distkv.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class HashTableTest {

    private HashTable<String, String> table;
    private KeyValueStore store;

    @BeforeEach
    void setUp() {
        table = new HashTable<>(4, 0.75f);
        store = new HashTableStore(4);
    }

    @Test
    @DisplayName("Basic CRUD operations in HashTable")
    void testBasicCrud() {
        assertNull(table.put("user:1", "Nidha"));
        assertEquals("Nidha", table.get("user:1"));
        assertEquals(1, table.size());
        assertTrue(table.containsKey("user:1"));

        // Update existing key
        String oldVal = table.put("user:1", "Nidha Ahmed");
        assertEquals("Nidha", oldVal);
        assertEquals("Nidha Ahmed", table.get("user:1"));
        assertEquals(1, table.size());

        // Remove key
        String removed = table.remove("user:1");
        assertEquals("Nidha Ahmed", removed);
        assertNull(table.get("user:1"));
        assertFalse(table.containsKey("user:1"));
        assertEquals(0, table.size());
        assertTrue(table.isEmpty());
    }

    @Test
    @DisplayName("Collision resolution with multiple keys mapping to same bucket")
    void testCollisionResolution() {
        // Create custom keys with forced identical hash codes to test collision chaining
        class CollidingKey {
            final String name;
            CollidingKey(String name) { this.name = name; }
            @Override public int hashCode() { return 42; } // All keys collide
            @Override public boolean equals(Object o) {
                if (this == o) return true;
                if (!(o instanceof CollidingKey other)) return false;
                return Objects.equals(name, other.name);
            }
        }

        HashTable<CollidingKey, String> collisionTable = new HashTable<>(16);
        CollidingKey k1 = new CollidingKey("A");
        CollidingKey k2 = new CollidingKey("B");
        CollidingKey k3 = new CollidingKey("C");

        collisionTable.put(k1, "ValA");
        collisionTable.put(k2, "ValB");
        collisionTable.put(k3, "ValC");

        assertEquals(3, collisionTable.size());
        assertEquals("ValA", collisionTable.get(k1));
        assertEquals("ValB", collisionTable.get(k2));
        assertEquals("ValC", collisionTable.get(k3));

        // Remove middle of collision chain
        assertEquals("ValB", collisionTable.remove(k2));
        assertEquals(2, collisionTable.size());
        assertEquals("ValA", collisionTable.get(k1));
        assertNull(collisionTable.get(k2));
        assertEquals("ValC", collisionTable.get(k3));

        // Remove head of chain
        assertEquals("ValC", collisionTable.remove(k3));
        assertEquals("ValA", collisionTable.get(k1));
        assertNull(collisionTable.get(k3));
        assertEquals(1, collisionTable.size());

        // Remove last remaining
        assertEquals("ValA", collisionTable.remove(k1));
        assertEquals(0, collisionTable.size());
        assertTrue(collisionTable.isEmpty());
    }

    @Test
    @DisplayName("Dynamic resizing when threshold is exceeded")
    void testDynamicResizing() {
        int initialCapacity = table.capacity();
        int entriesToInsert = 1000;

        for (int i = 0; i < entriesToInsert; i++) {
            table.put("key:" + i, "value:" + i);
        }

        assertEquals(entriesToInsert, table.size());
        assertTrue(table.capacity() > initialCapacity, "Capacity should expand past initial");

        // Verify every single element is still retrievable post-resizing
        for (int i = 0; i < entriesToInsert; i++) {
            assertEquals("value:" + i, table.get("key:" + i));
        }

        Set<String> keySet = table.keySet();
        assertEquals(entriesToInsert, keySet.size());
    }

    @Test
    @DisplayName("KeyValueStore wrapper interface adherence")
    void testKeyValueStoreInterface() {
        store.set("name", "Nidha");
        store.set("age", "22");
        store.set("role", "Systems Engineer");

        assertEquals("Nidha", store.get("name"));
        assertEquals("22", store.get("age"));
        assertTrue(store.exists("role"));
        assertEquals(3, store.size());

        Set<String> keys = store.keys();
        assertTrue(keys.contains("name"));
        assertTrue(keys.contains("age"));
        assertTrue(keys.contains("role"));

        assertTrue(store.delete("age"));
        assertFalse(store.exists("age"));
        assertNull(store.get("age"));
        assertEquals(2, store.size());

        store.clear();
        assertEquals(0, store.size());
        assertFalse(store.exists("name"));
    }

    @Test
    @DisplayName("ConcurrentMapStore parity with HashTableStore")
    void testConcurrentMapStoreParity() {
        KeyValueStore concurrentStore = new ConcurrentMapStore();
        concurrentStore.set("k1", "v1");
        concurrentStore.set("k2", "v2");

        assertEquals("v1", concurrentStore.get("k1"));
        assertTrue(concurrentStore.exists("k2"));
        assertEquals(2, concurrentStore.size());
        assertTrue(concurrentStore.delete("k1"));
        assertFalse(concurrentStore.exists("k1"));
    }
}
