package com.distkv.core;

import java.util.Set;

/**
 * Storage engine implementation backed by our custom {@link HashTable}.
 */
public class HashTableStore implements KeyValueStore {

    private final HashTable<String, String> table;

    public HashTableStore() {
        this.table = new HashTable<>();
    }

    public HashTableStore(int initialCapacity) {
        this.table = new HashTable<>(initialCapacity);
    }

    @Override
    public String get(String key) {
        if (key == null) return null;
        return table.get(key);
    }

    @Override
    public void set(String key, String value) {
        if (key == null) throw new IllegalArgumentException("Key cannot be null");
        table.put(key, value);
    }

    @Override
    public String put(String key, String value) {
        if (key == null) throw new IllegalArgumentException("Key cannot be null");
        return table.put(key, value);
    }

    @Override
    public boolean delete(String key) {
        if (key == null) return false;
        return table.remove(key) != null;
    }

    @Override
    public boolean exists(String key) {
        if (key == null) return false;
        return table.containsKey(key);
    }

    @Override
    public Set<String> keys() {
        return table.keySet();
    }

    @Override
    public int size() {
        return table.size();
    }

    @Override
    public void clear() {
        table.clear();
    }

    public int capacity() {
        return table.capacity();
    }
}
