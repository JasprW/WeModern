package me.jaspr.wemodern;

import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WeChatLaunchProxyActivityTest {
    @Test
    public void notificationProxyIdentityIsStableAndSeparatedByTarget() {
        assertEquals(
                WeChatLaunchProxyActivity.requestCodeFor("message:alice"),
                WeChatLaunchProxyActivity.requestCodeFor("message:alice"));
        assertNotEquals(
                WeChatLaunchProxyActivity.requestCodeFor("message:alice"),
                WeChatLaunchProxyActivity.requestCodeFor("message:bob"));
    }

    @Test
    public void notificationProxyPendingIntentIsImmutable() {
        assertEquals(
                FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE,
                WeChatLaunchProxyActivity.pendingIntentFlags());
    }

    @Test
    public void incomingCallActionsHaveSeparateIdentitiesAndShareCleanupPolicy() {
        String answer = "call:incoming:answer";
        String decline = "call:incoming:decline";
        String content = "call:incoming:content";

        assertNotEquals(
                WeChatLaunchProxyActivity.requestCodeFor(answer),
                WeChatLaunchProxyActivity.requestCodeFor(decline));
        assertNotEquals(
                WeChatLaunchProxyActivity.requestCodeFor(answer),
                WeChatLaunchProxyActivity.requestCodeFor(content));
        assertTrue(WeChatLaunchProxyActivity.isIncomingCallLaunchKey(answer));
        assertTrue(WeChatLaunchProxyActivity.isIncomingCallLaunchKey(decline));
        assertTrue(WeChatLaunchProxyActivity.isIncomingCallLaunchKey(content));
        assertFalse(WeChatLaunchProxyActivity.isIncomingCallLaunchKey("message:alice"));
    }
}
