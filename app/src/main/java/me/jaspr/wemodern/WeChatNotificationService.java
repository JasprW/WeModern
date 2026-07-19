package me.jaspr.wemodern;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.json.JSONException;
import org.json.JSONObject;

public class WeChatNotificationService extends NotificationListenerService {
    private static final String TAG = "WeModern";
    private static final String WECHAT_PACKAGE = "com.tencent.mm";
    private static final String WECHAT_VOIP_CHANNEL = "voip_notify_channel_new_id";
    private static final String MESSAGE_GROUP_KEY = "wechat.rewritten";
    private static final String REPLACEMENT_STORE = "sync_removal_replacements";
    private static final String STORAGE_SEPARATOR = "\u001f";
    private static final long ORIGINAL_SNOOZE_DURATION_MS = 24L * 60L * 60L * 1000L;
    private static final long INCOMING_CALL_SNOOZE_DURATION_MS = 2_000L;
    private static final long CONNECTED_CALL_STATUS_SNOOZE_DURATION_MS = 2_000L;
    private static final long SELF_HIDDEN_CANCEL_MARKER_DURATION_MS = 1_000L;
    private static final long CONNECTED_STATUS_FALLBACK_DELAY_MS = 10_000L;
    private static final long LEGACY_AUDIO_MODE_POLL_INTERVAL_MS = 250L;
    private static final long CALL_RINGING_TIMEOUT_MS = 2L * 60L * 1000L;
    private static final int MAX_HISTORY = 8;
    private static final int MESSAGE_GROUP_SUMMARY_ID = 0x5747534d;
    static final int CALL_REPLACEMENT_ID = 0x5743414c;
    private static final int REASON_LISTENER_CANCEL = 10;
    private static final int REASON_SNOOZED = 18;
    private static final NotificationPostDeduplicator POST_DEDUPLICATOR =
            new NotificationPostDeduplicator();
    private static volatile WeChatNotificationService activeInstance;
    private final Map<String, ArrayDeque<Message>> histories = new HashMap<>();
    private final Map<String, String> originalToConversation = new HashMap<>();
    private final Map<CancelEventKey, ReplacementRecord> replacementsByCancelEvent = new HashMap<>();
    private final Set<String> selfHiddenOriginals = new HashSet<>();
    private final CallSession callSession = new CallSession();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable legacyAudioModePoll = new Runnable() {
        @Override
        public void run() {
            if (!callAudioModeMonitoring) return;
            observeCurrentCallAudioMode("poll");
            if (callAudioModeMonitoring) {
                mainHandler.postDelayed(this, LEGACY_AUDIO_MODE_POLL_INTERVAL_MS);
            }
        }
    };
    private NotificationCancelLogWatcher cancelLogWatcher;
    private SharedPreferences.OnSharedPreferenceChangeListener debugPreferencesListener;
    private AudioManager audioManager;
    private AudioManager.OnModeChangedListener callAudioModeListener;
    private boolean callAudioModeMonitoring;
    private boolean callAudioModeListenerRegistered;
    private int lastObservedCallAudioMode = Integer.MIN_VALUE;
    private boolean listenerConnected;
    private boolean rewriteEnabled;
    private boolean incomingCallForeground;

    @Override
    public void onCreate() {
        super.onCreate();
        activeInstance = this;
        audioManager = getSystemService(AudioManager.class);
        rewriteEnabled = NotificationDebugPreferences.isRewriteEnabled(this);
        debugPreferencesListener = (sharedPreferences, key) ->
                mainHandler.post(this::refreshRewriteMode);
        NotificationDebugPreferences.registerListener(this, debugPreferencesListener);
        if (rewriteEnabled) {
            startRewriteInfrastructure();
        }
        Log.i(TAG, "service created"
                + ", sdk=" + Build.VERSION.SDK_INT
                + ", captureLogging="
                + NotificationDebugPreferences.isCaptureLoggingEnabled(this)
                + ", rewrite=" + rewriteEnabled);
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        listenerConnected = false;
        Log.i(TAG, "listener disconnected");
    }

    @Override
    public void onDestroy() {
        if (debugPreferencesListener != null) {
            NotificationDebugPreferences.unregisterListener(this, debugPreferencesListener);
        }
        removeCallReplacement();
        if (activeInstance == this) activeInstance = null;
        stopRewriteInfrastructure();
        super.onDestroy();
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        super.onTimeout(startId, fgsType);
        if (Build.VERSION.SDK_INT >= 34
                && fgsType == ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE) {
            endCallSession("incoming CallStyle foreground-service timeout");
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        listenerConnected = true;
        StatusBarNotification[] active = getActiveNotifications();
        captureActiveWeChatNotifications(active);
        if (!isRewriteCurrentlyEnabled()) {
            clearRewriteState();
            Log.i(TAG, "listener connected, rewrites disabled");
            return;
        }
        Log.i(TAG, "listener connected");
        processActiveNotificationsForRewrite(active);
    }

    private void processActiveNotificationsForRewrite(StatusBarNotification[] active) {
        if (active == null) return;
        for (StatusBarNotification sbn : active) {
            if (WECHAT_PACKAGE.equals(sbn.getPackageName())) {
                logWeChatPosted(sbn, "active-scan");
                handleWeChatNotification(sbn, true);
            } else if (getPackageName().equals(sbn.getPackageName())) {
                restoreShortcutContentIntent(sbn.getNotification());
            }
        }
        ConversationShortcuts.refreshIcons(this);
        cleanupOrphanedReplacements(active);
        mainHandler.postDelayed(this::cleanupCurrentOrphanedReplacements, 2000);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn, RankingMap rankingMap) {
        if (WECHAT_PACKAGE.equals(sbn.getPackageName()) && isCaptureLoggingEnabled()) {
            WeChatNotificationCapture.record(this, "POSTED", sbn, rankingMap, null);
        }
        if (!isRewriteCurrentlyEnabled()) return;
        handleNotificationPostedForRewrite(sbn);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (WECHAT_PACKAGE.equals(sbn.getPackageName()) && isCaptureLoggingEnabled()) {
            WeChatNotificationCapture.record(this, "POSTED", sbn, null, null);
        }
        if (!isRewriteCurrentlyEnabled()) return;
        handleNotificationPostedForRewrite(sbn);
    }

    private void handleNotificationPostedForRewrite(StatusBarNotification sbn) {
        if (getPackageName().equals(sbn.getPackageName())) {
            Log.i(TAG, "self notification posted"
                    + ", id=" + sbn.getId()
                    + ", tag=" + sbn.getTag()
                    + ", channel=" + channelId(sbn.getNotification())
                    + ", key=" + sbn.getKey());
            cleanupOrphanedReplacement(sbn);
            return;
        }
        if (!WECHAT_PACKAGE.equals(sbn.getPackageName())) return;
        logWeChatPosted(sbn, "posted");
        handleWeChatNotification(sbn, false);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (WECHAT_PACKAGE.equals(sbn.getPackageName()) && isCaptureLoggingEnabled()) {
            WeChatNotificationCapture.record(this, "REMOVED", sbn, null, 0);
        }
        if (!isRewriteCurrentlyEnabled()) return;
        handleWeChatNotificationRemoved(sbn, 0);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap, int reason) {
        if (WECHAT_PACKAGE.equals(sbn.getPackageName()) && isCaptureLoggingEnabled()) {
            WeChatNotificationCapture.record(this, "REMOVED", sbn, rankingMap, reason);
        }
        if (!isRewriteCurrentlyEnabled()) return;
        handleWeChatNotificationRemoved(sbn, reason);
    }

    private boolean isCaptureLoggingEnabled() {
        return NotificationDebugPreferences.isCaptureLoggingEnabled(this);
    }

    private boolean isRewriteCurrentlyEnabled() {
        return NotificationDebugPreferences.isRewriteEnabled(this);
    }

    private void captureActiveWeChatNotifications(StatusBarNotification[] active) {
        if (!isCaptureLoggingEnabled() || active == null) return;
        RankingMap rankingMap = getCurrentRanking();
        for (StatusBarNotification sbn : active) {
            if (WECHAT_PACKAGE.equals(sbn.getPackageName())) {
                WeChatNotificationCapture.record(this, "ACTIVE_SCAN", sbn, rankingMap, null);
            }
        }
    }

    private void refreshRewriteMode() {
        boolean desired = NotificationDebugPreferences.isRewriteEnabled(this);
        if (desired == rewriteEnabled) return;
        rewriteEnabled = desired;
        if (rewriteEnabled) {
            startRewriteInfrastructure();
            if (listenerConnected) {
                StatusBarNotification[] active = getActiveNotifications();
                captureActiveWeChatNotifications(active);
                processActiveNotificationsForRewrite(active);
            }
        } else {
            stopRewriteInfrastructure();
            clearRewriteState();
        }
        Log.i(TAG, "notification rewrite " + (rewriteEnabled ? "enabled" : "disabled"));
    }

    private void startRewriteInfrastructure() {
        NotificationChannels.ensure(this);
        ConversationBubbles.syncActiveNotifications(this);
        if (cancelLogWatcher != null) return;
        cancelLogWatcher = new NotificationCancelLogWatcher(
                this::handleNotificationCancelLog,
                this::handleActivityEvent
        );
        cancelLogWatcher.start();
    }

    private void stopRewriteInfrastructure() {
        stopConnectedAudioModeMonitoring();
        if (cancelLogWatcher == null) return;
        cancelLogWatcher.stop();
        cancelLogWatcher = null;
    }

    private void clearRewriteState() {
        stopConnectedAudioModeMonitoring();
        removeCallReplacement();
        getSystemService(NotificationManager.class).cancelAll();
        histories.clear();
        originalToConversation.clear();
        replacementsByCancelEvent.clear();
        selfHiddenOriginals.clear();
        callSession.reset();
        replacementStore().edit().clear().apply();
    }

    private void handleWeChatNotificationRemoved(StatusBarNotification sbn, int reason) {
        if (getPackageName().equals(sbn.getPackageName())) {
            if (isMessageGroupChild(sbn.getNotification())) {
                ConversationBubbleStore.remove(sbn.getNotification().getShortcutId());
                forgetReplacementsForReplacementId(sbn.getId());
                mainHandler.post(this::removeMessageGroupSummaryIfNotNeeded);
            }
            return;
        }
        if (!WECHAT_PACKAGE.equals(sbn.getPackageName())) return;
        String key = sbn.getKey();
        if (reason == REASON_LISTENER_CANCEL) {
            selfHiddenOriginals.remove(key);
            Log.d(TAG, "wechat hidden by notification listener: key=" + key);
            return;
        }
        if (selfHiddenOriginals.remove(key)) {
            Log.d(TAG, "wechat hidden by self: key=" + key + ", reason=" + reasonName(reason));
            return;
        }
        if (handleCapturedCallRemoval(sbn, reason)) return;
        if (shouldClearBubblesAfterWeChatRemoval(reason)) {
            BubbleLaunchCleanup.clearAfterWeChatAppCancel(this);
        }
        if (WeChatParser.isVoipNotification(sbn.getNotification())) {
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(voipReplacementId(sbn));
            forgetReplacement(CancelEventKey.from(sbn));
            Log.i(TAG, "cancel rewritten voip notification: key=" + key + ", reason=" + reasonName(reason));
            return;
        }
        String conversationKey = originalToConversation.get(key);
        if (conversationKey != null) {
            originalToConversation.remove(key);
            histories.remove(conversationKey);
            ConversationBubbleStore.remove(conversationKey);
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(stableId(conversationKey));
            forgetReplacement(CancelEventKey.from(sbn));
            Log.i(TAG, "cancel rewritten wechat notification after original removal"
                    + ", key=" + key
                    + ", conversation=" + conversationKey
                    + ", reason=" + reasonName(reason));
            return;
        }
        Log.d(TAG, "wechat removed: key=" + key + ", reason=" + reasonName(reason));
    }

    private void handleNotificationCancelLog(String pkg, int id, String tag, int userId, int reason) {
        if (!WECHAT_PACKAGE.equals(pkg)) return;
        CancelEventKey eventKey = new CancelEventKey(userId, id, tag);
        if (callSession.matchesIncoming(eventKey)) {
            cancelIncomingCallReplacement("wechat incoming notification app-cancelled");
            callSession.clearIncoming();
            return;
        }
        if (callSession.matchesStatus(eventKey)) {
            endCallSession("wechat call status app-cancelled");
            return;
        }
        BubbleLaunchCleanup.clearAfterWeChatAppCancel(this);
        ReplacementRecord replacement = replacementsByCancelEvent.get(eventKey);
        if (replacement == null) {
            replacement = restoreReplacement(eventKey);
            if (replacement == null) {
                Log.i(TAG, "wechat app cancel log missed replacement"
                        + ", userId=" + userId
                        + ", id=" + id
                        + ", tag=" + tag
                        + ", reason=" + reasonName(reason)
                        + ", tracked=" + replacementsByCancelEvent.size());
                return;
            }
            Log.i(TAG, "restored replacement from storage"
                    + ", userId=" + userId
                    + ", id=" + id
                    + ", tag=" + tag
                    + ", replacementId=" + replacement.replacementId
                    + ", conversation=" + replacement.conversationKey);
        }

        replacementsByCancelEvent.remove(eventKey);
        removePersistedReplacement(eventKey);
        originalToConversation.remove(replacement.originalKey);
        if (replacement.conversationKey != null) {
            histories.remove(replacement.conversationKey);
            ConversationBubbleStore.remove(replacement.conversationKey);
        }
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(replacement.replacementId);
        Log.i(TAG, "cancel rewritten wechat notification after app cancel log"
                + ", key=" + replacement.originalKey
                + ", conversation=" + replacement.conversationKey
                + ", reason=" + reasonName(reason));
    }

    private void handleActivityEvent(NotificationCancelLogWatcher.ActivityEvent event) {
        if (event.type == NotificationCancelLogWatcher.ActivityEvent.TYPE_CREATED) {
            if (TrampolineBubbleHost.isHostNotificationPosted()
                    && WeChatLauncher.isBubbleRootActivity(
                            event.componentName,
                            event.action
                    )) {
                TrampolineBubbleSessionState.onEmbeddedLaunchStarted(event.taskId);
                Log.i(TAG, "tracking trampoline bubble task"
                        + ", taskId=" + event.taskId
                        + ", activity=" + event.componentName);
            }
            return;
        }
        if (event.type == NotificationCancelLogWatcher.ActivityEvent.TYPE_RESUMED) {
            WeChatForegroundState.onForegroundActivityChanged(
                    event.taskId,
                    event.componentName
            );
            if (callSession.incomingVisible && isWeChatComponent(event.componentName)) {
                cancelIncomingCallReplacement("wechat activity resumed");
            }
            return;
        }
        if (event.type != NotificationCancelLogWatcher.ActivityEvent.TYPE_TASK_REMOVED) return;
        WeChatForegroundState.onTaskRemoved(event.taskId);
        if (TrampolineBubbleSessionState.onTaskRemoved(event.taskId)) {
            mainHandler.post(() -> {
                Log.i(TAG, "clearing trampoline host after bubble task removed"
                        + ", taskId=" + event.taskId);
                TrampolineBubbleHost.clear(this);
            });
        }
    }

    private boolean handleCapturedCallRemoval(StatusBarNotification sbn, int reason) {
        CancelEventKey eventKey = CancelEventKey.from(sbn);
        WeChatCallClassifier.Signal signal = WeChatCallClassifier.classify(sbn.getNotification());
        boolean incoming = callSession.matchesIncoming(eventKey)
                || signal == WeChatCallClassifier.Signal.INCOMING;
        boolean status = callSession.matchesStatus(eventKey)
                || signal == WeChatCallClassifier.Signal.WAITING_STATUS
                || signal == WeChatCallClassifier.Signal.CONNECTED_STATUS;
        if (status) {
            if (reason == REASON_SNOOZED) {
                Log.d(TAG, "ignore snoozed connected-call source removal: key=" + sbn.getKey());
                return true;
            }
            endCallSession("wechat call status removed, reason=" + reasonName(reason));
            return true;
        }
        if (incoming) {
            if (!callSession.connected) {
                cancelIncomingCallReplacement(
                        "wechat incoming notification removed, reason=" + reasonName(reason));
            }
            callSession.clearIncoming();
            return true;
        }
        return false;
    }

    private void handleWeChatNotification(StatusBarNotification sbn, boolean fromActiveScan) {
        if (!isRewriteCurrentlyEnabled()) {
            Log.w(TAG, "blocked rewrite while notification rewriting is disabled"
                    + ", key=" + sbn.getKey());
            return;
        }
        Notification original = sbn.getNotification();
        if (!POST_DEDUPLICATOR.shouldProcess(sbn.getKey(), sbn.getPostTime(), original.when)) {
            Log.d(TAG, "skip duplicate wechat callback: key=" + sbn.getKey()
                    + ", postTime=" + sbn.getPostTime()
                    + ", when=" + original.when);
            return;
        }
        if (handleCapturedCallNotification(sbn, original, fromActiveScan)) {
            return;
        }
        ParsedVoipNotification voip = WeChatParser.parseVoip(this, sbn);
        if (voip != null) {
            int replacementId = voipReplacementId(sbn);
            rememberReplacement(sbn, null, replacementId);
            postVoipReplacement(sbn, voip, original);
            hideOriginal(sbn);
            Log.i(TAG, "rewritten wechat voip notification"
                    + ", fromActiveScan=" + fromActiveScan
                    + ", title=" + voip.title
                    + ", text=" + voip.text
                    + ", key=" + sbn.getKey());
            return;
        }

        ParsedNotification parsed = WeChatParser.parse(sbn);
        if (parsed == null) {
            Log.d(TAG, "skip wechat notification: key=" + sbn.getKey()
                    + ", channel=" + channelId(original)
                    + ", flags=" + original.flags);
            return;
        }
        originalToConversation.put(sbn.getKey(), parsed.conversationKey);
        rememberReplacement(sbn, parsed.conversationKey, stableId(parsed.conversationKey));
        // Remove the original as early as possible so its high-importance heads-up surface
        // has the smallest possible window before the quiet replacement and bubble are ready.
        hideOriginal(sbn);

        ArrayDeque<Message> history = histories.computeIfAbsent(parsed.conversationKey, key -> new ArrayDeque<>());
        Icon originalSenderIcon = resolveSenderIcon(original);
        Icon circularSenderIcon = ConversationShortcuts.circleAvatarIcon(this, originalSenderIcon);
        Message message = new Message(parsed.sender, parsed.text, sbn.getPostTime(), circularSenderIcon);
        if (!containsRecentDuplicate(history, message)) {
            history.addLast(message);
            while (history.size() > MAX_HISTORY) history.removeFirst();
        }

        long avatarRevision = ConversationShortcuts.updateAvatarCache(
                this,
                parsed.conversationKey,
                originalSenderIcon
        );
        ConversationBubblePreferences.record(
                this,
                parsed.conversationKey,
                parsed.title,
                parsed.groupConversation,
                sbn.getPostTime(),
                avatarRevision
        );
        ConversationShortcuts.publish(
                this,
                parsed.conversationKey,
                parsed.title,
                original.contentIntent
        );
        ConversationBubbleState bubbleState = ConversationBubbleStore.get(parsed.conversationKey);
        if (bubbleState == null) {
            bubbleState = ConversationBubbleState.create(
                    parsed.conversationKey,
                    parsed.title,
                    original.contentIntent
            );
        } else {
            bubbleState = bubbleState.withMetadata(parsed.title, original.contentIntent);
        }
        bubbleState = bubbleState.append(
                parsed.sender,
                parsed.text,
                sbn.getPostTime(),
                original.contentIntent
        );
        ConversationBubbleStore.update(bubbleState);
        postReplacement(
                sbn,
                parsed,
                history,
                original,
                circularSenderIcon,
                originalSenderIcon,
                bubbleState
        );
        hideDuplicateOriginals(parsed, sbn.getKey());
        Log.i(TAG, "rewritten wechat notification"
                + ", fromActiveScan=" + fromActiveScan
                + ", title=" + parsed.title
                + ", sender=" + parsed.sender
                + ", text=" + parsed.text
                + ", key=" + sbn.getKey());
    }

    private void postReplacement(StatusBarNotification sbn, ParsedNotification parsed,
                                 ArrayDeque<Message> history, Notification original,
                                 Icon senderIcon, Icon originalSenderIcon,
                                 ConversationBubbleState bubbleState) {
        CharSequence contentText = parsed.groupConversation
                ? parsed.sender + ": " + parsed.text
                : parsed.text;
        Icon smallIcon = resolveSmallIcon(original);
        String messageChannelId = NotificationChannels.messageChannelId(
                this,
                parsed.conversationKey
        );
        Notification.Builder builder = new Notification.Builder(this, messageChannelId)
                .setSmallIcon(smallIcon)
                .setContentTitle(parsed.title)
                .setContentText(contentText)
                // A group notification's per-message sender is still shown as text, but
                // the source notification supplies only one avatar, not one per sender.
                .setStyle(buildMessageStyle(parsed, history, !parsed.groupConversation))
                .setWhen(sbn.getPostTime())
                .setShowWhen(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setVisibility(NotificationChannels.messageLockscreenVisibility())
                .setColor(0xff33b332)
                .setGroup(MESSAGE_GROUP_KEY);
        if (senderIcon != null) {
            builder.setLargeIcon(senderIcon);
        }

        PendingIntent contentIntent = original.contentIntent;
        PendingIntent launchIntent = WeChatLaunchProxyActivity.wrap(
                this,
                "message:" + parsed.conversationKey,
                contentIntent
        );
        if (launchIntent != null) builder.setContentIntent(launchIntent);
        PendingIntent deleteIntent = original.deleteIntent;
        if (deleteIntent != null) builder.setDeleteIntent(deleteIntent);

        if (Build.VERSION.SDK_INT >= 29) {
            builder.setAllowSystemGeneratedContextualActions(true);
        }
        builder.setShortcutId(parsed.conversationKey);
        if (Build.VERSION.SDK_INT >= 29) {
            builder.setLocusId(new android.content.LocusId(parsed.conversationKey));
        }
        Icon bubbleIcon = senderIcon != null
                ? senderIcon
                : Icon.createWithResource(this, R.mipmap.ic_launcher);
        ConversationBubbles.applyTo(
                this,
                builder,
                bubbleState,
                bubbleIcon,
                stableId(parsed.conversationKey)
        );

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Notification replacementNotification;
        try {
            Log.i(TAG, "post replacement notification"
                    + ", replacementId=" + stableId(parsed.conversationKey)
                    + ", conversation=" + parsed.conversationKey
                    + ", originalKey=" + sbn.getKey());
            replacementNotification = builder.build();
            nm.notify(stableId(parsed.conversationKey), replacementNotification);
        } catch (RuntimeException e) {
            Log.w(TAG, "failed to post with original icons, falling back", e);
            smallIcon = Icon.createWithResource(this, R.drawable.ic_wechat_notification_small);
            builder.setSmallIcon(smallIcon);
            builder.setLargeIcon((Icon) null);
            builder.setStyle(buildMessageStyle(parsed, history, false));
            Log.i(TAG, "post fallback replacement notification"
                    + ", replacementId=" + stableId(parsed.conversationKey)
                    + ", conversation=" + parsed.conversationKey
                    + ", originalKey=" + sbn.getKey());
            replacementNotification = builder.build();
            nm.notify(stableId(parsed.conversationKey), replacementNotification);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            TrampolineBubbleHost.update(
                    this,
                    replacementNotification,
                    parsed.conversationKey,
                    parsed.title,
                    originalSenderIcon != null ? originalSenderIcon : bubbleIcon
            );
        }
        if (histories.size() >= 2) {
            postMessageGroupSummary(contentIntent, smallIcon, messageChannelId);
        }
    }

    private void postMessageGroupSummary(
            PendingIntent contentIntent,
            Icon smallIcon,
            String messageChannelId
    ) {
        Notification.Builder builder = new Notification.Builder(this, messageChannelId)
                .setSmallIcon(smallIcon)
                .setContentTitle(getString(R.string.channel_wechat_messages))
                .setContentText(getString(R.string.app_name))
                .setGroup(MESSAGE_GROUP_KEY)
                .setGroupSummary(true)
                .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setVisibility(NotificationChannels.messageLockscreenVisibility())
                .setColor(0xff33b332);
        PendingIntent launchIntent = WeChatLaunchProxyActivity.wrap(
                this,
                "message-summary",
                contentIntent
        );
        if (launchIntent != null) builder.setContentIntent(launchIntent);
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                .notify(MESSAGE_GROUP_SUMMARY_ID, builder.build());
    }

    private Notification.MessagingStyle buildMessageStyle(ParsedNotification parsed,
                                                          ArrayDeque<Message> history,
                                                          boolean includeSenderIcons) {
        Notification.MessagingStyle style = new Notification.MessagingStyle(getString(R.string.app_name))
                .setConversationTitle(parsed.title);
        if (Build.VERSION.SDK_INT >= 28) {
            style.setGroupConversation(parsed.groupConversation);
        }
        for (Message message : history) {
            if (Build.VERSION.SDK_INT >= 28) {
                Person.Builder sender = new Person.Builder().setName(message.sender);
                if (includeSenderIcons && message.senderIcon != null) {
                    sender.setIcon(message.senderIcon);
                }
                style.addMessage(new Notification.MessagingStyle.Message(
                        message.text, message.when, sender.build()));
            } else {
                style.addMessage(message.text, message.when, message.sender);
            }
        }
        return style;
    }

    private void hideDuplicateOriginals(ParsedNotification parsed, String currentKey) {
        StatusBarNotification[] active = getActiveNotifications();
        if (active == null) return;
        for (StatusBarNotification activeSbn : active) {
            if (!WECHAT_PACKAGE.equals(activeSbn.getPackageName())) continue;
            if (TextUtils.equals(currentKey, activeSbn.getKey())) continue;
            ParsedNotification activeParsed = WeChatParser.parse(activeSbn);
            if (activeParsed == null) continue;
            if (!TextUtils.equals(parsed.conversationKey, activeParsed.conversationKey)) continue;
            originalToConversation.put(activeSbn.getKey(), activeParsed.conversationKey);
            rememberReplacement(activeSbn, activeParsed.conversationKey, stableId(activeParsed.conversationKey));
            hideOriginal(activeSbn);
            Log.i(TAG, "hide duplicate original wechat notification"
                    + ", key=" + activeSbn.getKey()
                    + ", conversation=" + activeParsed.conversationKey);
        }
    }

    private void rememberReplacement(StatusBarNotification sbn, String conversationKey, int replacementId) {
        CancelEventKey eventKey = CancelEventKey.from(sbn);
        ReplacementRecord replacement = new ReplacementRecord(sbn.getKey(), conversationKey, replacementId);
        replacementsByCancelEvent.put(eventKey, replacement);
        persistReplacement(eventKey, replacement);
        Log.i(TAG, "remember replacement"
                + ", userId=" + eventKey.userId
                + ", id=" + eventKey.id
                + ", tag=" + eventKey.tag
                + ", replacementId=" + replacementId
                + ", conversation=" + conversationKey);
    }

    private void cleanupOrphanedReplacements(StatusBarNotification[] active) {
        for (StatusBarNotification sbn : active) {
            cleanupOrphanedReplacement(sbn);
        }
    }

    private void cleanupCurrentOrphanedReplacements() {
        StatusBarNotification[] active = getActiveNotifications();
        if (active != null) cleanupOrphanedReplacements(active);
    }

    private void cleanupOrphanedReplacement(StatusBarNotification sbn) {
        if (!getPackageName().equals(sbn.getPackageName())) return;
        String channel = channelId(sbn.getNotification());
        if (!NotificationChannels.isMessageChannel(channel)
                && !NotificationChannels.WECHAT_CALLS.equals(channel)) {
            return;
        }
        if (sbn.getId() == CALL_REPLACEMENT_ID) {
            if (callSession.hasActiveState() || hasPersistedReplacement(CALL_REPLACEMENT_ID)) {
                Log.d(TAG, "keep active rewritten call notification: key=" + sbn.getKey());
            } else {
                removeCallReplacement();
                Log.i(TAG, "cancel orphaned rewritten call notification: key=" + sbn.getKey());
            }
            return;
        }
        boolean groupSummary = isMessageGroupSummary(sbn.getNotification());
        boolean testNotification = isTestNotificationId(sbn.getId());
        boolean persistedReplacement = hasPersistedReplacement(sbn.getId());
        if (shouldKeepSelfNotification(
                sbn.getId(),
                groupSummary,
                testNotification,
                persistedReplacement
        )) {
            Log.d(TAG, "keep owned notification"
                    + ", id=" + sbn.getId()
                    + ", channel=" + channel
                    + ", key=" + sbn.getKey());
            return;
        }
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(sbn.getId());
        Log.i(TAG, "cancel orphaned replacement notification"
                + ", id=" + sbn.getId()
                + ", channel=" + channel
                + ", key=" + sbn.getKey());
    }

    static boolean isTestNotificationId(int id) {
        return MessageTestNotifications.isTestId(id) || CallTestNotifications.isTestId(id);
    }

    static boolean shouldKeepSelfNotification(
            int notificationId,
            boolean groupSummary,
            boolean testNotification,
            boolean persistedReplacement
    ) {
        return TrampolineBubbleHost.isHostNotificationId(notificationId)
                || groupSummary
                || testNotification
                || persistedReplacement;
    }

    private static boolean isMessageGroupSummary(Notification notification) {
        return (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0
                && TextUtils.equals(MESSAGE_GROUP_KEY, notification.getGroup());
    }

    private static boolean isMessageGroupChild(Notification notification) {
        return (notification.flags & Notification.FLAG_GROUP_SUMMARY) == 0
                && TextUtils.equals(MESSAGE_GROUP_KEY, notification.getGroup());
    }

    private void removeMessageGroupSummaryIfNotNeeded() {
        StatusBarNotification[] active = getActiveNotifications();
        int childCount = 0;
        if (active != null) {
            for (StatusBarNotification sbn : active) {
                if (getPackageName().equals(sbn.getPackageName())
                        && isMessageGroupChild(sbn.getNotification())) {
                    childCount++;
                }
            }
        }
        if (!shouldRemoveMessageGroupSummary(childCount)) return;
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                .cancel(MESSAGE_GROUP_SUMMARY_ID);
    }

    static boolean shouldRemoveMessageGroupSummary(int childCount) {
        return childCount == 0;
    }

    private void forgetReplacement(CancelEventKey eventKey) {
        replacementsByCancelEvent.remove(eventKey);
        removePersistedReplacement(eventKey);
    }

    private void forgetReplacementsForReplacementId(int replacementId) {
        replacementsByCancelEvent.entrySet().removeIf(entry -> {
            ReplacementRecord replacement = entry.getValue();
            if (replacement.replacementId != replacementId) return false;
            originalToConversation.remove(replacement.originalKey);
            return true;
        });

        forgetPersistedReplacementsForReplacementId(this, replacementId);
    }

    static void forgetPersistedReplacementsForReplacementId(
            Context context,
            int replacementId
    ) {
        SharedPreferences preferences = context.getSharedPreferences(
                REPLACEMENT_STORE,
                Context.MODE_PRIVATE
        );
        SharedPreferences.Editor editor = preferences.edit();
        boolean changed = false;
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            if (!(entry.getValue() instanceof String)) continue;
            try {
                JSONObject json = new JSONObject((String) entry.getValue());
                if (json.optInt("replacementId") != replacementId) continue;
                editor.remove(entry.getKey());
                changed = true;
            } catch (JSONException ignored) {
            }
        }
        if (changed) editor.apply();
    }

    private void persistReplacement(CancelEventKey eventKey, ReplacementRecord replacement) {
        try {
            JSONObject json = new JSONObject()
                    .put("originalKey", replacement.originalKey)
                    .put("conversationKey", replacement.conversationKey == null
                            ? JSONObject.NULL : replacement.conversationKey)
                    .put("replacementId", replacement.replacementId);
            replacementStore().edit().putString(eventKey.storageKey(), json.toString()).apply();
        } catch (JSONException e) {
            Log.w(TAG, "failed to persist replacement for sync removal", e);
        }
    }

    private ReplacementRecord restoreReplacement(CancelEventKey eventKey) {
        String raw = replacementStore().getString(eventKey.storageKey(), null);
        if (raw == null) return null;
        try {
            JSONObject json = new JSONObject(raw);
            String originalKey = json.optString("originalKey", null);
            String conversationKey = json.isNull("conversationKey")
                    ? null : json.optString("conversationKey", null);
            int replacementId = json.getInt("replacementId");
            if (originalKey == null || replacementId == 0) return null;
            return new ReplacementRecord(originalKey, conversationKey, replacementId);
        } catch (JSONException e) {
            Log.w(TAG, "failed to restore replacement for sync removal", e);
            removePersistedReplacement(eventKey);
            return null;
        }
    }

    private boolean hasPersistedReplacement(int replacementId) {
        for (Object value : replacementStore().getAll().values()) {
            if (!(value instanceof String)) continue;
            try {
                JSONObject json = new JSONObject((String) value);
                if (json.optInt("replacementId") == replacementId) return true;
            } catch (JSONException ignored) {
            }
        }
        return false;
    }

    private void removePersistedReplacement(CancelEventKey eventKey) {
        replacementStore().edit().remove(eventKey.storageKey()).apply();
    }

    private SharedPreferences replacementStore() {
        return getSharedPreferences(REPLACEMENT_STORE, MODE_PRIVATE);
    }

    private boolean handleCapturedCallNotification(
            StatusBarNotification sbn,
            Notification original,
            boolean fromActiveScan
    ) {
        WeChatCallClassifier.Signal signal = WeChatCallClassifier.classify(original);
        if (signal == WeChatCallClassifier.Signal.NONE) return false;

        Bundle extras = original.extras;
        CharSequence title = extras == null
                ? null : extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = extras == null
                ? null : extras.getCharSequence(Notification.EXTRA_TEXT);
        String caller = WeChatCallClassifier.callerName(title, text);
        boolean video = WeChatCallClassifier.isVideo(title, text);

        if (signal == WeChatCallClassifier.Signal.INCOMING) {
            if (callSession.connected) {
                endCallSession("new incoming call replaced connected session");
            }
            callSession.incomingKey = sbn.getKey();
            callSession.incomingSource = CancelEventKey.from(sbn);
            callSession.updateCaller(caller);
            callSession.video = video;
            PendingIntent target = original.fullScreenIntent != null
                    ? original.fullScreenIntent : original.contentIntent;
            if (target != null) callSession.launchTarget = target;
            callSession.generation++;

            // Activity-log foreground tracking is asynchronous and can still report WeChat as
            // foreground after the user has left it. The incoming notification itself is the
            // authoritative signal: post first, then let the resume watcher remove our card if
            // WeChat really is visible.
            boolean posted = postIncomingCallReplacement();
            if (posted) {
                hideIncomingCallOriginal(sbn);
                scheduleIncomingCallTimeout(callSession.generation);
            }
            Log.i(TAG, "classified wechat incoming call"
                    + ", fromActiveScan=" + fromActiveScan
                    + ", caller=" + callSession.caller
                    + ", video=" + callSession.video
                    + ", posted=" + posted
                    + ", key=" + sbn.getKey());
            return true;
        }

        callSession.statusKey = sbn.getKey();
        callSession.statusSource = CancelEventKey.from(sbn);
        callSession.updateCaller(caller);
        callSession.video = callSession.video || video;
        if (original.contentIntent != null) callSession.launchTarget = original.contentIntent;

        if (signal == WeChatCallClassifier.Signal.WAITING_STATUS) {
            callSession.clearConnectedCandidate();
            stopConnectedAudioModeMonitoring();
            Log.i(TAG, "classified wechat pre-connect call status"
                    + ", fromActiveScan=" + fromActiveScan
                    + ", caller=" + callSession.caller
                    + ", flags=" + original.flags
                    + ", key=" + sbn.getKey());
            // The plain ongoing notification is not needed while the incoming CallStyle is
            // present. Listener cancellation does not suppress later posts that reuse this key,
            // so the 0x62 transition can still drive connection detection.
            hideCallStatusOriginal(sbn);
            return true;
        }

        if (!callSession.connected) {
            long observedAt = sbn.getPostTime() > 0
                    ? sbn.getPostTime() : System.currentTimeMillis();
            if (callSession.connectedCandidateAt <= 0L) {
                callSession.connectedCandidateAt = observedAt;
                int confirmationGeneration = ++callSession.connectionGeneration;
                scheduleConnectedStatusFallback(confirmationGeneration);
                Log.i(TAG, "defer first wechat foreground-service call status"
                        + ", fromActiveScan=" + fromActiveScan
                        + ", observedAt=" + observedAt
                        + ", key=" + sbn.getKey());
                startConnectedAudioModeMonitoring();
                return true;
            }
            if (!WeChatCallClassifier.isLaterConnectedUpdate(
                    callSession.connectedCandidateAt,
                    observedAt
            )) {
                Log.d(TAG, "ignore wechat foreground-transition status burst"
                        + ", firstObservedAt=" + callSession.connectedCandidateAt
                        + ", observedAt=" + observedAt
                        + ", key=" + sbn.getKey());
                return true;
            }
            long connectedAt = original.when > 0 ? original.when : observedAt;
            if (!promoteConnectedCall(connectedAt, "later notification update")) return true;
        }
        hideCallStatusOriginal(sbn);
        Log.i(TAG, "classified wechat connected call status"
                + ", fromActiveScan=" + fromActiveScan
                + ", caller=" + callSession.caller
                + ", flags=" + original.flags
                + ", connectedAt=" + callSession.connectedAt
                + ", key=" + sbn.getKey());
        return true;
    }

    private boolean promoteConnectedCall(long connectedAt, String source) {
        callSession.connected = true;
        callSession.connectedAt = connectedAt;
        callSession.connectedCandidateAt = 0L;
        callSession.connectionGeneration++;
        callSession.incomingVisible = false;
        callSession.incomingSource = null;
        if (postConnectedCallReplacement()) {
            stopConnectedAudioModeMonitoring();
            Log.i(TAG, "confirmed wechat connected call: " + source
                    + ", connectedAt=" + connectedAt);
            return true;
        }
        callSession.connected = false;
        cancelCallNotification(this);
        Log.w(TAG, "leave connected WeChat source visible after replacement failure");
        return false;
    }

    private boolean postIncomingCallReplacement() {
        PendingIntent target = callSession.launchTarget;
        if (target == null) {
            Log.w(TAG, "skip incoming CallStyle without a WeChat PendingIntent");
            return false;
        }
        try {
            String caller = callSession.displayCaller(this);
            Notification notification = buildIncomingCallNotification(
                    this,
                    caller,
                    callSession.video,
                    cachedCallAvatar(caller),
                    target
            );
            if (notification == null) {
                Log.w(TAG, "skip incoming CallStyle because proxy intents could not be created");
                return false;
            }
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            Log.i(TAG, "post incoming WeChat CallStyle"
                    + ", channel=" + NotificationChannels.WECHAT_CALLS);
            if (Build.VERSION.SDK_INT >= 37) {
                startForeground(
                        CALL_REPLACEMENT_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
                );
                incomingCallForeground = true;
            } else {
                notificationManager.notify(CALL_REPLACEMENT_ID, notification);
            }
            callSession.incomingVisible = true;
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "failed to post incoming WeChat CallStyle", e);
            return false;
        }
    }

    static Notification buildIncomingCallNotification(
            Context context,
            String caller,
            boolean video,
            Icon avatar,
            PendingIntent target
    ) {
        PendingIntent contentIntent = WeChatLaunchProxyActivity.wrap(
                context,
                "call:incoming:content",
                target
        );
        PendingIntent declineIntent = WeChatLaunchProxyActivity.wrap(
                context,
                "call:incoming:decline",
                target
        );
        PendingIntent answerIntent = WeChatLaunchProxyActivity.wrap(
                context,
                "call:incoming:answer",
                target
        );
        if (contentIntent == null || declineIntent == null || answerIntent == null) return null;

        Icon callIcon = Icon.createWithResource(
                context,
                video ? R.drawable.ic_material_videocam_24 : R.drawable.ic_material_call_24
        );
        Notification.Builder builder = new Notification.Builder(
                context,
                NotificationChannels.WECHAT_CALLS
        )
                .setSmallIcon(callIcon)
                .setContentTitle(caller)
                .setContentText(context.getString(video
                        ? R.string.wechat_incoming_video_call
                        : R.string.wechat_incoming_voice_call))
                .setShowWhen(false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_CALL)
                .setColor(0xff33b332)
                .setContentIntent(contentIntent);
        if (avatar != null) builder.setLargeIcon(avatar);

        if (Build.VERSION.SDK_INT >= 31) {
            Person.Builder personBuilder = new Person.Builder()
                    .setName(caller)
                    .setImportant(true);
            if (avatar != null) personBuilder.setIcon(avatar);
            Person callerPerson = personBuilder.build();
            builder.setStyle(Notification.CallStyle.forIncomingCall(
                    callerPerson,
                    declineIntent,
                    answerIntent
            ).setIsVideo(video));
            builder.addPerson(callerPerson);
        } else {
            builder.addAction(new Notification.Action.Builder(
                    callIcon,
                    context.getString(R.string.call_action_decline),
                    declineIntent
            ).build());
            builder.addAction(new Notification.Action.Builder(
                    callIcon,
                    context.getString(R.string.call_action_answer),
                    answerIntent
            ).build());
        }
        return builder.build();
    }

    private boolean postConnectedCallReplacement() {
        String caller = callSession.displayCaller(this);
        Icon callIcon = callIcon(callSession.video);
        Icon avatar = cachedCallAvatar(caller);
        Notification.Builder builder = new Notification.Builder(
                this,
                NotificationChannels.WECHAT_CALLS
        )
                .setSmallIcon(callIcon)
                .setContentTitle(caller)
                .setContentText(getString(R.string.wechat_call_in_progress))
                .setWhen(callSession.connectedAt)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_CALL)
                .setColor(0xff33b332);
        if (avatar != null) builder.setLargeIcon(avatar);

        PendingIntent launchIntent = WeChatLaunchProxyActivity.wrap(
                this,
                "call:ongoing",
                callSession.launchTarget
        );
        if (launchIntent != null) builder.setContentIntent(launchIntent);

        try {
            stopIncomingCallForeground();
            Notification notification = CallProgressStyle.build(builder, callIcon);
            if (Build.VERSION.SDK_INT >= 36) {
                Log.i(TAG, "voip promotedAllowed="
                        + getSystemService(NotificationManager.class).canPostPromotedNotifications()
                        + ", promotable=" + notification.hasPromotableCharacteristics());
            }
            getSystemService(NotificationManager.class).notify(CALL_REPLACEMENT_ID, notification);
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "failed to post connected WeChat Live Update", e);
            return false;
        }
    }

    private Icon cachedCallAvatar(String caller) {
        Bitmap bitmap = ConversationShortcuts.loadConversationAvatar(
                this,
                "wechat:" + caller
        );
        return bitmap == null ? null : Icon.createWithBitmap(bitmap);
    }

    private Icon callIcon(boolean video) {
        return Icon.createWithResource(
                this,
                video ? R.drawable.ic_material_videocam_24 : R.drawable.ic_material_call_24
        );
    }

    private void hideCallStatusOriginal(StatusBarNotification sbn) {
        if (shouldUseRollingCallStatusSnooze(
                callSession.connected,
                sbn.getNotification().flags
        )) {
            hideConnectedCallStatusOriginal(sbn.getKey());
            return;
        }
        cancelOriginal(sbn, "pre-connect call status");
    }

    private void hideConnectedCallStatusOriginal(String key) {
        if (key == null || !isRewriteCurrentlyEnabled()) return;
        try {
            selfHiddenOriginals.add(key);
            // Once the replacement Live Update exists, keep the protected WeChat FGS source
            // out of the shade with a renewable short snooze. The small window avoids carrying
            // this reused key into a later call as the previous 30-minute snooze did.
            POST_DEDUPLICATOR.forget(key);
            snoozeNotification(key, CONNECTED_CALL_STATUS_SNOOZE_DURATION_MS);
            mainHandler.postDelayed(
                    () -> selfHiddenOriginals.remove(key),
                    CONNECTED_CALL_STATUS_SNOOZE_DURATION_MS
                            + SELF_HIDDEN_CANCEL_MARKER_DURATION_MS
            );
            Log.d(TAG, "short-snooze connected wechat call status"
                    + ", durationMs=" + CONNECTED_CALL_STATUS_SNOOZE_DURATION_MS
                    + ", key=" + key);
        } catch (RuntimeException e) {
            selfHiddenOriginals.remove(key);
            Log.w(TAG, "failed to short-snooze connected call status: " + key, e);
        }
    }

    private void hideIncomingCallOriginal(StatusBarNotification sbn) {
        if (!isRewriteCurrentlyEnabled()) return;
        try {
            selfHiddenOriginals.add(sbn.getKey());
            // The source is ongoing and Android ignores listener cancel. A short renewable
            // snooze hides it without suppressing the same key for the next call for minutes.
            POST_DEDUPLICATOR.forget(sbn.getKey());
            snoozeNotification(sbn.getKey(), INCOMING_CALL_SNOOZE_DURATION_MS);
            Log.d(TAG, "short-snooze incoming wechat notification"
                    + ", durationMs=" + INCOMING_CALL_SNOOZE_DURATION_MS
                    + ", key=" + sbn.getKey());
        } catch (RuntimeException e) {
            selfHiddenOriginals.remove(sbn.getKey());
            Log.w(TAG, "failed to short-snooze incoming notification: " + sbn.getKey(), e);
        }
    }

    private void scheduleConnectedStatusFallback(int confirmationGeneration) {
        mainHandler.postDelayed(() -> {
            if (confirmationGeneration != callSession.connectionGeneration
                    || callSession.connected
                    || callSession.connectedCandidateAt <= 0L
                    || callSession.statusSource == null) {
                return;
            }
            if (promoteConnectedCall(
                    System.currentTimeMillis(),
                    "10-second foreground-service fallback"
            )) {
                hideConnectedCallStatusOriginal(callSession.statusKey);
            }
        }, CONNECTED_STATUS_FALLBACK_DELAY_MS);
    }

    private void startConnectedAudioModeMonitoring() {
        if (audioManager == null || callSession.connectedCandidateAt <= 0L) return;
        if (callAudioModeMonitoring) {
            observeCurrentCallAudioMode("notification update");
            return;
        }
        callAudioModeMonitoring = true;
        lastObservedCallAudioMode = Integer.MIN_VALUE;
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                callAudioModeListener = mode -> handleCallAudioMode(mode, "listener");
                audioManager.addOnModeChangedListener(
                        getMainExecutor(),
                        callAudioModeListener
                );
                callAudioModeListenerRegistered = true;
            } catch (RuntimeException e) {
                callAudioModeListener = null;
                callAudioModeListenerRegistered = false;
                Log.w(TAG, "failed to register audio mode listener; using polling", e);
            }
        }
        observeCurrentCallAudioMode("initial");
        if (callAudioModeMonitoring && !callAudioModeListenerRegistered) {
            mainHandler.postDelayed(
                    legacyAudioModePoll,
                    LEGACY_AUDIO_MODE_POLL_INTERVAL_MS
            );
        }
    }

    private void stopConnectedAudioModeMonitoring() {
        callAudioModeMonitoring = false;
        mainHandler.removeCallbacks(legacyAudioModePoll);
        if (Build.VERSION.SDK_INT >= 31
                && callAudioModeListenerRegistered
                && audioManager != null
                && callAudioModeListener != null) {
            try {
                audioManager.removeOnModeChangedListener(callAudioModeListener);
            } catch (RuntimeException e) {
                Log.w(TAG, "failed to unregister audio mode listener", e);
            }
        }
        callAudioModeListenerRegistered = false;
        callAudioModeListener = null;
        lastObservedCallAudioMode = Integer.MIN_VALUE;
    }

    private void observeCurrentCallAudioMode(String source) {
        if (!callAudioModeMonitoring || audioManager == null) return;
        try {
            handleCallAudioMode(audioManager.getMode(), source);
        } catch (RuntimeException e) {
            Log.w(TAG, "failed to read audio mode", e);
        }
    }

    private void handleCallAudioMode(int mode, String source) {
        if (!callAudioModeMonitoring) return;
        if (mode != lastObservedCallAudioMode) {
            lastObservedCallAudioMode = mode;
            Log.i(TAG, "wechat call audio mode"
                    + ", mode=" + CallAudioModePolicy.modeName(mode)
                    + ", source=" + source
                    + ", hasStatus=" + (callSession.statusSource != null)
                    + ", candidateAt=" + callSession.connectedCandidateAt);
        }
        if (!CallAudioModePolicy.shouldConfirmConnection(
                isRewriteCurrentlyEnabled(),
                callSession.connected,
                callSession.statusSource != null,
                callSession.connectedCandidateAt,
                mode
        )) {
            return;
        }
        if (promoteConnectedCall(
                System.currentTimeMillis(),
                "audio mode " + CallAudioModePolicy.modeName(mode)
        )) {
            hideConnectedCallStatusOriginal(callSession.statusKey);
        }
    }

    private void cancelOriginal(StatusBarNotification sbn, String source) {
        cancelOriginal(sbn.getKey(), source);
    }

    private void cancelOriginal(String key, String source) {
        if (key == null) return;
        if (!isRewriteCurrentlyEnabled()) {
            Log.w(TAG, "refusing to cancel original while notification rewriting is disabled"
                    + ", source=" + source
                    + ", key=" + key);
            return;
        }
        try {
            selfHiddenOriginals.add(key);
            cancelNotification(key);
            // A listener cancel can be ignored for an ongoing/FGS source. Do not let its marker
            // survive long enough to consume a later genuine APP_CANCEL for the same reused key.
            mainHandler.postDelayed(
                    () -> selfHiddenOriginals.remove(key),
                    SELF_HIDDEN_CANCEL_MARKER_DURATION_MS
            );
            Log.d(TAG, "cancel original wechat notification"
                    + ", source=" + source
                    + ", key=" + key);
        } catch (RuntimeException e) {
            selfHiddenOriginals.remove(key);
            Log.w(TAG, "failed to cancel original"
                    + ", source=" + source
                    + ", key=" + key, e);
        }
    }

    private void scheduleIncomingCallTimeout(int generation) {
        mainHandler.postDelayed(() -> {
            if (generation != callSession.generation || callSession.connected) return;
            endCallSession("incoming call timeout");
        }, CALL_RINGING_TIMEOUT_MS);
    }

    private void cancelIncomingCallReplacement(String reason) {
        if (callSession.connected || !callSession.incomingVisible) return;
        cancelCallNotification(this);
        callSession.incomingVisible = false;
        Log.i(TAG, "cancel incoming WeChat CallStyle: " + reason);
    }

    private void endCallSession(String reason) {
        stopConnectedAudioModeMonitoring();
        cancelCallNotification(this);
        callSession.reset();
        Log.i(TAG, "end rewritten WeChat call session: " + reason);
    }

    static void cancelCallNotification(Context context) {
        WeChatNotificationService service = activeInstance;
        if (service != null) {
            service.removeCallReplacement();
        } else {
            context.getSystemService(NotificationManager.class).cancel(CALL_REPLACEMENT_ID);
        }
    }

    private void removeCallReplacement() {
        stopIncomingCallForeground();
        getSystemService(NotificationManager.class).cancel(CALL_REPLACEMENT_ID);
    }

    private void stopIncomingCallForeground() {
        if (!incomingCallForeground) return;
        stopForeground(STOP_FOREGROUND_REMOVE);
        incomingCallForeground = false;
    }

    private static boolean isWeChatComponent(String componentName) {
        return componentName != null && (componentName.startsWith(WECHAT_PACKAGE + "/")
                || componentName.startsWith(WECHAT_PACKAGE + ":"));
    }

    private void postVoipReplacement(StatusBarNotification sbn, ParsedVoipNotification voip,
                                     Notification original) {
        long startedAt = original.when > 0 ? original.when : sbn.getPostTime();
        Icon callIcon = Icon.createWithResource(this,
                voip.video ? R.drawable.ic_material_videocam_24 : R.drawable.ic_material_call_24);
        Notification.Builder builder = new Notification.Builder(this, NotificationChannels.WECHAT_CALLS)
                .setSmallIcon(callIcon)
                .setContentTitle(voip.title)
                .setContentText(voip.text)
                .setWhen(startedAt)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_CALL)
                .setColor(0xff33b332);

        PendingIntent contentIntent = original.contentIntent;
        PendingIntent launchIntent = WeChatLaunchProxyActivity.wrap(
                this,
                "voip:" + sbn.getKey(),
                contentIntent
        );
        if (launchIntent != null) builder.setContentIntent(launchIntent);
        PendingIntent deleteIntent = original.deleteIntent;
        if (deleteIntent != null) builder.setDeleteIntent(deleteIntent);

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        try {
            Notification notification = CallProgressStyle.build(builder, callIcon);
            if (Build.VERSION.SDK_INT >= 36) {
                Log.i(TAG, "voip promotedAllowed="
                        + getSystemService(NotificationManager.class).canPostPromotedNotifications()
                        + ", promotable=" + notification.hasPromotableCharacteristics());
            }
            nm.notify(voipReplacementId(sbn), notification);
        } catch (RuntimeException e) {
            Log.w(TAG, "failed to post voip with material small icon, falling back", e);
            Icon fallbackIcon = Icon.createWithResource(this, R.drawable.ic_material_call_24);
            builder.setSmallIcon(fallbackIcon);
            Notification notification = CallProgressStyle.build(builder, fallbackIcon);
            nm.notify(voipReplacementId(sbn), notification);
        }
    }

    private static Icon resolveSmallIcon(Notification original) {
        Icon icon = original.getSmallIcon();
        if (icon != null) return icon;
        return Icon.createWithResource("me.jaspr.wemodern", R.drawable.ic_wechat_notification_small);
    }

    private static Icon resolveSenderIcon(Notification original) {
        return original.getLargeIcon();
    }

    private static void restoreShortcutContentIntent(Notification notification) {
        String shortcutId = notification.getShortcutId();
        if (!TextUtils.isEmpty(shortcutId) && notification.contentIntent != null) {
            ConversationShortcuts.registerContentIntent(shortcutId, notification.contentIntent);
        }
    }

    private void logWeChatPosted(StatusBarNotification sbn, String source) {
        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;
        CharSequence title = extras == null ? null : extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = extras == null ? null : extras.getCharSequence(Notification.EXTRA_TEXT);
        Log.i(TAG, "wechat notification " + source
                + ", id=" + sbn.getId()
                + ", tag=" + sbn.getTag()
                + ", channel=" + channelId(notification)
                + ", flags=" + notification.flags
                + ", title=" + title
                + ", text=" + text
                + ", key=" + sbn.getKey());
    }

    private void hideOriginal(StatusBarNotification sbn) {
        hideOriginal(sbn, ORIGINAL_SNOOZE_DURATION_MS);
    }

    private void hideOriginal(StatusBarNotification sbn, long snoozeDurationMs) {
        if (!isRewriteCurrentlyEnabled()) {
            Log.w(TAG, "refusing to hide original while notification rewriting is disabled"
                    + ", key=" + sbn.getKey());
            return;
        }
        try {
            selfHiddenOriginals.add(sbn.getKey());
            if (shouldSnoozeOriginal(sbn.getNotification().flags)) {
                snoozeNotification(sbn.getKey(), snoozeDurationMs);
                Log.d(TAG, "snooze protected original wechat notification"
                        + ", durationMs=" + snoozeDurationMs
                        + ", key=" + sbn.getKey());
            } else {
                cancelNotification(sbn.getKey());
                Log.d(TAG, "cancel original wechat notification: " + sbn.getKey());
            }
        } catch (RuntimeException e) {
            selfHiddenOriginals.remove(sbn.getKey());
            Log.w(TAG, "failed to hide original: " + sbn.getKey(), e);
        }
    }

    static boolean shouldSnoozeOriginal(int flags) {
        return (flags & (Notification.FLAG_FOREGROUND_SERVICE | Notification.FLAG_NO_CLEAR)) != 0;
    }

    static boolean shouldUseRollingCallStatusSnooze(boolean connected, int flags) {
        return connected && shouldSnoozeOriginal(flags);
    }

    static boolean shouldClearBubblesAfterWeChatRemoval(int reason) {
        return reason == 0
                || reason == NotificationListenerService.REASON_APP_CANCEL
                || reason == NotificationListenerService.REASON_APP_CANCEL_ALL;
    }

    private static String reasonName(int reason) {
        if (reason == 0) return "unknown";
        if (reason == NotificationListenerService.REASON_CANCEL) return "cancel";
        if (reason == NotificationListenerService.REASON_APP_CANCEL) return "app_cancel";
        if (reason == NotificationListenerService.REASON_APP_CANCEL_ALL) return "app_cancel_all";
        if (reason == REASON_LISTENER_CANCEL) return "listener_cancel";
        return String.valueOf(reason);
    }

    private static boolean containsRecentDuplicate(ArrayDeque<Message> history, Message candidate) {
        for (Message message : history) {
            if (TextUtils.equals(message.sender, candidate.sender)
                    && TextUtils.equals(message.text, candidate.text)
                    && Math.abs(message.when - candidate.when) < 5000) {
                return true;
            }
        }
        return false;
    }

    private static int stableId(String key) {
        return 0x57000000 ^ key.hashCode();
    }

    private static int voipReplacementId(StatusBarNotification sbn) {
        return CALL_REPLACEMENT_ID;
    }

    private static int userIdFromKey(String key) {
        if (key == null) return 0;
        int sep = key.indexOf('|');
        if (sep <= 0) return 0;
        try {
            return Integer.parseInt(key.substring(0, sep));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static final class CancelEventKey {
        final int userId;
        final int id;
        final String tag;

        CancelEventKey(int userId, int id, String tag) {
            this.userId = userId;
            this.id = id;
            this.tag = NotificationCancelLogWatcher.normalizeTag(tag);
        }

        static CancelEventKey from(StatusBarNotification sbn) {
            return new CancelEventKey(userIdFromKey(sbn.getKey()), sbn.getId(), sbn.getTag());
        }

        String storageKey() {
            return userId + STORAGE_SEPARATOR + id + STORAGE_SEPARATOR + (tag == null ? "" : tag);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof CancelEventKey)) return false;
            CancelEventKey other = (CancelEventKey) obj;
            return userId == other.userId && id == other.id && TextUtils.equals(tag, other.tag);
        }

        @Override
        public int hashCode() {
            int result = userId;
            result = 31 * result + id;
            result = 31 * result + (tag != null ? tag.hashCode() : 0);
            return result;
        }
    }

    private static final class ReplacementRecord {
        final String originalKey;
        final String conversationKey;
        final int replacementId;

        ReplacementRecord(String originalKey, String conversationKey, int replacementId) {
            this.originalKey = originalKey;
            this.conversationKey = conversationKey;
            this.replacementId = replacementId;
        }
    }

    private static final class CallSession {
        String incomingKey;
        CancelEventKey incomingSource;
        String statusKey;
        CancelEventKey statusSource;
        String caller;
        boolean video;
        PendingIntent launchTarget;
        boolean incomingVisible;
        boolean connected;
        long connectedAt;
        long connectedCandidateAt;
        int generation;
        int connectionGeneration;

        void updateCaller(String candidate) {
            if (TextUtils.isEmpty(candidate)) return;
            boolean generic = "WeChat".equalsIgnoreCase(candidate) || "微信".equals(candidate);
            if (!generic || TextUtils.isEmpty(caller)) caller = candidate;
        }

        String displayCaller(Context context) {
            return TextUtils.isEmpty(caller)
                    ? context.getString(R.string.wechat_call_title)
                    : caller;
        }

        boolean matchesIncoming(CancelEventKey eventKey) {
            return incomingSource != null && incomingSource.equals(eventKey);
        }

        boolean matchesStatus(CancelEventKey eventKey) {
            return statusSource != null && statusSource.equals(eventKey);
        }

        boolean hasActiveState() {
            return incomingSource != null
                    || statusSource != null
                    || incomingVisible
                    || connected;
        }

        void clearIncoming() {
            incomingKey = null;
            incomingSource = null;
            incomingVisible = false;
        }

        void clearConnectedCandidate() {
            if (connectedCandidateAt <= 0L) return;
            connectedCandidateAt = 0L;
            connectionGeneration++;
        }

        void reset() {
            incomingKey = null;
            incomingSource = null;
            statusKey = null;
            statusSource = null;
            caller = null;
            video = false;
            launchTarget = null;
            incomingVisible = false;
            connected = false;
            connectedAt = 0L;
            connectedCandidateAt = 0L;
            generation++;
            connectionGeneration++;
        }
    }

    private static String channelId(Notification notification) {
        return Build.VERSION.SDK_INT >= 26 ? notification.getChannelId() : "";
    }

    private static final class Message {
        final CharSequence sender;
        final CharSequence text;
        final long when;
        final Icon senderIcon;

        Message(CharSequence sender, CharSequence text, long when, Icon senderIcon) {
            this.sender = sender;
            this.text = text;
            this.when = when;
            this.senderIcon = senderIcon;
        }
    }

    static final class ParsedNotification {
        final String conversationKey;
        final CharSequence title;
        final CharSequence sender;
        final CharSequence text;
        final boolean groupConversation;

        ParsedNotification(String conversationKey, CharSequence title, CharSequence sender,
                           CharSequence text, boolean groupConversation) {
            this.conversationKey = conversationKey;
            this.title = title;
            this.sender = sender;
            this.text = text;
            this.groupConversation = groupConversation;
        }
    }

    static final class ParsedVoipNotification {
        final CharSequence title;
        final CharSequence text;
        final boolean video;

        ParsedVoipNotification(CharSequence title, CharSequence text, boolean video) {
            this.title = title;
            this.text = text;
            this.video = video;
        }
    }

    static final class WeChatParser {
        static ParsedVoipNotification parseVoip(Context context, StatusBarNotification sbn) {
            Notification n = sbn.getNotification();
            if (!isVoipNotification(n)) return null;
            Bundle extras = n.extras;
            CharSequence title = extras == null ? null : extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = extras == null ? null : extras.getCharSequence(Notification.EXTRA_TEXT);
            if (isEmpty(title)) title = context.getString(R.string.wechat_call_title);
            if (isEmpty(text)) text = context.getString(R.string.wechat_call_in_progress);
            return new ParsedVoipNotification(title, text, isVideoCall(title, text));
        }

        static boolean isVideoCall(CharSequence title, CharSequence text) {
            String value = (clean(title) + " " + clean(text)).toLowerCase(Locale.ROOT);
            return value.contains("视频") || value.contains("視訊") || value.contains("video");
        }

        static boolean isVoipNotification(Notification n) {
            Bundle extras = n.extras;
            CharSequence title = extras == null
                    ? null : extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = extras == null
                    ? null : extras.getCharSequence(Notification.EXTRA_TEXT);
            return isVoipNotification(n.flags, channelId(n), title, text);
        }

        static boolean isVoipNotification(int flags, String channel, CharSequence title,
                                          CharSequence text) {
            if ((flags & Notification.FLAG_ONGOING_EVENT) == 0) return false;
            if (WECHAT_VOIP_CHANNEL.equals(channel)) return true;

            String value = (clean(title) + " " + clean(text)).toLowerCase(Locale.ROOT);
            boolean chineseCallInProgress = (value.contains("通话") || value.contains("通話"))
                    && (value.contains("通话中") || value.contains("通話中")
                    || value.contains("正在") || value.contains("进行中") || value.contains("進行中"));
            boolean englishCallInProgress = value.contains("call")
                    && (value.contains("in progress") || value.contains("ongoing")
                    || value.contains("active"));
            return chineseCallInProgress || englishCallInProgress;
        }

        static ParsedNotification parse(StatusBarNotification sbn) {
            Notification n = sbn.getNotification();
            if (isVoipNotification(n)) {
                return null;
            }
            Bundle extras = n.extras;
            if (extras == null) return null;
            CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
            if (isEmpty(title) || isEmpty(text)) return null;

            String channel = channelId(n);
            if (!isConversationChannel(channel, n)) return null;

            String titleString = clean(title);
            String body = clean(text);
            String ticker = n.tickerText == null ? "" : clean(n.tickerText);

            ParsedText parsedBody = parseBody(titleString, body);
            ParsedText parsedTicker = ticker.isEmpty() ? null : parseBody(titleString, ticker);
            ParsedText chosen = parsedTicker != null && !isEmpty(parsedTicker.text) ? parsedTicker : parsedBody;
            if (chosen == null || isEmpty(chosen.text)) return null;

            boolean group = chosen.sender != null && !TextUtils.equals(chosen.sender, titleString);
            CharSequence sender = group ? chosen.sender : titleString;
            String conversationKey = "wechat:" + titleString;
            return new ParsedNotification(conversationKey, titleString, sender, chosen.text, group);
        }

        private static boolean isConversationChannel(String channel, Notification n) {
            if ("message_channel_new_id".equals(channel)) return true;
            if ("message_dnd_mode_channel_id".equals(channel)) return true;
            if ("message_channel_id".equals(channel)) return true;
            if (n.tickerText != null) return true;
            return channel == null || channel.isEmpty();
        }

        private static ParsedText parseBody(String title, String body) {
            String value = stripCountPrefix(body).trim();
            String sender = null;
            String text = value;

            String titlePrefix = title + ": ";
            if (value.startsWith(titlePrefix)) {
                text = value.substring(titlePrefix.length()).trim();
            } else {
                int colon = value.indexOf(": ");
                if (colon > 0 && colon < 64) {
                    sender = value.substring(0, colon).trim();
                    text = value.substring(colon + 2).trim();
                }
            }
            if (sender == null || sender.isEmpty()) sender = title;
            return new ParsedText(sender, text);
        }

        private static String stripCountPrefix(String value) {
            if (value.startsWith("[") && value.contains("]")) {
                int end = value.indexOf(']');
                if (end > 0 && end < 12) {
                    return value.substring(end + 1);
                }
            }
            return value;
        }

        private static String clean(CharSequence value) {
            return value == null ? "" : value.toString().trim();
        }

        private static boolean isEmpty(CharSequence value) {
            return value == null || value.length() == 0;
        }
    }

    private static final class ParsedText {
        final String sender;
        final String text;

        ParsedText(String sender, String text) {
            this.sender = sender;
            this.text = text;
        }
    }
}
