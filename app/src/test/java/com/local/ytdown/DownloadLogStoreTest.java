package com.local.ytdown;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class DownloadLogStoreTest {
    private long at(String value) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).parse(value).getTime();
    }

    @Test
    public void keepsOnlyEntriesFromLast24HoursIncludingBoundary() throws Exception {
        String log = "2026-09-28 09:59:59  old\n"
                + "2026-09-28 10:00:00  boundary\n"
                + "2026-09-29 09:00:00  recent\n";
        assertEquals("2026-09-28 10:00:00  boundary\n"
                        + "2026-09-29 09:00:00  recent\n",
                DownloadLogStore.retainRecent(log, at("2026-09-29 10:00:00")));
    }

    @Test
    public void removesUndatedAndMalformedLegacyEntries() throws Exception {
        String log = "legacy line\n2026-02-30 10:00:00  invalid date\n"
                + "2026-09-29 09:00:00  recent\n";
        assertEquals("2026-09-29 09:00:00  recent\n",
                DownloadLogStore.retainRecent(log, at("2026-09-29 10:00:00")));
    }

    @Test
    public void handlesEmptyAndUnterminatedLogs() throws Exception {
        long now = at("2026-09-29 10:00:00");
        assertEquals("", DownloadLogStore.retainRecent("", now));
        assertEquals("2026-09-29 09:00:00  recent",
                DownloadLogStore.retainRecent("2026-09-29 09:00:00  recent", now));
    }
}
