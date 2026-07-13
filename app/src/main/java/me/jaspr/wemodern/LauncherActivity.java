package me.jaspr.wemodern;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public final class LauncherActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ConversationShortcuts.ensureSettingsShortcut(this);
        boolean launchedFromBubble = AppIconLaunchPolicy.isLaunchedFromBubble(this);
        if (!AppIconBehavior.shouldOpenWeChat(this) || !WeChatLauncher.open(this)) {
            Intent settingsIntent = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            settingsIntent.setFlags(AppIconLaunchPolicy.adjustSettingsFlags(
                    settingsIntent.getFlags(),
                    launchedFromBubble
            ));
            startActivity(settingsIntent);
        }
        finish();
    }
}
