package me.jaspr.wemodern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class TrampolineBubbleSessionStateTest {
    @After
    public void resetState() {
        TrampolineBubbleSessionState.resetForTest();
    }

    @Test
    public void sessionTracksTheExactEmbeddedTask() {
        assertFalse(TrampolineBubbleSessionState.isEmbeddedSessionActive());

        TrampolineBubbleSessionState.onEmbeddedLaunchStarted(42);
        assertTrue(TrampolineBubbleSessionState.isEmbeddedSessionActive());
        assertTrue(TrampolineBubbleSessionState.isEmbeddedTask(42));
        assertFalse(TrampolineBubbleSessionState.isEmbeddedTask(41));

        assertFalse(TrampolineBubbleSessionState.onTaskRemoved(41));
        assertTrue(TrampolineBubbleSessionState.isEmbeddedSessionActive());

        assertTrue(TrampolineBubbleSessionState.onTaskRemoved(42));
        assertFalse(TrampolineBubbleSessionState.isEmbeddedSessionActive());
    }

    @Test
    public void conversationHostsCanCoexist() {
        TrampolineBubbleSessionState.onEmbeddedLaunchStarted(42, "wechat_alice");
        TrampolineBubbleSessionState.onEmbeddedLaunchStarted(43, "wechat_bob");

        assertTrue(TrampolineBubbleSessionState.isEmbeddedTask(42));
        assertTrue(TrampolineBubbleSessionState.isEmbeddedTask(43));
        assertTrue(TrampolineBubbleSessionState.isEmbeddedConversation("wechat_alice"));
        assertTrue(TrampolineBubbleSessionState.isEmbeddedConversation("wechat_bob"));
        assertTrue(TrampolineBubbleSessionState.onTaskRemoved(42));
        assertFalse(TrampolineBubbleSessionState.isEmbeddedConversation("wechat_alice"));
        assertTrue(TrampolineBubbleSessionState.isEmbeddedConversation("wechat_bob"));
        assertTrue(TrampolineBubbleSessionState.isEmbeddedSessionActive());
    }

    @Test
    public void dismissingConversationHostReleasesOnlyThatConversation() {
        TrampolineBubbleSessionState.onEmbeddedLaunchStarted(42, "wechat_alice");
        TrampolineBubbleSessionState.onEmbeddedLaunchStarted(43, "wechat_bob");

        TrampolineBubbleSessionState.onConversationHostDismissed("wechat_alice");

        assertFalse(TrampolineBubbleSessionState.isEmbeddedTask(42));
        assertTrue(TrampolineBubbleSessionState.isEmbeddedTask(43));
    }

    @Test
    public void clearingAllHostsEndsEveryEmbeddedSession() {
        TrampolineBubbleSessionState.onEmbeddedLaunchStarted(41, "wechat_alice");
        TrampolineBubbleSessionState.onEmbeddedLaunchStarted(42, "wechat_bob");

        TrampolineBubbleSessionState.onAllHostsCleared();

        assertFalse(TrampolineBubbleSessionState.isEmbeddedTask(41));
        assertFalse(TrampolineBubbleSessionState.isEmbeddedTask(42));
        assertFalse(TrampolineBubbleSessionState.isEmbeddedSessionActive());
    }

    @Test
    public void sharedHostTaskIsTrackedSeparatelyFromConversationTasks() {
        TrampolineBubbleSessionState.onSharedHostLaunchStarted(51);
        TrampolineBubbleSessionState.onEmbeddedLaunchStarted(52, "wechat_alice");

        assertTrue(TrampolineBubbleSessionState.isSharedHostTask(51));
        assertFalse(TrampolineBubbleSessionState.isSharedHostTask(52));
        assertTrue(TrampolineBubbleSessionState.isEmbeddedTask(51));
        assertTrue(TrampolineBubbleSessionState.isEmbeddedTask(52));
    }
}
