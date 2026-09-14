package me.jaspr.wemodern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WeChatNotificationCaptureTest {
    @Test
    public void debugDefaultsCaptureWithoutRewriting() {
        // Unit tests run against the debug variant, where evidence collection is the default.
        assertTrue(BuildConfig.DEBUG);
        assertTrue(NotificationDebugPreferences.DEFAULT_CAPTURE_LOGGING_ENABLED);
        assertFalse(NotificationDebugPreferences.DEFAULT_REWRITE_ENABLED);
    }

    @Test
    public void defaultsFollowTheBuildType() {
        // The pair is build-type driven: debug collects evidence, release does the work and
        // stores nothing. Keeping the relationship explicit stops a future edit from making
        // a release build capture notification content by default.
        assertEquals(BuildConfig.DEBUG, NotificationDebugPreferences.DEFAULT_CAPTURE_LOGGING_ENABLED);
        assertEquals(!BuildConfig.DEBUG, NotificationDebugPreferences.DEFAULT_REWRITE_ENABLED);
    }
}
