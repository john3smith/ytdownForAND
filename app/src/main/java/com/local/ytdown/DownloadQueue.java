package com.local.ytdown;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.ArrayList;
import java.util.List;

final class DownloadQueue {
    static final int MAX_PENDING = 10;

    static final class Task {
        final String url;
        final String format;
        final boolean account;

        Task(String url, String format) {
            this(url, format, false);
        }

        Task(String url, String format, boolean account) {
            this.url = url;
            this.format = format;
            this.account = account;
        }
    }

    private final Deque<Task> pending = new ArrayDeque<>();

    boolean offer(Task task, String activeUrl) {
        if (task.url.equals(activeUrl) || contains(task.url) || pending.size() >= MAX_PENDING) {
            return false;
        }
        pending.addLast(task);
        return true;
    }

    Task poll() {
        return pending.pollFirst();
    }

    List<Task> snapshot() {
        return new ArrayList<>(pending);
    }

    boolean remove(String url) {
        return pending.removeIf(task -> task.url.equals(url));
    }

    int size() {
        return pending.size();
    }

    boolean isFull() {
        return pending.size() >= MAX_PENDING;
    }

    boolean contains(String url) {
        for (Task task : pending) {
            if (task.url.equals(url)) {
                return true;
            }
        }
        return false;
    }

    void clear() {
        pending.clear();
    }
}
