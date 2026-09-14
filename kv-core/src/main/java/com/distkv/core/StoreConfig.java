package com.distkv.core;

/**
 * Configuration options for storage engine instances.
 */
public class StoreConfig {

    private int initialCapacity = 16;
    private float loadFactor = 0.75f;
    private int maxCapacity = 100_000;
    private boolean lruEvictionEnabled = false;

    public StoreConfig() {}

    public int getInitialCapacity() {
        return initialCapacity;
    }

    public StoreConfig setInitialCapacity(int initialCapacity) {
        this.initialCapacity = initialCapacity;
        return this;
    }

    public float getLoadFactor() {
        return loadFactor;
    }

    public StoreConfig setLoadFactor(float loadFactor) {
        this.loadFactor = loadFactor;
        return this;
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public StoreConfig setMaxCapacity(int maxCapacity) {
        this.maxCapacity = maxCapacity;
        return this;
    }

    public boolean isLruEvictionEnabled() {
        return lruEvictionEnabled;
    }

    public StoreConfig setLruEvictionEnabled(boolean lruEvictionEnabled) {
        this.lruEvictionEnabled = lruEvictionEnabled;
        return this;
    }
}
