package me.jaspr.wemodern;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class TestNotificationIdsTest {
    @Test
    public void messageAndCallTestsAreProtectedFromReplacementCleanup() {
        assertTrue(WeChatNotificationService.isTestNotificationId(100));
        assertTrue(WeChatNotificationService.isTestNotificationId(CallTestNotifications.CURRENT_ID));
    }

    @Test
    public void messageTestUsesOnlyItsDedicatedConversationShortcutId() {
        assertTrue(MessageTestNotifications.isTestShortcutId(
                MessageTestNotifications.SHORTCUT_ID));
        assertFalse(MessageTestNotifications.isTestShortcutId("wemodern_settings"));
    }
}
