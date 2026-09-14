package com.distkv.core;

import java.util.Set;

/**
 * Fundamental interface for in-memory key-value storage.
 * Provides basic CRUD operations and metadata inspections.
 */
public interface KeyValueStore {

    /**
     * Retrieves the value associated with the specified key.
     *
     * @param key the key to retrieve
     * @return the associated value, or null if the key does not exist
     */
    String get(String key);

    /**
     * Associates the specified value with the specified key.
     *
     * @param key   the key
     * @param value the value
     */
    void set(String key, String value);

    /**
     * Associates the specified value with the key and returns the previous value.
     *
     * @param key   the key
     * @param value the value
     * @return the previous value associated with the key, or null if there was none
     */
    String put(String key, String value);

    /**
     * Removes the mapping for a key if it is present.
     *
     * @param key the key to remove
     * @return true if the key was present and removed, false otherwise
     */
    boolean delete(String key);

    /**
     * Tests if the specified key exists in the store.
     *
     * @param key the key to check
     * @return true if the key exists, false otherwise
     */
    boolean exists(String key);

    /**
     * Returns a set of all keys currently in the store.
     *
     * @return set of all keys
     */
    Set<String> keys();

    /**
     * Returns the number of key-value pairs stored.
     *
     * @return current size of the store
     */
    int size();

    /**
     * Clears all key-value pairs from the store.
     */
    void clear();
}
