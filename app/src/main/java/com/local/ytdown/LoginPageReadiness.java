package com.local.ytdown;

/** Monotonic minimum settling time, not a proof of login or security clearance. */
final class LoginPageReadiness {
    static final long SETTLE_MILLIS = 3_000;
    private long generation;
    private String page;
    private long readyAt = Long.MAX_VALUE;

    void started(String url) { generation++; page = url; readyAt = Long.MAX_VALUE; }
    boolean finished(String url, long now) {
        if (page != null && page.equals(url) && readyAt == Long.MAX_VALUE) {
            readyAt = now + SETTLE_MILLIS;
            return true;
        }
        return false;
    }
    void invalidate() { generation++; page = null; readyAt = Long.MAX_VALUE; }
    long generation() { return generation; }
    long remaining(String url, long now) {
        if (page == null || !page.equals(url) || readyAt == Long.MAX_VALUE) return -1;
        return Math.max(0, readyAt - now);
    }
    boolean ready(String url, long now) { return remaining(url, now) == 0; }
}
