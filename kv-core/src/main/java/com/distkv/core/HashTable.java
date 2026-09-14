package com.distkv.core;

import java.util.*;

/**
 * High-performance, custom-built separate-chaining Hash Table data structure.
 * Designed from first principles to demonstrate hashing, collision resolution,
 * power-of-two table indexing, and dynamic resizing with load-factor thresholds.
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 */
public class HashTable<K, V> {

    private static final int DEFAULT_INITIAL_CAPACITY = 16;
    private static final int MAXIMUM_CAPACITY = 1 << 30;
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    private Node<K, V>[] table;
    private int size;
    private int threshold;
    private final float loadFactor;

    /**
     * Linked list entry node for bucket chaining.
     */
    public static class Node<K, V> {
        final int hash;
        final K key;
        V value;
        Node<K, V> next;

        Node(int hash, K key, V value, Node<K, V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }

        public K getKey() {
            return key;
        }

        public V getValue() {
            return value;
        }

        public V setValue(V newValue) {
            V oldValue = this.value;
            this.value = newValue;
            return oldValue;
        }
    }

    public HashTable() {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    public HashTable(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    @SuppressWarnings("unchecked")
    public HashTable(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Illegal initial capacity: " + initialCapacity);
        }
        if (loadFactor <= 0 || Float.isNaN(loadFactor)) {
            throw new IllegalArgumentException("Illegal load factor: " + loadFactor);
        }

        int capacity = roundUpToPowerOfTwo(initialCapacity);
        this.loadFactor = loadFactor;
        this.threshold = (int) (capacity * loadFactor);
        this.table = (Node<K, V>[]) new Node[capacity];
        this.size = 0;
    }

    /**
     * Computes key hash with high-bit spreading to prevent poor distribution.
     */
    static int hash(Object key) {
        if (key == null) {
            return 0;
        }
        int h = key.hashCode();
        return h ^ (h >>> 16);
    }

    private static int roundUpToPowerOfTwo(int cap) {
        int n = cap - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
    }

    /**
     * Associates the specified value with the specified key in this table.
     *
     * @param key   key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with key, or null if there was no mapping
     */
    public V put(K key, V value) {
        int h = hash(key);
        int index = h & (table.length - 1);

        Node<K, V> head = table[index];
        Node<K, V> current = head;

        while (current != null) {
            if (current.hash == h && (Objects.equals(current.key, key))) {
                V oldVal = current.value;
                current.value = value;
                return oldVal;
            }
            current = current.next;
        }

        // Key not found, insert at head of chain
        table[index] = new Node<>(h, key, value, head);
        size++;

        if (size >= threshold) {
            resize();
        }

        return null;
    }

    /**
     * Returns the value to which the specified key is mapped, or null if not found.
     *
     * @param key the key whose associated value is to be returned
     * @return the value associated with key, or null
     */
    public V get(Object key) {
        int h = hash(key);
        int index = h & (table.length - 1);

        Node<K, V> current = table[index];
        while (current != null) {
            if (current.hash == h && (Objects.equals(current.key, key))) {
                return current.value;
            }
            current = current.next;
        }
        return null;
    }

    /**
     * Removes the mapping for the specified key from this table if present.
     *
     * @param key key whose mapping is to be removed
     * @return the previous value associated with key, or null if there was no mapping
     */
    public V remove(Object key) {
        int h = hash(key);
        int index = h & (table.length - 1);

        Node<K, V> current = table[index];
        Node<K, V> prev = null;

        while (current != null) {
            if (current.hash == h && (Objects.equals(current.key, key))) {
                if (prev == null) {
                    table[index] = current.next;
                } else {
                    prev.next = current.next;
                }
                size--;
                return current.value;
            }
            prev = current;
            current = current.next;
        }
        return null;
    }

    /**
     * Returns true if this map contains a mapping for the specified key.
     */
    public boolean containsKey(Object key) {
        return get(key) != null || (key != null && findNode(key) != null);
    }

    private Node<K, V> findNode(Object key) {
        int h = hash(key);
        int index = h & (table.length - 1);
        Node<K, V> current = table[index];
        while (current != null) {
            if (current.hash == h && (Objects.equals(current.key, key))) {
                return current;
            }
            current = current.next;
        }
        return null;
    }

    /**
     * Returns the number of key-value mappings in this table.
     */
    public int size() {
        return size;
    }

    /**
     * Returns true if this table contains no key-value mappings.
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns current internal capacity (bucket count).
     */
    public int capacity() {
        return table.length;
    }

    /**
     * Clears all mappings from this table.
     */
    @SuppressWarnings("unchecked")
    public void clear() {
        if (table != null && size > 0) {
            size = 0;
            Arrays.fill(table, null);
        }
    }

    /**
     * Returns a set of the keys contained in this table.
     */
    public Set<K> keySet() {
        Set<K> keys = new HashSet<>(size);
        for (Node<K, V> head : table) {
            Node<K, V> current = head;
            while (current != null) {
                keys.add(current.key);
                current = current.next;
            }
        }
        return keys;
    }

    /**
     * Returns a collection view of the values contained in this table.
     */
    public List<V> values() {
        List<V> vals = new ArrayList<>(size);
        for (Node<K, V> head : table) {
            Node<K, V> current = head;
            while (current != null) {
                vals.add(current.value);
                current = current.next;
            }
        }
        return vals;
    }

    /**
     * Resizes the table by doubling its bucket capacity and re-distributing nodes.
     */
    @SuppressWarnings("unchecked")
    private void resize() {
        int oldCap = table.length;
        if (oldCap >= MAXIMUM_CAPACITY) {
            threshold = Integer.MAX_VALUE;
            return;
        }

        int newCap = oldCap << 1;
        Node<K, V>[] newTable = (Node<K, V>[]) new Node[newCap];
        threshold = (int) (newCap * loadFactor);

        for (int i = 0; i < oldCap; i++) {
            Node<K, V> node = table[i];
            while (node != null) {
                Node<K, V> next = node.next;
                int newIndex = node.hash & (newCap - 1);

                // Insert at head of new bucket chain
                node.next = newTable[newIndex];
                newTable[newIndex] = node;

                node = next;
            }
        }

        this.table = newTable;
    }
}
