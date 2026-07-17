package me.jaspr.wemodern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

public class WeChatForegroundStateTest {
    @After
    public void resetState() {
        WeChatForegroundState.resetForTest();
        TrampolineBubbleSessionState.resetForTest();
    }

    @Test
    public void tracksWeChatUntilAnotherAppResumes() {
        WeChatForegroundState.onForegroundActivityChanged(
                10,
                "com.tencent.mm/.ui.LauncherUI"
        );
        assertTrue(WeChatForegroundState.isWeChatForeground());

        WeChatForegroundState.onForegroundActivityChanged(
                11,
                "com.example.other/.MainActivity"
        );
        assertFalse(WeChatForegroundState.isWeChatForeground());
    }

    @Test
    public void transientLaunchProxyDoesNotErasePreviousForegroundApp() {
        WeChatForegroundState.onForegroundActivityChanged(
                10,
                "com.tencent.mm/.ui.LauncherUI"
        );
        WeChatForegroundState.onForegroundActivityChanged(
                11,
                "me.jaspr.wemodern/.WeChatLaunchProxyActivity"
        );
        assertTrue(WeChatForegroundState.isWeChatForeground());
    }

    @Test
    public void mainActivityClearsAStaleWeChatForeground() {
        WeChatForegroundState.onForegroundActivityChanged(
                10,
                "com.tencent.mm/.ui.LauncherUI"
        );
        WeChatForegroundState.onForegroundActivityChanged(
                11,
                "me.jaspr.wemodern/.MainActivity"
        );
        assertFalse(WeChatForegroundState.isWeChatForeground());
    }

    @Test
    public void weChatInsideTrackedBubbleTaskIsNotFullScreenForeground() {
        TrampolineBubbleSessionState.onEmbeddedLaunchStarted(42);
        WeChatForegroundState.onForegroundActivityChanged(
                42,
                "com.tencent.mm/.ui.LauncherUI"
        );
        assertFalse(WeChatForegroundState.isWeChatForeground());

        WeChatForegroundState.onForegroundActivityChanged(
                10,
                "com.tencent.mm/.ui.LauncherUI"
        );
        assertTrue(WeChatForegroundState.isWeChatForeground());
    }

    @Test
    public void removingForegroundBubbleTaskClearsStaleWeChatState() {
        TrampolineBubbleSessionState.onEmbeddedLaunchStarted(42);
        WeChatForegroundState.onForegroundActivityChanged(
                42,
                "com.tencent.mm/.ui.LauncherUI"
        );

        WeChatForegroundState.onTaskRemoved(42);
        TrampolineBubbleSessionState.onTaskRemoved(42);

        assertFalse(WeChatForegroundState.isWeChatForeground());
    }

    @Test
    public void controlledWeChatLaunchMarksItForeground() {
        WeChatForegroundState.onForegroundActivityChanged(
                11,
                "com.example.other/.MainActivity"
        );
        WeChatForegroundState.onWeChatLaunchSucceeded();
        assertTrue(WeChatForegroundState.isWeChatForeground());
    }

    @Test
    public void controlledLaunchDoesNotStickWithoutActivityEventAccess() {
        WeChatForegroundState.onWeChatLaunchSucceeded();
        assertFalse(WeChatForegroundState.isWeChatForeground());
    }
}
