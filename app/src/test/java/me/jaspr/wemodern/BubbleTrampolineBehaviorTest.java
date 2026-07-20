package me.jaspr.wemodern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BubbleTrampolineBehaviorTest {
    @Test
    public void enabledPreferenceDependsOnPlatformSupportNotCurrentBubbleReadiness() {
        assertTrue(BubbleTrampolineBehavior.shouldStoreEnabledPreference(true, true));
        assertFalse(BubbleTrampolineBehavior.shouldStoreEnabledPreference(true, false));
        assertFalse(BubbleTrampolineBehavior.shouldStoreEnabledPreference(false, true));
    }

    @Test
    public void conversationBridgePreferenceIsExperimentalAndDisabledWhenUnsupported() {
        assertTrue(BubbleTrampolineBehavior.shouldStoreConversationBridgePreference(
                true,
                true
        ));
        assertFalse(BubbleTrampolineBehavior.shouldStoreConversationBridgePreference(
                true,
                false
        ));
        assertFalse(BubbleTrampolineBehavior.shouldStoreConversationBridgePreference(
                false,
                true
        ));
    }

    @Test
    public void multipleConversationPreferenceIsExperimentalAndDisabledWhenUnsupported() {
        assertTrue(BubbleTrampolineBehavior.shouldStoreMultipleConversationBubblesPreference(
                true,
                true
        ));
        assertFalse(BubbleTrampolineBehavior.shouldStoreMultipleConversationBubblesPreference(
                true,
                false
        ));
        assertFalse(BubbleTrampolineBehavior.shouldStoreMultipleConversationBubblesPreference(
                false,
                true
        ));
    }

    @Test
    public void multipleConversationModeRequiresBothTrampolineLayers() {
        assertTrue(BubbleTrampolineBehavior.shouldUseMultipleConversationBubbles(
                true,
                true,
                true
        ));
        assertFalse(BubbleTrampolineBehavior.shouldUseMultipleConversationBubbles(
                false,
                true,
                true
        ));
        assertFalse(BubbleTrampolineBehavior.shouldUseMultipleConversationBubbles(
                true,
                false,
                true
        ));
        assertFalse(BubbleTrampolineBehavior.shouldUseMultipleConversationBubbles(
                true,
                true,
                false
        ));
    }

    @Test
    public void enabledTestMessageOpensWeChatHome() {
        assertTrue(BubbleTrampolineBehavior.shouldOpenWeChatHome(
                MessageTestNotifications.SHORTCUT_ID,
                true
        ));
    }

    @Test
    public void disabledTestMessageKeepsNormalAction() {
        assertFalse(BubbleTrampolineBehavior.shouldOpenWeChatHome(
                MessageTestNotifications.SHORTCUT_ID,
                false
        ));
    }

    @Test
    public void enabledRealConversationUsesHomeTrampoline() {
        assertTrue(BubbleTrampolineBehavior.shouldOpenWeChatHome("wechat_alice", true));
        assertFalse(BubbleTrampolineBehavior.shouldOpenWeChatHome(null, true));
    }

    @Test
    public void multiConversationReplacementSurvivesOnlyWhileItIsAnActiveBubbleHost() {
        assertTrue(BubbleTrampolineBehavior.shouldPreserveMessageReplacement(true, true));
        assertFalse(BubbleTrampolineBehavior.shouldPreserveMessageReplacement(false, true));
        assertFalse(BubbleTrampolineBehavior.shouldPreserveMessageReplacement(true, false));
    }
}
