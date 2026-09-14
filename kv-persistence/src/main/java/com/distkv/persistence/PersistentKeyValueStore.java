package com.distkv.persistence;

import com.distkv.core.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * Decorator that wraps an in-memory {@link KeyValueStore} with durable Append-Only File (AOF) logging.
 * All write mutations are committed to AOF disk log, and state is restored from disk upon initialization.
 */
public class PersistentKeyValueStore implements KeyValueStore, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PersistentKeyValueStore.class);

    private final KeyValueStore underlyingStore;
    private final AOFWriter aofWriter;
    private final AOFRecoveryResult recoveryResult;

    /**
     * Creates and recovers a PersistentKeyValueStore.
     * Replays existing AOF logs if the file exists, then opens the AOFWriter for append mode.
     */
    public static PersistentKeyValueStore create(KeyValueStore store, File aofFile, FsyncPolicy fsyncPolicy) throws IOException {
        AOFRecoveryResult recoveryResult = null;
        if (aofFile.exists()) {
            recoveryResult = AOFReader.replay(aofFile, store);
        }

        AOFWriter writer = new AOFWriter(aofFile, fsyncPolicy);
        return new PersistentKeyValueStore(store, writer, recoveryResult);
    }

    public PersistentKeyValueStore(KeyValueStore underlyingStore, AOFWriter aofWriter, AOFRecoveryResult recoveryResult) {
        this.underlyingStore = underlyingStore;
        this.aofWriter = aofWriter;
        this.recoveryResult = recoveryResult;
    }

    @Override
    public String get(String key) {
        return underlyingStore.get(key);
    }

    @Override
    public void set(String key, String value) {
        try {
            aofWriter.appendSet(key, value);
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist SET mutation to AOF", e);
        }
        underlyingStore.set(key, value);
    }

    @Override
    public String put(String key, String value) {
        try {
            aofWriter.appendSet(key, value);
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist PUT mutation to AOF", e);
        }
        return underlyingStore.put(key, value);
    }

    @Override
    public boolean delete(String key) {
        if (!underlyingStore.exists(key)) {
            return false;
        }
        try {
            aofWriter.appendDelete(key);
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist DELETE mutation to AOF", e);
        }
        return underlyingStore.delete(key);
    }

    @Override
    public boolean exists(String key) {
        return underlyingStore.exists(key);
    }

    @Override
    public Set<String> keys() {
        return underlyingStore.keys();
    }

    @Override
    public int size() {
        return underlyingStore.size();
    }

    @Override
    public void clear() {
        try {
            aofWriter.appendClear();
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist CLEAR mutation to AOF", e);
        }
        underlyingStore.clear();
    }

    public AOFRecoveryResult getRecoveryResult() {
        return recoveryResult;
    }

    public void fsync() throws IOException {
        aofWriter.fsync();
    }

    @Override
    public void close() throws IOException {
        aofWriter.close();
    }
}
