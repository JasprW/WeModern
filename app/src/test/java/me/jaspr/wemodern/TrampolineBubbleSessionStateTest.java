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
    public void clearingHostEndsTheEmbeddedSession() {
        TrampolineBubbleSessionState.onEmbeddedLaunchStarted(42);
        TrampolineBubbleSessionState.onHostCleared();
        assertFalse(TrampolineBubbleSessionState.isEmbeddedSessionActive());
    }

    @Test
    public void replacingHostDetachesTheOldTaskFromSuccessorCleanup() {
        TrampolineBubbleSessionState.onEmbeddedLaunchStarted(42);
        TrampolineBubbleSessionState.onHostReplaced();

        assertFalse(TrampolineBubbleSessionState.isEmbeddedSessionActive());
        assertFalse(TrampolineBubbleSessionState.onTaskRemoved(42));
    }

    @Test
    public void independentConversationHostsCanCoexist() {
        TrampolineBubbleSessionState.onEmbeddedLaunchStarted(42, "wechat_alice", true);
        TrampolineBubbleSessionState.onEmbeddedLaunchStarted(43, "wechat_bob", true);

        assertTrue(TrampolineBubbleSessionState.isEmbeddedTask(42));
        assertTrue(TrampolineBubbleSessionState.isEmbeddedTask(43));
        assertTrue(TrampolineBubbleSessionState.isEmbeddedConversation("wechat_alice"));
        assertTrue(TrampolineBubbleSessionState.isEmbeddedConversation("wechat_bob"));
        assertTrue(TrampolineBubbleSessionState.isIndependentHostTask(42));

        assertTrue(TrampolineBubbleSessionState.onTaskRemoved(42));
        assertFalse(TrampolineBubbleSessionState.isEmbeddedConversation("wechat_alice"));
        assertTrue(TrampolineBubbleSessionState.isEmbeddedConversation("wechat_bob"));
        assertTrue(TrampolineBubbleSessionState.isEmbeddedSessionActive());
    }

    @Test
    public void clearingDedicatedHostDoesNotClearIndependentConversationHosts() {
        TrampolineBubbleSessionState.onEmbeddedLaunchStarted(41);
        TrampolineBubbleSessionState.onEmbeddedLaunchStarted(42, "wechat_alice", true);

        TrampolineBubbleSessionState.onHostCleared();

        assertFalse(TrampolineBubbleSessionState.isEmbeddedTask(41));
        assertTrue(TrampolineBubbleSessionState.isEmbeddedTask(42));
    }

    @Test
    public void dismissingConversationHostReleasesOnlyThatConversation() {
        TrampolineBubbleSessionState.onEmbeddedLaunchStarted(42, "wechat_alice", true);
        TrampolineBubbleSessionState.onEmbeddedLaunchStarted(43, "wechat_bob", true);

        TrampolineBubbleSessionState.onConversationHostDismissed("wechat_alice");

        assertFalse(TrampolineBubbleSessionState.isEmbeddedTask(42));
        assertTrue(TrampolineBubbleSessionState.isEmbeddedTask(43));
    }

    @Test
    public void disablingMultiConversationModePreservesOnlyDedicatedHost() {
        TrampolineBubbleSessionState.onEmbeddedLaunchStarted(41);
        TrampolineBubbleSessionState.onEmbeddedLaunchStarted(42, "wechat_alice", true);

        TrampolineBubbleSessionState.onIndependentHostsCleared();

        assertTrue(TrampolineBubbleSessionState.isEmbeddedTask(41));
        assertFalse(TrampolineBubbleSessionState.isEmbeddedTask(42));
    }
}
