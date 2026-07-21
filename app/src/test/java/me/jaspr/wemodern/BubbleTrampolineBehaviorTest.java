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
    public void trampolineReplacementSurvivesOnlyWhileItIsAnActiveBubbleHost() {
        assertTrue(BubbleTrampolineBehavior.shouldPreserveMessageReplacement(true, true));
        assertFalse(BubbleTrampolineBehavior.shouldPreserveMessageReplacement(false, true));
        assertFalse(BubbleTrampolineBehavior.shouldPreserveMessageReplacement(true, false));
    }
}
