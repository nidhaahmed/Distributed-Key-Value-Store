package com.distkv.network;

import com.distkv.network.protocol.Command;
import com.distkv.network.protocol.MalformedRequestException;
import com.distkv.network.protocol.Request;
import com.distkv.network.protocol.RequestParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RequestParserTest {

    @Test
    @DisplayName("Parse standard single and multi-argument commands")
    void testStandardCommands() throws MalformedRequestException {
        Request setReq = RequestParser.parse("SET user:1 Nidha");
        assertEquals(Command.SET, setReq.getCommand());
        assertEquals(2, setReq.argCount());
        assertEquals("user:1", setReq.getArg(0));
        assertEquals("Nidha", setReq.getArg(1));

        Request getReq = RequestParser.parse("GET user:1");
        assertEquals(Command.GET, getReq.getCommand());
        assertEquals("user:1", getReq.getArg(0));

        Request pingReq = RequestParser.parse("PING");
        assertEquals(Command.PING, pingReq.getCommand());
        assertEquals(0, pingReq.argCount());

        Request pingMsgReq = RequestParser.parse("PING 'hello world'");
        assertEquals(Command.PING, pingMsgReq.getCommand());
        assertEquals("hello world", pingMsgReq.getArg(0));
    }

    @Test
    @DisplayName("Parse commands with quoted strings and spaces")
    void testQuotedStrings() throws MalformedRequestException {
        Request req = RequestParser.parse("SET \"user:profile:1\" \"Nidha Ahmed - Distributed Systems\"");
        assertEquals(Command.SET, req.getCommand());
        assertEquals("user:profile:1", req.getArg(0));
        assertEquals("Nidha Ahmed - Distributed Systems", req.getArg(1));
    }

    @Test
    @DisplayName("Reject malformed commands and improper argument counts")
    void testMalformedCommands() {
        // Unknown command
        MalformedRequestException e1 = assertThrows(MalformedRequestException.class, () -> RequestParser.parse("FOOBAR"));
        assertTrue(e1.getMessage().contains("unknown command 'FOOBAR'"));

        // Too few arguments
        MalformedRequestException e2 = assertThrows(MalformedRequestException.class, () -> RequestParser.parse("SET onlyKey"));
        assertTrue(e2.getMessage().contains("wrong number of arguments"));

        // Too many arguments
        MalformedRequestException e3 = assertThrows(MalformedRequestException.class, () -> RequestParser.parse("GET k1 k2"));
        assertTrue(e3.getMessage().contains("wrong number of arguments"));

        // Unclosed quotes
        assertThrows(MalformedRequestException.class, () -> RequestParser.parse("SET k1 \"unclosed"));
    }

    @Test
    @DisplayName("Tokenize input with whitespace variations")
    void testTokenizeWhitespace() throws MalformedRequestException {
        List<String> tokens = RequestParser.tokenize("   SET    key1   val1   ");
        assertEquals(3, tokens.size());
        assertEquals("SET", tokens.get(0));
        assertEquals("key1", tokens.get(1));
        assertEquals("val1", tokens.get(2));
    }
}
