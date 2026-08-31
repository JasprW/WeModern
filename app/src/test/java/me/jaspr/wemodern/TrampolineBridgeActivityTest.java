package me.jaspr.wemodern;

import static android.app.PendingIntent.FLAG_MUTABLE;
import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TrampolineBridgeActivityTest {
    @Test
    public void bridgeAcceptsOnlyEnabledWeChatActivityTargets() {
        assertTrue(TrampolineBridgeActivity.shouldUseTarget(
                true,
                "com.tencent.mm",
                true
        ));
        assertFalse(TrampolineBridgeActivity.shouldUseTarget(
                false,
                "com.tencent.mm",
                true
        ));
        assertFalse(TrampolineBridgeActivity.shouldUseTarget(
                true,
                "example.app",
                true
        ));
        assertFalse(TrampolineBridgeActivity.shouldUseTarget(
                true,
                "com.tencent.mm",
                false
        ));
    }

    @Test
    public void bridgePendingIntentIsMutableForBubbleLaunchOptions() {
        assertEquals(
                FLAG_UPDATE_CURRENT | FLAG_MUTABLE,
                TrampolineBridgeActivity.pendingIntentFlags()
        );
    }

    @Test
    public void bridgeUsesAStableIdentityPerConversation() {
        assertEquals(
                TrampolineBridgeActivity.requestCodeFor("wechat_alice"),
                TrampolineBridgeActivity.requestCodeFor("wechat_alice")
        );
        assertNotEquals(
                ConversationBubbles.requestCodeFor("wechat_alice"),
                TrampolineBridgeActivity.requestCodeFor("wechat_alice")
        );
        assertNotEquals(
                TrampolineBridgeActivity.requestCodeFor("wechat_alice"),
                TrampolineBridgeActivity.requestCodeFor("wechat_bob")
        );
    }

    @Test
    public void bridgeCollapsesOnlyAfterTheConversationCoveredIt() {
        assertTrue(TrampolineBridgeActivity.shouldCollapseAfterTarget(true, true));
        assertFalse(TrampolineBridgeActivity.shouldCollapseAfterTarget(true, false));
        assertFalse(TrampolineBridgeActivity.shouldCollapseAfterTarget(false, true));
    }

    @Test
    public void bridgeCollapsesOnlyForItsBubbledConversationResult() {
        int requestCode = TrampolineBridgeActivity.targetResultRequestCode();

        assertTrue(TrampolineBridgeActivity.shouldCollapseAfterTargetResult(
                requestCode,
                true,
                true
        ));
        assertFalse(TrampolineBridgeActivity.shouldCollapseAfterTargetResult(
                requestCode + 1,
                true,
                true
        ));
        assertFalse(TrampolineBridgeActivity.shouldCollapseAfterTargetResult(
                requestCode,
                false,
                true
        ));
        assertFalse(TrampolineBridgeActivity.shouldCollapseAfterTargetResult(
                requestCode,
                true,
                false
        ));
    }

    @Test
    public void missingClearTopDoesNotBackgroundABridgeStillCoveredByWeChat() {
        assertFalse(TrampolineBridgeActivity.shouldCollapseImmediatelyWhenClearTopUnavailable(
                true
        ));
        assertTrue(TrampolineBridgeActivity.shouldCollapseImmediatelyWhenClearTopUnavailable(
                false
        ));
    }
}
