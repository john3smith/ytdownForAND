package com.local.ytdown;

import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public final class DownloadEngineStartupTest {
    @Test public void successfulUpdateStillVerifiesInstalledEngine() throws Exception {
        FakeEngine engine = new FakeEngine();
        DownloadEngineStartup.Ready ready = DownloadEngineStartup.prepare(engine);
        assertEquals("update,probe", engine.calls);
        assertEquals("2026.10.14", ready.version);
        assertNull(ready.updateFailure);
    }

    @Test public void failedUpdateAllowsVerifiedInstalledEngine() throws Exception {
        FakeEngine engine = new FakeEngine();
        engine.updateFailure = new IOException("offline");
        DownloadEngineStartup.Ready ready = DownloadEngineStartup.prepare(engine);
        assertEquals("update,probe", engine.calls);
        assertEquals("2026.10.14", ready.version);
        assertSame(engine.updateFailure, ready.updateFailure);
    }

    @Test public void failedUpdateAndBrokenEngineRemainFailed() {
        FakeEngine engine = new FakeEngine();
        engine.updateFailure = new IOException("offline");
        engine.probeFailure = new IOException("cannot execute installed engine");
        Exception failure = assertThrows(IOException.class, () -> DownloadEngineStartup.prepare(engine));
        assertSame(engine.probeFailure, failure);
        assertArrayEquals(new Throwable[]{engine.updateFailure}, failure.getSuppressed());
    }

    @Test public void successfulUpdateDoesNotHideBrokenEngine() {
        FakeEngine engine = new FakeEngine();
        engine.probeFailure = new IOException("cannot execute installed engine");
        assertSame(engine.probeFailure,
                assertThrows(IOException.class, () -> DownloadEngineStartup.prepare(engine)));
    }

    @Test public void emptyVersionCannotMarkEngineReady() {
        for (String version : new String[]{null, "", " \n\t"}) {
            FakeEngine engine = new FakeEngine();
            engine.version = version;
            assertThrows(IOException.class, () -> DownloadEngineStartup.prepare(engine));
        }
    }

    @Test public void fatalUpdateErrorDoesNotEnableFallback() {
        AssertionError fatal = new AssertionError("fatal");
        DownloadEngineStartup.Engine engine = new DownloadEngineStartup.Engine() {
            public void update() { throw fatal; }
            public String installedVersion() { fail("Must not hide a fatal update error"); return ""; }
        };
        assertSame(fatal, assertThrows(AssertionError.class, () -> DownloadEngineStartup.prepare(engine)));
    }

    private static final class FakeEngine implements DownloadEngineStartup.Engine {
        String calls = "";
        String version = " 2026.10.14\n";
        IOException updateFailure;
        IOException probeFailure;
        public void update() throws IOException {
            calls += "update";
            if (updateFailure != null) throw updateFailure;
        }
        public String installedVersion() throws IOException {
            calls += ",probe";
            if (probeFailure != null) throw probeFailure;
            return version;
        }
    }
}
