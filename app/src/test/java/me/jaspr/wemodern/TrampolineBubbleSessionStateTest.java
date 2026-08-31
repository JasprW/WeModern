package me.jaspr.wemodern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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
        assertEquals("wechat_alice", TrampolineBubbleSessionState.conversationIdForTask(42));
        assertEquals("wechat_bob", TrampolineBubbleSessionState.conversationIdForTask(43));
        assertNull(TrampolineBubbleSessionState.conversationIdForTask(44));
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

    @Test
    public void chattingThenLauncherInSameConversationTaskRequestsOneCollapse() {
        TrampolineBubbleSessionState.onEmbeddedLaunchStarted(52, "wechat_alice");

        assertFalse(TrampolineBubbleSessionState.shouldCollapseAfterActivityResumed(
                52,
                "com.tencent.mm/.ui.chatting.variants.ChattingMainUI"
        ));
        assertTrue(TrampolineBubbleSessionState.shouldCollapseAfterActivityResumed(
                52,
                "com.tencent.mm/.ui.LauncherUI"
        ));
        assertFalse(TrampolineBubbleSessionState.shouldCollapseAfterActivityResumed(
                52,
                "com.tencent.mm/.ui.LauncherUI"
        ));
    }

    @Test
    public void launcherDoesNotCollapseWithoutImmediatelyPrecedingChat() {
        TrampolineBubbleSessionState.onEmbeddedLaunchStarted(52, "wechat_alice");

        assertFalse(TrampolineBubbleSessionState.shouldCollapseAfterActivityResumed(
                52,
                "com.tencent.mm/.ui.LauncherUI"
        ));
        assertFalse(TrampolineBubbleSessionState.shouldCollapseAfterActivityResumed(
                52,
                "com.tencent.mm/.ui.chatting.ChattingUI"
        ));
        assertFalse(TrampolineBubbleSessionState.shouldCollapseAfterActivityResumed(
                52,
                "com.tencent.mm/.plugin.gallery.GalleryUI"
        ));
        assertFalse(TrampolineBubbleSessionState.shouldCollapseAfterActivityResumed(
                52,
                "com.tencent.mm/.ui.LauncherUI"
        ));
    }

    @Test
    public void collapseDetectionIsBoundToExactConversationTask() {
        TrampolineBubbleSessionState.onEmbeddedLaunchStarted(52, "wechat_alice");
        TrampolineBubbleSessionState.onSharedHostLaunchStarted(53);

        assertFalse(TrampolineBubbleSessionState.shouldCollapseAfterActivityResumed(
                51,
                "com.tencent.mm/.ui.chatting.variants.ChattingMainUI"
        ));
        assertFalse(TrampolineBubbleSessionState.shouldCollapseAfterActivityResumed(
                53,
                "com.tencent.mm/.ui.chatting.variants.ChattingMainUI"
        ));
        assertFalse(TrampolineBubbleSessionState.shouldCollapseAfterActivityResumed(
                53,
                "com.tencent.mm/.ui.LauncherUI"
        ));
        assertFalse(TrampolineBubbleSessionState.shouldCollapseAfterActivityResumed(
                52,
                "com.tencent.mm/.ui.chatting.variants.ChattingMainUI"
        ));
        assertFalse(TrampolineBubbleSessionState.shouldCollapseAfterActivityResumed(
                51,
                "com.tencent.mm/.ui.LauncherUI"
        ));
        assertTrue(TrampolineBubbleSessionState.shouldCollapseAfterActivityResumed(
                52,
                "com.tencent.mm/.ui.LauncherUI"
        ));
    }

    @Test
    public void failedCollapseCanBeRequestedAgain() {
        TrampolineBubbleSessionState.onEmbeddedLaunchStarted(52, "wechat_alice");
        TrampolineBubbleSessionState.shouldCollapseAfterActivityResumed(
                52,
                "com.tencent.mm/com.tencent.mm.ui.chatting.variants.ChattingMainUI"
        );
        assertTrue(TrampolineBubbleSessionState.shouldCollapseAfterActivityResumed(
                52,
                "com.tencent.mm/com.tencent.mm.ui.LauncherUI"
        ));

        TrampolineBubbleSessionState.onCollapseRequestFailed(52);

        assertTrue(TrampolineBubbleSessionState.shouldCollapseAfterActivityResumed(
                52,
                "com.tencent.mm/.ui.LauncherUI"
        ));
    }
}
