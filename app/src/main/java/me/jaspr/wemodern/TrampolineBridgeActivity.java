package me.jaspr.wemodern;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

/** Mutable bubble root that forwards an opaque WeChat conversation intent. */
@TargetApi(Build.VERSION_CODES.S)
public final class TrampolineBridgeActivity extends Activity {
    private static final String TAG = "WeModern";
    private static final String WECHAT_PACKAGE = "com.tencent.mm";
    private static final String EXTRA_TARGET =
            "me.jaspr.wemodern.extra.TRAMPOLINE_BRIDGE_TARGET";
    private static final String EXTRA_CONVERSATION_ID =
            "me.jaspr.wemodern.extra.TRAMPOLINE_BRIDGE_CONVERSATION_ID";
    private static final String EXTRA_CONVERSATION_TITLE =
            "me.jaspr.wemodern.extra.TRAMPOLINE_BRIDGE_CONVERSATION_TITLE";
    private static final String ACTION_COLLAPSE_AFTER_TARGET_RESULT =
            "me.jaspr.wemodern.action.COLLAPSE_TRAMPOLINE_AFTER_TARGET_RESULT";
    private static final String EXTRA_COLLAPSE_REASON =
            "me.jaspr.wemodern.extra.TRAMPOLINE_COLLAPSE_REASON";
    private static final int REQUEST_CODE_NAMESPACE = 0x45000000;
    private static final int TARGET_RESULT_REQUEST_CODE = 0x5743;

    private boolean targetLaunchSubmitted;
    private boolean pausedAfterTargetLaunch;
    private boolean movingTaskToBack;
    private boolean relaunchOnNextResume;
    private boolean collapseOnNextResume;
    private String collapseReason;
    private TrampolineLoadingView loadingView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (AppIconLaunchPolicy.isLaunchedFromBubble(this)) {
            loadingView = new TrampolineLoadingView(this);
            setContentView(loadingView);
        }
        if (isCollapseIntent(getIntent())) {
            prepareCollapse(getIntent());
            return;
        }
        forward(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (isCollapseIntent(intent)) {
            // Keep the original target intent so expanding this Bubble again can forward the
            // same conversation after the task has been collapsed.
            prepareCollapse(intent);
            return;
        }
        setIntent(intent);
        resetLaunchState();
        forward(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!AppIconLaunchPolicy.isLaunchedFromBubble(this)) return;
        if (collapseOnNextResume) {
            collapseOnNextResume = false;
            String reason = collapseReason == null
                    ? "WeChat conversation returned"
                    : collapseReason;
            collapseReason = null;
            collapseBubble(reason);
            return;
        }
        if (relaunchOnNextResume) {
            relaunchOnNextResume = false;
            forward(getIntent());
            return;
        }
        if (!shouldCollapseAfterTarget(targetLaunchSubmitted, pausedAfterTargetLaunch)) {
            return;
        }

        collapseBubble("Bridge resumed after conversation Back");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (loadingView != null) loadingView.stop();
        if (movingTaskToBack) {
            movingTaskToBack = false;
            return;
        }
        if (targetLaunchSubmitted) pausedAfterTargetLaunch = true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        boolean launchedFromBubble = AppIconLaunchPolicy.isLaunchedFromBubble(this);
        if (!shouldCollapseAfterTargetResult(
                requestCode,
                launchedFromBubble,
                targetLaunchSubmitted
        )) {
            return;
        }

        Log.i(TAG, "WeChat conversation returned to trampoline bridge"
                + ", taskId=" + getTaskId()
                + ", resultCode=" + resultCode
                + ", bridgeResumed=" + !pausedAfterTargetLaunch);
        boolean targetStillCoversBridge = pausedAfterTargetLaunch;
        targetLaunchSubmitted = false;
        pausedAfterTargetLaunch = false;
        requestCollapseAtBridgeRoot(targetStillCoversBridge);
    }

    static PendingIntent createBubbleIntent(
            Context context,
            PendingIntent target,
            String conversationId,
            CharSequence conversationTitle
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null;
        if (!isUsableTarget(target)) return null;
        Intent bridge = new Intent(context, TrampolineBridgeActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .setData(bridgeUriForConversation(conversationId))
                .putExtra(EXTRA_TARGET, target)
                .putExtra(EXTRA_CONVERSATION_ID, conversationId)
                .putExtra(
                        EXTRA_CONVERSATION_TITLE,
                        conversationTitle == null ? "" : conversationTitle.toString()
                );
        return PendingIntent.getActivity(
                context,
                requestCodeFor(conversationId),
                bridge,
                pendingIntentFlags()
        );
    }

    static boolean shouldUseTarget(
            boolean trampolineEnabled,
            String creatorPackage,
            boolean activity
    ) {
        return trampolineEnabled && WECHAT_PACKAGE.equals(creatorPackage) && activity;
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

    static boolean shouldCollapseAfterTargetResult(
            int requestCode,
            boolean launchedFromBubble,
            boolean targetLaunchSubmitted
    ) {
        return requestCode == TARGET_RESULT_REQUEST_CODE
                && launchedFromBubble
                && targetLaunchSubmitted;
    }

    static int targetResultRequestCode() {
        return TARGET_RESULT_REQUEST_CODE;
    }

    static boolean shouldCollapseImmediatelyWhenClearTopUnavailable(
            boolean targetStillCoversBridge
    ) {
        return !targetStillCoversBridge;
    }

    static boolean requestCollapseForTask(
            Context context,
            int taskId,
            String conversationId,
            String reason
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context == null || taskId < 0) {
            return false;
        }
        ActivityManager manager = context.getSystemService(ActivityManager.class);
        if (manager == null) return false;

        Intent collapseIntent = new Intent(context, TrampolineBridgeActivity.class)
                .setAction(ACTION_COLLAPSE_AFTER_TARGET_RESULT)
                .setData(bridgeUriForConversation(conversationId))
                .putExtra(EXTRA_COLLAPSE_REASON, reason)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        for (ActivityManager.AppTask appTask : manager.getAppTasks()) {
            try {
                if (appTask.getTaskInfo().taskId != taskId) continue;
                appTask.startActivity(context, collapseIntent, null);
                Log.i(TAG, "requested trampoline bridge clear-top"
                        + ", taskId=" + taskId
                        + ", reason=" + reason);
                return true;
            } catch (RuntimeException e) {
                Log.w(TAG, "failed to clear trampoline task above bridge"
                        + ", taskId=" + taskId
                        + ", reason=" + reason, e);
                return false;
            }
        }
        Log.w(TAG, "trampoline task unavailable for clear-top"
                + ", taskId=" + taskId
                + ", reason=" + reason);
        return false;
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

        String conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID);
        if (loadingView != null) {
            loadingView.showConversation(
                    conversationId,
                    intent.getStringExtra(EXTRA_CONVERSATION_TITLE)
            );
            loadingView.runAfterNextDraw(() -> launchBubbledTarget(
                    target,
                    conversationId
            ));
            return;
        }
        launchBubbledTarget(target, conversationId);
    }

    private void launchBubbledTarget(
            PendingIntent target,
            String conversationId
    ) {
        int taskId = getTaskId();
        TrampolineBubbleSessionState.onEmbeddedLaunchStarted(
                taskId,
                conversationId
        );
        BubbleLaunchCleanup.suppressAppCancelForTrampolineLaunch(this, conversationId);
        Log.i(TAG, "forwarding WeChat conversation from trampoline bubble"
                + ", taskId=" + taskId
                + ", conversation=" + conversationId
                + ", immutable=" + target.isImmutable()
                + ", persistentBridgeRoot=true");

        // Keep this activity as the bubble task root. When the WeChat child returns, onResume()
        // moves the task to the background so System UI collapses rather than removes the bubble.
        targetLaunchSubmitted = true;
        pausedAfterTargetLaunch = false;
        try {
            startIntentSenderForResult(
                    target.getIntentSender(),
                    TARGET_RESULT_REQUEST_CODE,
                    null,
                    AppIconLaunchPolicy.taskSeparatingFlags(),
                    0,
                    0,
                    pendingIntentLaunchOptions()
            );
            // Keep the rewritten notification as the Bubble host, but start the next incoming
            // notification from a fresh unread history for this conversation only.
            WeChatNotificationService.markConversationOpenedFromBubble(conversationId);
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
        collapseOnNextResume = false;
        collapseReason = null;
    }

    private void requestCollapseAtBridgeRoot(boolean targetStillCoversBridge) {
        collapseOnNextResume = true;
        collapseReason = "WeChat conversation result";
        if (requestCollapseForTask(
                this,
                getTaskId(),
                getIntent().getStringExtra(EXTRA_CONVERSATION_ID),
                collapseReason
        )) {
            return;
        }

        if (!shouldCollapseImmediatelyWhenClearTopUnavailable(targetStillCoversBridge)) {
            Log.i(TAG, "defer trampoline collapse until Bridge resumes"
                    + ", taskId=" + getTaskId());
            return;
        }

        collapseOnNextResume = false;
        collapseReason = null;
        collapseBubble("conversation result fallback");
    }

    private void prepareCollapse(Intent intent) {
        collapseOnNextResume = true;
        collapseReason = intent == null
                ? null
                : intent.getStringExtra(EXTRA_COLLAPSE_REASON);
    }

    private void collapseBubble(String reason) {
        targetLaunchSubmitted = false;
        pausedAfterTargetLaunch = false;
        movingTaskToBack = true;
        relaunchOnNextResume = true;
        boolean moved = moveTaskToBack(true);
        Log.i(TAG, "collapsing trampoline bubble"
                + ", taskId=" + getTaskId()
                + ", reason=" + reason
                + ", moved=" + moved);
        if (!moved) {
            movingTaskToBack = false;
            relaunchOnNextResume = false;
        }
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

    private static boolean isCollapseIntent(Intent intent) {
        return intent != null
                && ACTION_COLLAPSE_AFTER_TARGET_RESULT.equals(intent.getAction());
    }

    private static int conversationHash(String conversationId) {
        return conversationId == null ? 0 : conversationId.hashCode();
    }

    private static android.net.Uri bridgeUriForConversation(String conversationId) {
        return android.net.Uri.parse(
                "wemodern://trampoline-bridge/host/"
                        + Integer.toUnsignedString(conversationHash(conversationId), 16)
        );
    }
}
