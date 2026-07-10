package me.jaspr.wemodern;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public final class LauncherActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ConversationShortcuts.ensureSettingsShortcut(this);
        if (!WeChatLauncher.open(this)) {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        }
        finish();
    }
}
