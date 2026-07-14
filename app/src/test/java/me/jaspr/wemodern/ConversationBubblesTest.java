package me.jaspr.wemodern;

import static android.app.PendingIntent.FLAG_MUTABLE;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ConversationBubblesTest {
    @Test
    public void bubblesRequireAndroid10() {
        assertFalse(ConversationBubbles.isSupported(28));
        assertTrue(ConversationBubbles.isSupported(29));
        assertTrue(ConversationBubbles.isSupported(37));
    }

    @Test
    public void requestCodeIsStableForConversation() {
        assertEquals(
                ConversationBubbles.requestCodeFor("wechat:alice"),
                ConversationBubbles.requestCodeFor("wechat:alice")
        );
    }

    @Test
    public void requestCodeSeparatesConversations() {
        assertNotEquals(
                ConversationBubbles.requestCodeFor("wechat:alice"),
                ConversationBubbles.requestCodeFor("wechat:bob")
        );
    }

    @Test
    public void bubblePendingIntentIsMutableForSystemUiLaunchOptions() {
        assertEquals(FLAG_UPDATE_CURRENT | FLAG_MUTABLE, ConversationBubbles.pendingIntentFlags());
    }
}
