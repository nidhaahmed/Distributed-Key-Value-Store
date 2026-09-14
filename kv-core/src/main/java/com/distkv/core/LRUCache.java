package com.distkv.core;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * Least Recently Used (LRU) in-memory cache implementing {@link KeyValueStore}.
 * Combines an O(1) hash table lookup with a doubly-linked list for O(1) access reordering and eviction.
 * Thread-safe via {@link ReentrantReadWriteLock}.
 */
public class LRUCache implements KeyValueStore {

    public static final int DEFAULT_CAPACITY = 100_000;

    /**
     * Doubly-linked list node storing key-value pairs and neighbor pointers.
     */
    static class Node {
        final String key;
        String value;
        Node prev;
        Node next;

        Node(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    private final int capacity;
    private final Map<String, Node> map;
    private final Node head;
    private final Node tail;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private Consumer<String> evictionListener;

    public LRUCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive: " + capacity);
        }
        this.capacity = capacity;
        this.map = new HashMap<>(capacity);

        // Initialize sentinels
        this.head = new Node(null, null);
        this.tail = new Node(null, null);
        head.next = tail;
        tail.prev = head;
    }

    public void setEvictionListener(Consumer<String> listener) {
        this.evictionListener = listener;
    }

    @Override
    public String get(String key) {
        if (key == null) return null;

        lock.writeLock().lock(); // Write lock because LRU access mutates the list order
        try {
            Node node = map.get(key);
            if (node == null) {
                return null;
            }
            moveToHead(node);
            return node.value;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void set(String key, String value) {
        put(key, value);
    }

    @Override
    public String put(String key, String value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("Key and value cannot be null");
        }

        lock.writeLock().lock();
        try {
            Node existing = map.get(key);
            if (existing != null) {
                String oldVal = existing.value;
                existing.value = value;
                moveToHead(existing);
                return oldVal;
            }

            // Evict least-recently used if capacity reached
            if (map.size() >= capacity) {
                Node lru = popTail();
                if (lru != null) {
                    map.remove(lru.key);
                    if (evictionListener != null) {
                        evictionListener.accept(lru.key);
                    }
                }
            }

            Node newNode = new Node(key, value);
            map.put(key, newNode);
            addToHead(newNode);
            return null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean delete(String key) {
        if (key == null) return false;

        lock.writeLock().lock();
        try {
            Node node = map.remove(key);
            if (node == null) {
                return false;
            }
            removeNode(node);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean exists(String key) {
        if (key == null) return false;

        lock.readLock().lock();
        try {
            return map.containsKey(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Set<String> keys() {
        lock.readLock().lock();
        try {
            // Return keys in most-recently-used to least-recently-used order
            Set<String> keys = new LinkedHashSet<>();
            Node curr = head.next;
            while (curr != tail) {
                keys.add(curr.key);
                curr = curr.next;
            }
            return keys;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int size() {
        lock.readLock().lock();
        try {
            return map.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void clear() {
        lock.writeLock().lock();
        try {
            map.clear();
            head.next = tail;
            tail.prev = head;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int getCapacity() {
        return capacity;
    }

    // --- Internal Doubly-Linked List Helpers (Must be called under writeLock) ---

    private void addToHead(Node node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    private void removeNode(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void moveToHead(Node node) {
        removeNode(node);
        addToHead(node);
    }

    private Node popTail() {
        if (tail.prev == head) {
            return null;
        }
        Node res = tail.prev;
        removeNode(res);
        return res;
    }
}
