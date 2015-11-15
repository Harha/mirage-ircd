package io.github.harha.ircd;

import io.github.harha.ircd.server.IRCServer;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class IRCServerTests {
    @Test
    public void testIRCServerSimpleInit() throws IOException {
        IRCServer server = new IRCServer();
        assertEquals(server.getString("sName"), "mirage-ircd test server");
        assertTrue(server.getMotd().size() > 1);
    }
}
