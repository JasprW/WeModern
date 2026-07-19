package me.jaspr.wemodern;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

public final class CallTestService extends Service {
    private static final String TAG = "WeModern";
    private static final String EXTRA_VIDEO = "video";
    private static final String EXTRA_SESSION_ID = "session_id";
    private long activeSessionId = Long.MIN_VALUE;
    private boolean activeVideo;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannels.ensure(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        boolean video = intent != null && intent.getBooleanExtra(EXTRA_VIDEO, false);
        long sessionId = intent == null
                ? Long.MIN_VALUE
                : intent.getLongExtra(EXTRA_SESSION_ID, Long.MIN_VALUE);
        switch (CallTestNotifications.commandFor(action)) {
            case SHOW_INCOMING:
                activeSessionId = sessionId;
                activeVideo = video;
                showIncoming(video, sessionId);
                break;
            case SHOW_ONGOING:
                if (isCurrentSession(video, sessionId)) {
                    showOngoing(video, sessionId);
                } else {
                    logStaleAction(action, video, sessionId);
                }
                break;
            case STOP:
                if (isCurrentSession(video, sessionId)) {
                    stopTestCall(action);
                } else {
                    logStaleAction(action, video, sessionId);
                }
                break;
            case IGNORE:
                Log.w(TAG, "ignore unknown call test action: " + action);
                stopSelf(startId);
                break;
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    static Intent startIntent(Context context, boolean video) {
        return commandIntent(
                context,
                CallTestNotifications.ACTION_START,
                video,
                SystemClock.elapsedRealtimeNanos()
        );
    }

    private void showIncoming(boolean video, long sessionId) {
        Icon avatar = mockCallerAvatar();
        Notification notification = CallStyleNotifications.buildIncoming(
                this,
                getString(CallTestNotifications.mockCallerNameResource()),
                getString(video
                        ? R.string.wechat_incoming_video_call
                        : R.string.wechat_incoming_voice_call),
                video,
                avatar,
                contentIntent(video, sessionId),
                serviceIntent(CallTestNotifications.ACTION_DECLINE, video, sessionId),
                serviceIntent(CallTestNotifications.ACTION_ANSWER, video, sessionId)
        );
        startCallForeground(notification);
        getSystemService(NotificationManager.class).cancel(CallTestNotifications.LEGACY_VIDEO_ID);
        Log.i(TAG, "posted incoming CallStyle test, video=" + video);
    }

    private void showOngoing(boolean video, long sessionId) {
        Icon avatar = mockCallerAvatar();
        Notification notification = CallStyleNotifications.buildOngoing(
                this,
                getString(CallTestNotifications.mockCallerNameResource()),
                getString(video ? R.string.test_video_call_text : R.string.test_voice_call_text),
                video,
                avatar,
                System.currentTimeMillis(),
                contentIntent(video, sessionId),
                serviceIntent(CallTestNotifications.ACTION_HANG_UP, video, sessionId)
        );
        startCallForeground(notification);
        if (Build.VERSION.SDK_INT >= 36) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            Log.i(TAG, "test ongoing CallStyle promotedAllowed="
                    + manager.canPostPromotedNotifications()
                    + ", promotable=" + notification.hasPromotableCharacteristics()
                    + ", video=" + video);
        }
    }

    private void startCallForeground(Notification notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                    CallTestNotifications.CURRENT_ID,
                    notification,
                    CallNotificationPresentation.foregroundServiceType()
            );
        } else {
            startForeground(CallTestNotifications.CURRENT_ID, notification);
        }
    }

    private void stopTestCall(String action) {
        activeSessionId = Long.MIN_VALUE;
        stopForeground(STOP_FOREGROUND_REMOVE);
        getSystemService(NotificationManager.class).cancel(CallTestNotifications.CURRENT_ID);
        stopSelf();
        Log.i(TAG, "stopped CallStyle test: " + action);
    }

    private PendingIntent contentIntent(boolean video, long sessionId) {
        Intent intent = new Intent(this, MainActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(
                this,
                CallTestNotifications.requestCodeFor("content", video, sessionId),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private PendingIntent serviceIntent(String action, boolean video, long sessionId) {
        return PendingIntent.getService(
                this,
                CallTestNotifications.requestCodeFor(action, video, sessionId),
                commandIntent(this, action, video, sessionId),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private boolean isCurrentSession(boolean video, long sessionId) {
        return CallTestNotifications.isCurrentSession(
                activeSessionId,
                activeVideo,
                sessionId,
                video
        );
    }

    private Icon mockCallerAvatar() {
        return Icon.createWithResource(
                this,
                CallTestNotifications.mockCallerAvatarResource()
        );
    }

    private void logStaleAction(String action, boolean video, long sessionId) {
        Log.i(TAG, "ignore stale CallStyle test action: " + action
                + ", video=" + video
                + ", session=" + sessionId
                + ", activeVideo=" + activeVideo
                + ", activeSession=" + activeSessionId);
    }

    private static Intent commandIntent(
            Context context,
            String action,
            boolean video,
            long sessionId
    ) {
        return new Intent(context, CallTestService.class)
                .setAction(action)
                .putExtra(EXTRA_VIDEO, video)
                .putExtra(EXTRA_SESSION_ID, sessionId);
    }
}
