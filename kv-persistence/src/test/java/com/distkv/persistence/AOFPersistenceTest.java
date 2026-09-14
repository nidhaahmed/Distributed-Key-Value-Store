package com.distkv.persistence;

import com.distkv.core.HashTableStore;
import com.distkv.core.KeyValueStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class AOFPersistenceTest {

    @Test
    @DisplayName("Durability and crash recovery: writes persist across restart")
    void testBasicDurabilityAndReplay(@TempDir Path tempDir) throws IOException {
        File aofFile = tempDir.resolve("test-store.aof").toFile();

        // 1. Initial process session
        try (PersistentKeyValueStore store = PersistentKeyValueStore.create(new HashTableStore(), aofFile, FsyncPolicy.ALWAYS)) {
            store.set("user:1", "Nidha");
            store.set("age", "22");
            store.set("role", "Distributed Systems Engineer");
            store.delete("age");

            assertEquals("Nidha", store.get("user:1"));
            assertNull(store.get("age"));
            assertEquals(2, store.size());
        }

        // 2. Simulated restart: create a new in-memory store and recover from same AOF
        KeyValueStore recoveredMemoryStore = new HashTableStore();
        AOFRecoveryResult result = AOFReader.replay(aofFile, recoveredMemoryStore);

        assertTrue(result.isRecoverySuccessful());
        assertEquals(4, result.getTotalLinesProcessed());
        assertEquals(4, result.getReplayedOperations());
        assertEquals(0, result.getCorruptedEntriesSkipped());

        // Verify recovered state
        assertEquals("Nidha", recoveredMemoryStore.get("user:1"));
        assertNull(recoveredMemoryStore.get("age"));
        assertEquals("Distributed Systems Engineer", recoveredMemoryStore.get("role"));
        assertEquals(2, recoveredMemoryStore.size());
    }

    @Test
    @DisplayName("Graceful recovery from corrupted or truncated trailing log entries")
    void testCorruptedTrailingEntryRecovery(@TempDir Path tempDir) throws IOException {
        File aofFile = tempDir.resolve("corrupted-store.aof").toFile();

        // 1. Write valid entries
        try (PersistentKeyValueStore store = PersistentKeyValueStore.create(new HashTableStore(), aofFile, FsyncPolicy.ALWAYS)) {
            store.set("valid1", "data1");
            store.set("valid2", "data2");
        }

        // 2. Artificially append corrupted trailing entry (simulating crash mid-write)
        try (FileWriter writer = new FileWriter(aofFile, true)) {
            writer.write("SET incomplete_key \"unterminated string\n");
            writer.write("CORRUPTED_OPCODE broken\n");
        }

        // 3. Replay log
        KeyValueStore store = new HashTableStore();
        AOFRecoveryResult result = AOFReader.replay(aofFile, store);

        assertTrue(result.isRecoverySuccessful());
        assertEquals("data1", store.get("valid1"));
        assertEquals("data2", store.get("valid2"));
        assertNull(store.get("incomplete_key"));
        assertEquals(2, store.size());
        assertEquals(2, result.getCorruptedEntriesSkipped());
    }

    @Test
    @DisplayName("Support for complex values with quotes, newlines, and backslashes")
    void testSpecialCharacterEscaping(@TempDir Path tempDir) throws IOException {
        File aofFile = tempDir.resolve("escaped-store.aof").toFile();

        String specialKey = "config:\"json\"";
        String specialValue = "{\n  \"name\": \"Nidha\",\n  \"path\": \"C:\\\\data\\\\store\"\n}";

        try (PersistentKeyValueStore store = PersistentKeyValueStore.create(new HashTableStore(), aofFile, FsyncPolicy.ALWAYS)) {
            store.set(specialKey, specialValue);
        }

        KeyValueStore newStore = new HashTableStore();
        AOFReader.replay(aofFile, newStore);

        assertEquals(specialValue, newStore.get(specialKey));
    }

    @Test
    @DisplayName("FsyncPolicy EVERYSEC background scheduler operation")
    void testEverySecPolicy(@TempDir Path tempDir) throws Exception {
        File aofFile = tempDir.resolve("everysec.aof").toFile();

        try (PersistentKeyValueStore store = PersistentKeyValueStore.create(new HashTableStore(), aofFile, FsyncPolicy.EVERYSEC)) {
            for (int i = 0; i < 50; i++) {
                store.set("k" + i, "v" + i);
            }
            Thread.sleep(1200); // Allow background fsync to trigger
        }

        KeyValueStore newStore = new HashTableStore();
        AOFReader.replay(aofFile, newStore);
        assertEquals(50, newStore.size());
    }
}
