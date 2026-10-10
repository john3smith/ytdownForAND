package com.local.ytdown;

import android.content.Context;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

final class HttpsConnectionAssist {
    static final String PREFERENCE = "https_assist";
    private static LocalHttpsRelay relay;
    private static int users;
    private HttpsConnectionAssist() { }

    static boolean enabled(Context context) {
        return context.getSharedPreferences("ui_preferences", Context.MODE_PRIVATE)
                .getBoolean(PREFERENCE, false);
    }

    static synchronized Lease acquire() throws IOException {
        if (relay == null) relay = new LocalHttpsRelay();
        users++;
        return new Lease(relay);
    }

    static final class Lease implements Closeable {
        private final LocalHttpsRelay owned;
        private final AtomicBoolean released = new AtomicBoolean();
        private Lease(LocalHttpsRelay owned) { this.owned = owned; }
        String proxyUrl() { return owned.proxyUrl(); }
        int fragmentedCount() { return owned.fragmentedCount(); }
        int failureCount() { return owned.failureCount(); }
        @Override public void close() {
            if (!released.compareAndSet(false, true)) return;
            synchronized (HttpsConnectionAssist.class) {
                if (--users == 0) { owned.close(); if (relay == owned) relay = null; }
            }
        }
    }
}
