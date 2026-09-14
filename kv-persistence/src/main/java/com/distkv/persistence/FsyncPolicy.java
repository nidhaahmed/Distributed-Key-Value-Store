package com.distkv.persistence;

/**
 * Fsync synchronization policy for Append-Only File (AOF) persistence.
 */
public enum FsyncPolicy {
    /**
     * Fsync to physical disk after every single write mutation.
     * Guarantees maximum durability at the cost of lower throughput.
     */
    ALWAYS,

    /**
     * Fsync dirty buffer pages to physical disk every second via background worker.
     * Balances high write throughput with a maximum 1-second data-loss window.
     */
    EVERYSEC,

    /**
     * Relies on the operating system to flush dirty pages when appropriate.
     * Offers maximum write throughput.
     */
    NO
}
