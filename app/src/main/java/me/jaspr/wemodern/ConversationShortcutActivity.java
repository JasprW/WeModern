package me.jaspr.wemodern;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public class ConversationShortcutActivity extends Activity {
    private static final String TAG = "WeModern";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String conversationId = getIntent().getStringExtra(
                ConversationShortcuts.EXTRA_CONVERSATION_ID);
        if (!ConversationShortcuts.openConversation(this, conversationId)) {
            Log.w(TAG, "shortcut target unavailable; opening WeChat instead: " + conversationId);
            if (!WeChatLauncher.open(this)) {
                Log.e(TAG, "shortcut target unavailable and WeChat cannot be resolved: "
                        + conversationId);
            }
        }
        finish();
    }
}
