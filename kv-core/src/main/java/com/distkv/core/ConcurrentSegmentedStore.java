package com.distkv.core;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * High-concurrency KeyValueStore backed by lock striping across independent segments.
 * Each segment contains its own custom {@link HashTableStore} and {@link ReentrantReadWriteLock}.
 * This avoids global lock bottlenecks, allowing concurrent reads across all segments,
 * and concurrent writes to disjoint segments without contention.
 */
public class ConcurrentSegmentedStore implements KeyValueStore {

    private static final int DEFAULT_SEGMENTS = 16;

    private static class Segment {
        final HashTableStore store;
        final ReentrantReadWriteLock lock;

        Segment(int initialCapacity) {
            this.store = new HashTableStore(initialCapacity);
            this.lock = new ReentrantReadWriteLock();
        }
    }

    private final Segment[] segments;
    private final int segmentMask;

    public ConcurrentSegmentedStore() {
        this(DEFAULT_SEGMENTS, 16);
    }

    public ConcurrentSegmentedStore(int segmentCount, int initialCapacityPerSegment) {
        int segs = roundUpToPowerOfTwo(segmentCount);
        this.segments = new Segment[segs];
        this.segmentMask = segs - 1;

        for (int i = 0; i < segs; i++) {
            this.segments[i] = new Segment(initialCapacityPerSegment);
        }
    }

    private static int roundUpToPowerOfTwo(int cap) {
        int n = cap - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return (n < 0) ? 1 : n + 1;
    }

    private int segmentIndex(String key) {
        int h = key == null ? 0 : key.hashCode();
        h ^= (h >>> 16);
        return h & segmentMask;
    }

    private Segment getSegment(String key) {
        return segments[segmentIndex(key)];
    }

    @Override
    public String get(String key) {
        if (key == null) return null;
        Segment seg = getSegment(key);
        seg.lock.readLock().lock();
        try {
            return seg.store.get(key);
        } finally {
            seg.lock.readLock().unlock();
        }
    }

    @Override
    public void set(String key, String value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("Key and value cannot be null");
        }
        Segment seg = getSegment(key);
        seg.lock.writeLock().lock();
        try {
            seg.store.set(key, value);
        } finally {
            seg.lock.writeLock().unlock();
        }
    }

    @Override
    public String put(String key, String value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("Key and value cannot be null");
        }
        Segment seg = getSegment(key);
        seg.lock.writeLock().lock();
        try {
            return seg.store.put(key, value);
        } finally {
            seg.lock.writeLock().unlock();
        }
    }

    @Override
    public boolean delete(String key) {
        if (key == null) return false;
        Segment seg = getSegment(key);
        seg.lock.writeLock().lock();
        try {
            return seg.store.delete(key);
        } finally {
            seg.lock.writeLock().unlock();
        }
    }

    @Override
    public boolean exists(String key) {
        if (key == null) return false;
        Segment seg = getSegment(key);
        seg.lock.readLock().lock();
        try {
            return seg.store.exists(key);
        } finally {
            seg.lock.readLock().unlock();
        }
    }

    @Override
    public Set<String> keys() {
        Set<String> allKeys = new HashSet<>();
        // Lock all segments for consistent snapshot
        lockAllReads();
        try {
            for (Segment seg : segments) {
                allKeys.addAll(seg.store.keys());
            }
        } finally {
            unlockAllReads();
        }
        return allKeys;
    }

    @Override
    public int size() {
        int total = 0;
        lockAllReads();
        try {
            for (Segment seg : segments) {
                total += seg.store.size();
            }
        } finally {
            unlockAllReads();
        }
        return total;
    }

    @Override
    public void clear() {
        // Lock all segments for write in deterministic order to prevent deadlocks
        lockAllWrites();
        try {
            for (Segment seg : segments) {
                seg.store.clear();
            }
        } finally {
            unlockAllWrites();
        }
    }

    private void lockAllReads() {
        for (Segment segment : segments) {
            segment.lock.readLock().lock();
        }
    }

    private void unlockAllReads() {
        for (int i = segments.length - 1; i >= 0; i--) {
            segments[i].lock.readLock().unlock();
        }
    }

    private void lockAllWrites() {
        for (Segment segment : segments) {
            segment.lock.writeLock().lock();
        }
    }

    private void unlockAllWrites() {
        for (int i = segments.length - 1; i >= 0; i--) {
            segments[i].lock.writeLock().unlock();
        }
    }
}
