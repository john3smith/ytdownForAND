package com.local.ytdown;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DownloadFormatSelectorTest {
    @Test
    public void everyFallbackRequiresARealVideoCodec() {
        for (int position = 0; position < 3; position++) {
            String selector = DownloadFormatSelector.forQualityPosition(position);
            assertTrue(selector.contains("bestvideo"));
            assertTrue(selector.endsWith("[vcodec!=none]"));
            assertFalse(selector.endsWith("/best"));
        }
    }

    @Test
    public void limitedQualitiesKeepTheirHeightLimits() {
        assertTrue(DownloadFormatSelector.forQualityPosition(1).contains("height<=1080"));
        assertTrue(DownloadFormatSelector.forQualityPosition(2).contains("height<=720"));
    }

    @Test
    public void outputNamesIncludeAnIndexForMultipleVideos() {
        String template = DownloadFormatSelector.outputTemplate(new java.io.File("downloads"));
        assertTrue(template.contains("%(id)s"));
        assertTrue(template.contains("%(autonumber)03d"));
    }
}
