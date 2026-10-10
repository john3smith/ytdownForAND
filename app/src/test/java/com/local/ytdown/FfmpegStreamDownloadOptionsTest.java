package com.local.ytdown;

import static org.junit.Assert.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FfmpegStreamDownloadOptionsTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private File dependency(String name) throws IOException {
        File result = folder.newFile(name);
        Files.write(result.toPath(), new byte[]{1});
        return result;
    }

    @Test public void usesBundledFfmpegAndVerifiedTls() throws Exception {
        File executable = dependency("libffmpeg.so");
        File ca = dependency("trusted cert.pem");
        Map<String, String> options = new LinkedHashMap<>();
        assertTrue(FfmpegStreamDownloadOptions.apply(true, false, executable, ca, options::put));
        assertEquals(3, options.size());
        assertEquals("ffmpeg", options.get("--downloader"));
        assertEquals(executable.getAbsolutePath(), options.get("--ffmpeg-location"));
        String args = options.get("--downloader-args");
        assertTrue(args.contains("-tls_verify 1"));
        assertTrue(args.contains("-ca_file '" + ca.getAbsolutePath() + "'"));
        assertTrue(args.endsWith("-rw_timeout 30000000"));
        assertFalse(options.containsKey("--no-check-certificates"));
        assertFalse(options.containsKey("-f"));
        assertFalse(options.containsKey("--cookies"));
        assertFalse(options.containsKey("--proxy"));
    }

    @Test public void leavesOtherSitesAndAudioExtractionUnchanged() throws Exception {
        Map<String, String> options = new LinkedHashMap<>();
        assertFalse(FfmpegStreamDownloadOptions.apply(false, false, null, null, options::put));
        assertFalse(FfmpegStreamDownloadOptions.apply(true, true, null, null, options::put));
        assertTrue(options.isEmpty());
    }

    @Test public void missingCaFailsBeforeAnyOptionsAreApplied() throws Exception {
        Map<String, String> options = new LinkedHashMap<>();
        try {
            FfmpegStreamDownloadOptions.apply(true, false, dependency("libffmpeg.so"),
                    new File(folder.getRoot(), "missing"), options::put);
            fail("Missing trust bundle must fail closed");
        } catch (IOException expected) { assertTrue(options.isEmpty()); }
    }

    @Test public void emptyExecutableFailsClosed() throws Exception {
        Map<String, String> options = new LinkedHashMap<>();
        try {
            FfmpegStreamDownloadOptions.apply(true, false, folder.newFile("empty"),
                    dependency("cert.pem"), options::put);
            fail("Empty FFmpeg must not fall back silently");
        } catch (IOException expected) { assertTrue(options.isEmpty()); }
    }

    @Test public void quotesApostrophesAndRejectsControlCharacters() throws Exception {
        assertEquals("'a'\"'\"'b c'", FfmpegStreamDownloadOptions.quoteArgument("a'b c"));
        for (String value : new String[]{null, "a\nb", "a\rb", "a\0b"}) {
            try { FfmpegStreamDownloadOptions.quoteArgument(value); fail("Unsafe argument"); }
            catch (IOException expected) { /* expected */ }
        }
    }
}
