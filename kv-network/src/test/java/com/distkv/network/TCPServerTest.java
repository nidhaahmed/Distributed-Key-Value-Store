package com.distkv.network;

import com.distkv.core.HashTableStore;
import com.distkv.core.KeyValueStore;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class TCPServerTest {

    private TCPServer server;
    private KeyValueStore store;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        store = new HashTableStore();
        // Port 0 binds to an available ephemeral port
        server = new TCPServer("127.0.0.1", 0, store, 8);
        server.start();
        port = server.getPort();
        assertTrue(port > 0, "Port should be bound to a valid ephemeral port");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @DisplayName("Basic client-server interaction over TCP")
    void testBasicClientInteraction() throws IOException {
        try (TCPClient client = new TCPClient("127.0.0.1", port)) {
            // PING
            assertEquals("PONG", client.ping());

            // SET & GET
            assertTrue(client.set("user:1", "Nidha"));
            assertEquals("Nidha", client.get("user:1"));

            // EXISTS
            assertTrue(client.exists("user:1"));
            assertFalse(client.exists("unknown:key"));

            // KEYS
            client.set("user:2", "Ahmed");
            Set<String> keys = client.keys();
            assertEquals(2, keys.size());
            assertTrue(keys.contains("user:1"));
            assertTrue(keys.contains("user:2"));

            // DELETE
            assertTrue(client.delete("user:1"));
            assertNull(client.get("user:1"));
            assertFalse(client.delete("user:1")); // Second delete returns false/not found
        }
    }

    @Test
    @DisplayName("Server response to malformed and erroneous requests")
    void testMalformedRequests() throws IOException {
        try (TCPClient client = new TCPClient("127.0.0.1", port)) {
            // Unknown command
            String resp1 = client.sendCommand("INVALID_CMD foo bar");
            assertTrue(resp1.startsWith("ERR unknown command"));

            // Wrong number of arguments
            String resp2 = client.sendCommand("SET justKey");
            assertTrue(resp2.startsWith("ERR wrong number of arguments"));

            // Valid command still works after error
            assertTrue(client.set("healthy", "yes"));
            assertEquals("yes", client.get("healthy"));
        }
    }
}
