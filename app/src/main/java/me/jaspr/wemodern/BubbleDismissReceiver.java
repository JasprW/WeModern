package me.jaspr.wemodern;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

public final class BubbleDismissReceiver extends BroadcastReceiver {
    static final String ACTION_DISMISS = "me.jaspr.wemodern.action.DISMISS_BUBBLE";
    static final String EXTRA_NOTIFICATION_ID = "notification_id";
    static final String EXTRA_CONVERSATION_ID = "conversation_id";
    private static final String TAG = "WeModern";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_DISMISS.equals(intent.getAction())) return;
        int notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0);
        String conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID);
        if (notificationId == 0 || TextUtils.isEmpty(conversationId)) return;

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(notificationId);
        ConversationBubbleStore.remove(conversationId);
        WeChatNotificationService.forgetPersistedReplacementsForReplacementId(
                context,
                notificationId
        );
        Log.i(TAG, "cleared notification after bubble dismissal"
                + ", id=" + notificationId
                + ", conversation=" + conversationId);
    }
}
