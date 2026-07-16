package me.jaspr.wemodern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NotificationPostDeduplicatorTest {
    @Test
    public void duplicateListenerCallbacksAreProcessedOnce() {
        NotificationPostDeduplicator deduplicator = new NotificationPostDeduplicator();

        assertTrue(deduplicator.shouldProcess("wechat-message", 100L, 90L));
        assertFalse(deduplicator.shouldProcess("wechat-message", 100L, 90L));
    }

    @Test
    public void notificationUpdatesAndDifferentKeysAreStillProcessed() {
        NotificationPostDeduplicator deduplicator = new NotificationPostDeduplicator();

        assertTrue(deduplicator.shouldProcess("wechat-message", 100L, 90L));
        assertTrue(deduplicator.shouldProcess("wechat-message", 101L, 90L));
        assertTrue(deduplicator.shouldProcess("wechat-message", 101L, 91L));
        assertTrue(deduplicator.shouldProcess("wechat-call", 101L, 91L));
    }

    @Test
    public void missingSystemKeyDoesNotDropAValidPost() {
        NotificationPostDeduplicator deduplicator = new NotificationPostDeduplicator();

        assertTrue(deduplicator.shouldProcess(null, 100L, 90L));
        assertTrue(deduplicator.shouldProcess(null, 100L, 90L));
    }
}
