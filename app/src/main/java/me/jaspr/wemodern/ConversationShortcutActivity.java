package me.jaspr.wemodern;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class ConversationShortcutActivity extends Activity {
    private static final String TAG = "WeModern";
    private static final String WECHAT_PACKAGE = "com.tencent.mm";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String conversationId = getIntent().getStringExtra(
                ConversationShortcuts.EXTRA_CONVERSATION_ID);
        if (!ConversationShortcuts.openConversation(this, conversationId)) {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(WECHAT_PACKAGE);
            if (launchIntent != null) {
                Log.w(TAG, "shortcut target unavailable; opening WeChat instead: " + conversationId);
                startActivity(launchIntent);
            } else {
                Log.e(TAG, "shortcut target unavailable and WeChat cannot be resolved: "
                        + conversationId);
            }
        }
        finish();
    }
}
