package me.jaspr.wemodern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WeChatLauncherTest {
    @Test
    public void recognizesOnlyActionlessWeChatLauncherAsBubbleRoot() {
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
                "android.intent.action.MAIN"
        ));
        assertFalse(WeChatLauncher.isBubbleRootActivity(
                "com.tencent.mm/.plugin.gallery.ui.AlbumPreviewUI",
                null
        ));
    }
}
