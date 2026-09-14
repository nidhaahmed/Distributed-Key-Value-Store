package com.distkv.server;

import com.distkv.network.TCPClient;
import com.distkv.persistence.FsyncPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class KVServerAppTest {

    @Test
    @DisplayName("End-to-end integration: Server bootstrap + TCP Client + AOF Durability + LRU")
    void testEndToEndServerLifecycle(@TempDir Path tempDir) throws IOException {
        File aofFile = tempDir.resolve("e2e-store.aof").toFile();

        ServerConfig config = new ServerConfig()
                .setHost("127.0.0.1")
                .setPort(0) // dynamic ephemeral port
                .setAofFilePath(aofFile.getAbsolutePath())
                .setFsyncPolicy(FsyncPolicy.ALWAYS)
                .setPersistenceEnabled(true)
                .setLruEnabled(true)
                .setLruCapacity(100);

        KVServerApp app1 = new KVServerApp(config);
        app1.start();
        int serverPort = app1.getTcpServer().getPort();

        try (TCPClient client = new TCPClient("127.0.0.1", serverPort)) {
            assertEquals("PONG", client.ping());
            assertTrue(client.set("user:100", "Nidha"));
            assertTrue(client.set("framework", "Java21"));
            assertEquals("Nidha", client.get("user:100"));
            assertEquals("Java21", client.get("framework"));
        } finally {
            app1.stop();
        }

        // Restart server with same AOF file to verify complete recovery
        ServerConfig restartConfig = new ServerConfig()
                .setHost("127.0.0.1")
                .setPort(0)
                .setAofFilePath(aofFile.getAbsolutePath())
                .setFsyncPolicy(FsyncPolicy.ALWAYS)
                .setPersistenceEnabled(true)
                .setLruEnabled(true)
                .setLruCapacity(100);

        KVServerApp app2 = new KVServerApp(restartConfig);
        app2.start();
        int restartPort = app2.getTcpServer().getPort();

        try (TCPClient client = new TCPClient("127.0.0.1", restartPort)) {
            // Verify data recovered from AOF
            assertEquals("Nidha", client.get("user:100"));
            assertEquals("Java21", client.get("framework"));

            // Perform further mutation
            assertTrue(client.delete("framework"));
            assertNull(client.get("framework"));
        } finally {
            app2.stop();
        }
    }
}
