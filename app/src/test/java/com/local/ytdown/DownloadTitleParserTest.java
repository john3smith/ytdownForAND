package com.local.ytdown;

import static org.junit.Assert.*;
import org.junit.Test;

public class DownloadTitleParserTest {
    @Test public void titleEventPreservesUnicodeAndEscapes() {
        assertEquals("테스트 \"영상\"", DownloadTitleParser.parse("YTDownTitle=\"테스트 \\\"영상\\\"\""));
    }

    @Test public void unrelatedOrInvalidEventsAreNotTitles() {
        for (String value : new String[]{null, "[download] 50%", "YTDownTitle=42",
                "YTDownTitle=null", "YTDownTitle=\"\"", "YTDownTitle={}",
                "YTDownTitle=\"title\" extra", "YTDownTitle=\"" + "x".repeat(8192) + "\""}) {
            assertNull(DownloadTitleParser.parse(value));
        }
    }
}
