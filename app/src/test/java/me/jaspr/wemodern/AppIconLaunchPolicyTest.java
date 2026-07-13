package me.jaspr.wemodern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import org.junit.Test;

public class AppIconLaunchPolicyTest {
    @Test
    public void bubbledLaunchRemovesNewTaskFlag() {
        int original = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP;

        assertEquals(
                Intent.FLAG_ACTIVITY_CLEAR_TOP,
                AppIconLaunchPolicy.adjustWeChatFlags(original, true)
        );
    }

    @Test
    public void regularLaunchKeepsNewTaskFlag() {
        int original = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP;

        assertEquals(original, AppIconLaunchPolicy.adjustWeChatFlags(original, false));
    }

    @Test
    public void regularSettingsLaunchUsesSeparateTask() {
        int original = Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP;

        assertEquals(
                original | Intent.FLAG_ACTIVITY_NEW_TASK,
                AppIconLaunchPolicy.adjustSettingsFlags(original, false)
        );
    }

    @Test
    public void bubbledSettingsLaunchStaysInBubbleTask() {
        int original = Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP;

        assertEquals(original, AppIconLaunchPolicy.adjustSettingsFlags(original, true));
    }

    @Test
    public void bubbledSettingsForwardsOnlyWhenFeatureIsEnabled() {
        assertTrue(AppIconLaunchPolicy.shouldForwardFromSettings(true, true));
        assertFalse(AppIconLaunchPolicy.shouldForwardFromSettings(true, false));
        assertFalse(AppIconLaunchPolicy.shouldForwardFromSettings(false, true));
    }
}
