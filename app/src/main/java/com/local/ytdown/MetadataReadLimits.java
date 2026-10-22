package com.local.ytdown;

import com.fasterxml.jackson.core.StreamReadConstraints;

/** Bound JSON handled by the upstream downloader's subsequently constructed factories. */
final class MetadataReadLimits {
    private MetadataReadLimits() { }

    static void install() {
        StreamReadConstraints.overrideDefaultStreamReadConstraints(
                StreamReadConstraints.builder()
                        .maxNestingDepth(128)
                        .maxNumberLength(1000)
                        .maxStringLength(4 * 1024 * 1024)
                        .maxDocumentLength(16L * 1024 * 1024)
                        .build());
    }
}
