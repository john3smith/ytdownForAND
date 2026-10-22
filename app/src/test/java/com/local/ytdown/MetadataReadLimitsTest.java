package com.local.ytdown;

import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import static org.junit.Assert.*;

public class MetadataReadLimitsTest {
    @Test public void normalVideoMetadataRemainsReadable() throws Exception {
        MetadataReadLimits.install();
        assertEquals("한글 영상", new ObjectMapper().readTree(
                "{\"title\":\"한글 영상\",\"formats\":[{\"format_id\":\"18\",\"height\":360}]}"
        ).get("title").asText());
    }

    @Test public void deeplyNestedMetadataIsRejected() throws Exception {
        MetadataReadLimits.install();
        String nested = "[".repeat(129) + "0" + "]".repeat(129);
        try { new ObjectMapper().readTree(nested); fail("Unbounded metadata accepted"); }
        catch (StreamConstraintsException expected) { assertTrue(expected.getMessage().contains("128")); }
    }

    @Test public void oversizedNumericMetadataIsRejected() throws Exception {
        MetadataReadLimits.install();
        try { new ObjectMapper().readTree("{\"duration\":" + "9".repeat(1001) + "}"); fail("Oversized number accepted"); }
        catch (StreamConstraintsException expected) { assertTrue(expected.getMessage().contains("1000")); }
    }
}
