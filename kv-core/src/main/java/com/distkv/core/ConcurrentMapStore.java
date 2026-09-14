package com.distkv.core;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Baseline KeyValueStore implementation backed by Java's ConcurrentHashMap.
 * Useful for correctness validation and benchmarking against custom hash table.
 */
public class ConcurrentMapStore implements KeyValueStore {

    private final Map<String, String> store;

    public ConcurrentMapStore() {
        this.store = new ConcurrentHashMap<>();
    }

    public ConcurrentMapStore(int initialCapacity) {
        this.store = new ConcurrentHashMap<>(initialCapacity);
    }

    @Override
    public String get(String key) {
        if (key == null) return null;
        return store.get(key);
    }

    @Override
    public void set(String key, String value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("Key and value cannot be null");
        }
        store.put(key, value);
    }

    @Override
    public String put(String key, String value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("Key and value cannot be null");
        }
        return store.put(key, value);
    }

    @Override
    public boolean delete(String key) {
        if (key == null) return false;
        return store.remove(key) != null;
    }

    @Override
    public boolean exists(String key) {
        if (key == null) return false;
        return store.containsKey(key);
    }

    @Override
    public Set<String> keys() {
        return Collections.unmodifiableSet(store.keySet());
    }

    @Override
    public int size() {
        return store.size();
    }

    @Override
    public void clear() {
        store.clear();
    }
}
