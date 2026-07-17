package me.jaspr.wemodern;

import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

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
}
