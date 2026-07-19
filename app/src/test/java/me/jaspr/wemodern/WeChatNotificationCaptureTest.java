package me.jaspr.wemodern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WeChatNotificationCaptureTest {
    @Test
    public void debugDefaultsCaptureWithoutRewriting() {
        assertTrue(NotificationDebugPreferences.DEFAULT_CAPTURE_LOGGING_ENABLED);
        assertFalse(NotificationDebugPreferences.DEFAULT_REWRITE_ENABLED);
    }
}
