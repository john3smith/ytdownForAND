package com.local.ytdown;

import java.io.IOException;

/** An update check is optional; successfully executing the installed engine is not. */
final class DownloadEngineStartup {
    interface Engine {
        void update() throws Exception;
        String installedVersion() throws Exception;
    }

    static final class Ready {
        final String version;
        final Exception updateFailure;

        Ready(String version, Exception updateFailure) {
            this.version = version;
            this.updateFailure = updateFailure;
        }
    }

    static Ready prepare(Engine engine) throws Exception {
        Exception updateFailure = null;
        try {
            engine.update();
        } catch (Exception error) {
            updateFailure = error;
        }
        try {
            String version = engine.installedVersion();
            if (version == null || version.trim().isEmpty()) {
                throw new IOException("Installed yt-dlp returned no version");
            }
            return new Ready(version.trim(), updateFailure);
        } catch (Exception error) {
            if (updateFailure != null && updateFailure != error) {
                error.addSuppressed(updateFailure);
            }
            throw error;
        }
    }

    private DownloadEngineStartup() { }
}
