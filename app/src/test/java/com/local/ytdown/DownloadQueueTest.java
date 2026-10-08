package com.local.ytdown;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DownloadQueueTest {
    @Test
    public void tasksDefaultToVideoAndKeepTheirOwnAudioModeInQueue() {
        DownloadQueue queue = new DownloadQueue();
        DownloadQueue.Task audioAccount = new DownloadQueue.Task("https://x.com/account", "audio", true, true);
        DownloadQueue.Task video = new DownloadQueue.Task("https://youtu.be/video", "best");
        assertTrue(queue.offer(audioAccount, null));
        assertTrue(queue.offer(video, null));
        assertTrue(queue.poll().audioOnly);
        assertFalse(queue.poll().audioOnly);
        assertFalse(new DownloadQueue.Task("url", "format", true).audioOnly);
    }

    @Test
    public void accountUsesOneSlotAndKeepsCapturedFormatWithLaterSharedLinks() {
        DownloadQueue queue = new DownloadQueue();
        DownloadQueue.Task account = new DownloadQueue.Task("https://www.instagram.com/example/", "best", true);
        assertTrue(queue.offer(account, null));
        assertTrue(queue.offer(new DownloadQueue.Task("https://youtu.be/example", "720p"), null));
        assertEquals(2, queue.size());
        assertTrue(queue.snapshot().get(0).account);
        assertEquals(account, queue.poll());
        assertEquals("best", account.format);
        assertEquals("https://youtu.be/example", queue.poll().url);
    }

    @Test
    public void accountDuplicatesAndIndividualCancellationDoNotDropSharedLinks() {
        DownloadQueue queue = new DownloadQueue();
        DownloadQueue.Task account = new DownloadQueue.Task("https://x.com/example", "best", true);
        assertTrue(queue.offer(account, null));
        assertFalse(queue.offer(new DownloadQueue.Task(account.url, "other", true), null));
        assertTrue(queue.offer(new DownloadQueue.Task("https://youtu.be/example", "best"), null));
        assertTrue(queue.remove(account.url));
        assertEquals(1, queue.size());
        assertFalse(queue.poll().account);
    }

    @Test
    public void keepsTenPendingTasksInFifoOrder() {
        DownloadQueue queue = new DownloadQueue();
        for (int i = 1; i <= 10; i++) {
            assertTrue(queue.offer(new DownloadQueue.Task("https://x.com/status/" + i, "best"),
                    "https://x.com/status/active"));
        }

        assertTrue(queue.isFull());
        assertFalse(queue.offer(new DownloadQueue.Task("https://x.com/status/11", "best"), null));
        assertEquals("https://x.com/status/1", queue.poll().url);
        assertEquals(9, queue.size());
    }

    @Test
    public void rejectsActiveAndQueuedDuplicates() {
        DownloadQueue queue = new DownloadQueue();
        DownloadQueue.Task task = new DownloadQueue.Task("https://x.com/status/1", "best");

        assertFalse(queue.offer(task, task.url));
        assertTrue(queue.offer(task, null));
        assertFalse(queue.offer(task, null));
    }

    @Test
    public void removesOnlySelectedPendingTask() {
        DownloadQueue queue = new DownloadQueue();
        queue.offer(new DownloadQueue.Task("https://example.com/1", "best"), null);
        queue.offer(new DownloadQueue.Task("https://example.com/2", "best"), null);
        assertTrue(queue.remove("https://example.com/1"));
        assertFalse(queue.remove("https://example.com/1"));
        assertEquals("https://example.com/2", queue.poll().url);
        assertEquals(0, queue.size());
    }
}
