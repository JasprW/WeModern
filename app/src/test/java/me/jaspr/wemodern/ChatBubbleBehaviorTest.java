package me.jaspr.wemodern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ChatBubbleBehaviorTest {
    @Test
    public void chatBubblesRequireAndroid10() {
        assertFalse(ChatBubbleBehavior.isSupported(28));
        assertTrue(ChatBubbleBehavior.isSupported(29));
        assertTrue(ChatBubbleBehavior.isSupported(37));
    }

    @Test
    public void featureAndSystemPermissionAreBothRequiredForReadyState() {
        assertFalse(ChatBubbleBehavior.isReady(false, false));
        assertFalse(ChatBubbleBehavior.isReady(true, false));
        assertFalse(ChatBubbleBehavior.isReady(false, true));
        assertTrue(ChatBubbleBehavior.isReady(true, true));
    }

    @Test
    public void trampolineRequiresCoreSetupFeatureAndSystemPermission() {
        assertFalse(ChatBubbleBehavior.canUseTrampoline(false, true, true));
        assertFalse(ChatBubbleBehavior.canUseTrampoline(true, false, true));
        assertFalse(ChatBubbleBehavior.canUseTrampoline(true, true, false));
        assertTrue(ChatBubbleBehavior.canUseTrampoline(true, true, true));
    }
}
