package me.jaspr.wemodern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import org.junit.Test;

public class WeChatLauncherTest {
    @Test
    public void bubbleRootMatchesOnlyActionlessWeChatLauncher() {
        assertTrue(WeChatLauncher.isBubbleRootActivity(
                "com.tencent.mm/.ui.LauncherUI",
                null
        ));
        assertTrue(WeChatLauncher.isBubbleRootActivity(
                "com.tencent.mm/com.tencent.mm.ui.LauncherUI",
                null
        ));

        assertFalse(WeChatLauncher.isBubbleRootActivity(
                "com.tencent.mm/.ui.LauncherUI",
                Intent.ACTION_MAIN
        ));
        assertFalse(WeChatLauncher.isBubbleRootActivity(
                "com.tencent.mm/.ui.chatting.ChattingUI",
                null
        ));
        assertFalse(WeChatLauncher.isBubbleRootActivity(null, null));
    }
}
