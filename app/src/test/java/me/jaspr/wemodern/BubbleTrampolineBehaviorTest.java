package me.jaspr.wemodern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BubbleTrampolineBehaviorTest {
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
    public void dedicatedHostLetsMessageReplacementsSynchronizeNormally() {
        assertFalse(BubbleTrampolineBehavior.shouldPreserveMessageReplacement(true, true));
        assertFalse(BubbleTrampolineBehavior.shouldPreserveMessageReplacement(false, true));
        assertFalse(BubbleTrampolineBehavior.shouldPreserveMessageReplacement(true, false));
    }
}
