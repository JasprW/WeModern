package me.jaspr.wemodern;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

/** Experimental mutable bubble root that forwards an opaque WeChat conversation intent. */
@TargetApi(Build.VERSION_CODES.S)
public final class TrampolineBridgeActivity extends Activity {
    private static final String TAG = "WeModern";
    private static final String WECHAT_PACKAGE = "com.tencent.mm";
    private static final String EXTRA_TARGET =
            "me.jaspr.wemodern.extra.TRAMPOLINE_BRIDGE_TARGET";
    private static final String EXTRA_CONVERSATION_ID =
            "me.jaspr.wemodern.extra.TRAMPOLINE_BRIDGE_CONVERSATION_ID";
    private static final int REQUEST_CODE_NAMESPACE = 0x45000000;

    private boolean targetLaunchSubmitted;
    private boolean pausedAfterTargetLaunch;
    private boolean movingTaskToBack;
    private boolean relaunchOnNextResume;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        forward(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        resetLaunchState();
        forward(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!AppIconLaunchPolicy.isLaunchedFromBubble(this)) return;
        if (relaunchOnNextResume) {
            relaunchOnNextResume = false;
            forward(getIntent());
            return;
        }
        if (!shouldCollapseAfterTarget(targetLaunchSubmitted, pausedAfterTargetLaunch)) {
            return;
        }

        targetLaunchSubmitted = false;
        pausedAfterTargetLaunch = false;
        movingTaskToBack = true;
        relaunchOnNextResume = true;
        boolean moved = moveTaskToBack(true);
        Log.i(TAG, "collapsing trampoline bubble after conversation Back"
                + ", taskId=" + getTaskId()
                + ", moved=" + moved);
        if (!moved) {
            movingTaskToBack = false;
            relaunchOnNextResume = false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (movingTaskToBack) {
            movingTaskToBack = false;
            return;
        }
        if (targetLaunchSubmitted) pausedAfterTargetLaunch = true;
    }

    static PendingIntent createBubbleIntent(
            Context context,
            PendingIntent target,
            String conversationId
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null;
        if (!isUsableTarget(target)) return null;
        Intent bridge = new Intent(context, TrampolineBridgeActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .setData(android.net.Uri.parse(
                        "wemodern://trampoline-bridge/host/"
                                + Integer.toUnsignedString(conversationHash(conversationId), 16)
                ))
                .putExtra(EXTRA_TARGET, target)
                .putExtra(EXTRA_CONVERSATION_ID, conversationId);
        return PendingIntent.getActivity(
                context,
                requestCodeFor(conversationId),
                bridge,
                pendingIntentFlags()
        );
    }

    static boolean shouldUseTarget(
            boolean experimentalEnabled,
            String creatorPackage,
            boolean activity
    ) {
        return experimentalEnabled && WECHAT_PACKAGE.equals(creatorPackage) && activity;
    }

    static int requestCode() {
        return REQUEST_CODE_NAMESPACE;
    }

    static int requestCodeFor(String conversationId) {
        return REQUEST_CODE_NAMESPACE | (conversationHash(conversationId) & 0x00ffffff);
    }

    static int pendingIntentFlags() {
        return ConversationBubbles.pendingIntentFlags();
    }

    static boolean shouldCollapseAfterTarget(
            boolean targetLaunchSubmitted,
            boolean pausedAfterTargetLaunch
    ) {
        return targetLaunchSubmitted && pausedAfterTargetLaunch;
    }

    private void forward(Intent intent) {
        PendingIntent target = targetFrom(intent);
        boolean launchedFromBubble = AppIconLaunchPolicy.isLaunchedFromBubble(this);
        if (!isUsableTarget(target)) {
            Log.w(TAG, "trampoline bridge target is unavailable or not a WeChat activity");
            fallback(launchedFromBubble);
            return;
        }

        if (!launchedFromBubble) {
            finish();
            BubbleLaunchCleanup.clear(this);
            if (!PendingIntentLauncher.send(target)) {
                WeChatLauncher.open(this);
            }
            return;
        }

        int taskId = getTaskId();
        String conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID);
        boolean independentHost =
                BubbleTrampolineBehavior.shouldUseMultipleConversationBubbles(this);
        TrampolineBubbleSessionState.onEmbeddedLaunchStarted(
                taskId,
                conversationId,
                independentHost
        );
        BubbleLaunchCleanup.suppressAppCancelForTrampolineLaunch(this);
        Log.i(TAG, "forwarding WeChat conversation from trampoline bubble"
                + ", taskId=" + taskId
                + ", conversation=" + conversationId
                + ", independentHost=" + independentHost
                + ", immutable=" + target.isImmutable()
                + ", persistentBridgeRoot=true");

        // Keep this activity as the bubble task root. When the WeChat child returns, onResume()
        // moves the task to the background so System UI collapses rather than removes the bubble.
        targetLaunchSubmitted = true;
        pausedAfterTargetLaunch = false;
        try {
            startIntentSender(
                    target.getIntentSender(),
                    null,
                    AppIconLaunchPolicy.taskSeparatingFlags(),
                    0,
                    0,
                    pendingIntentLaunchOptions()
            );
        } catch (IntentSender.SendIntentException | RuntimeException e) {
            Log.w(TAG, "failed to forward WeChat conversation inside trampoline bubble", e);
            resetLaunchState();
            TrampolineBubbleSessionState.onTaskRemoved(taskId);
            BubbleLaunchCleanup.clearAppCancelSuppression(this);
            WeChatLauncher.openFromBubbleFallback(this);
        }
    }

    private void resetLaunchState() {
        targetLaunchSubmitted = false;
        pausedAfterTargetLaunch = false;
        movingTaskToBack = false;
        relaunchOnNextResume = false;
    }

    private void fallback(boolean launchedFromBubble) {
        if (launchedFromBubble) {
            WeChatLauncher.openFromBubbleFallback(this);
            return;
        }
        finish();
        BubbleLaunchCleanup.clear(this);
        WeChatLauncher.open(this);
    }

    private Bundle pendingIntentLaunchOptions() {
        ActivityOptions options = ActivityOptions.makeBasic();
        if (Build.VERSION.SDK_INT >= 36) {
            options.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
            );
        } else if (Build.VERSION.SDK_INT >= 34) {
            options.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            );
        }
        return options.toBundle();
    }

    private static boolean isUsableTarget(PendingIntent target) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && target != null && shouldUseTarget(
                true,
                target.getCreatorPackage(),
                target.isActivity()
        );
    }

    private static PendingIntent targetFrom(Intent intent) {
        if (intent == null) return null;
        if (Build.VERSION.SDK_INT >= 33) {
            return intent.getParcelableExtra(EXTRA_TARGET, PendingIntent.class);
        }
        @SuppressWarnings("deprecation")
        PendingIntent target = intent.getParcelableExtra(EXTRA_TARGET);
        return target;
    }

    private static int conversationHash(String conversationId) {
        return conversationId == null ? 0 : conversationId.hashCode();
    }
}
