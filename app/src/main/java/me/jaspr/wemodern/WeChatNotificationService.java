package me.jaspr.wemodern;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
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
    private static final int MAX_HISTORY = 8;
    private static final int TEST_VOICE_CALL_NOTIFICATION_ID = 101;
    private static final int TEST_VIDEO_CALL_NOTIFICATION_ID = 102;
    private static final int MESSAGE_GROUP_SUMMARY_ID = 0x5747534d;
    private final Map<String, ArrayDeque<Message>> histories = new HashMap<>();
    private final Map<String, String> originalToConversation = new HashMap<>();
    private final Map<CancelEventKey, ReplacementRecord> replacementsByCancelEvent = new HashMap<>();
    private final Set<String> selfHiddenOriginals = new HashSet<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private NotificationCancelLogWatcher cancelLogWatcher;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannels.ensure(this);
        cancelLogWatcher = new NotificationCancelLogWatcher(this::handleNotificationCancelLog);
        cancelLogWatcher.start();
        Log.i(TAG, "service created, sdk=" + Build.VERSION.SDK_INT);
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.i(TAG, "listener disconnected");
    }

    @Override
    public void onDestroy() {
        if (cancelLogWatcher != null) cancelLogWatcher.stop();
        super.onDestroy();
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.i(TAG, "listener connected");
        StatusBarNotification[] active = getActiveNotifications();
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
    public void onNotificationPosted(StatusBarNotification sbn) {
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
        handleWeChatNotificationRemoved(sbn, 0);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap, int reason) {
        handleWeChatNotificationRemoved(sbn, reason);
    }

    private void handleWeChatNotificationRemoved(StatusBarNotification sbn, int reason) {
        if (getPackageName().equals(sbn.getPackageName())) {
            if (isMessageGroupChild(sbn.getNotification())) {
                mainHandler.post(this::removeMessageGroupSummaryIfNotNeeded);
            }
            return;
        }
        if (!WECHAT_PACKAGE.equals(sbn.getPackageName())) return;
        String key = sbn.getKey();
        if (selfHiddenOriginals.remove(key)) {
            Log.d(TAG, "wechat hidden by self: key=" + key + ", reason=" + reasonName(reason));
            return;
        }
        if (WeChatParser.isVoipNotification(sbn.getNotification())) {
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(voipReplacementId(sbn));
            forgetReplacement(CancelEventKey.from(sbn));
            Log.i(TAG, "cancel rewritten voip notification: key=" + key + ", reason=" + reasonName(reason));
            return;
        }
        String conversationKey = originalToConversation.remove(key);
        if (conversationKey != null) {
            histories.remove(conversationKey);
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
        ReplacementRecord replacement = replacementsByCancelEvent.remove(eventKey);
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

        removePersistedReplacement(eventKey);
        originalToConversation.remove(replacement.originalKey);
        if (replacement.conversationKey != null) histories.remove(replacement.conversationKey);
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(replacement.replacementId);
        Log.i(TAG, "cancel rewritten wechat notification after app cancel log"
                + ", key=" + replacement.originalKey
                + ", conversation=" + replacement.conversationKey
                + ", reason=" + reasonName(reason));
    }

    private void handleWeChatNotification(StatusBarNotification sbn, boolean fromActiveScan) {
        Notification original = sbn.getNotification();
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
        ArrayDeque<Message> history = histories.computeIfAbsent(parsed.conversationKey, key -> new ArrayDeque<>());
        Icon originalSenderIcon = resolveSenderIcon(original);
        Icon fittedSenderIcon = ConversationShortcuts.fitAvatarIcon(this, originalSenderIcon);
        Message message = new Message(parsed.sender, parsed.text, sbn.getPostTime(), fittedSenderIcon);
        if (!containsRecentDuplicate(history, message)) {
            history.addLast(message);
            while (history.size() > MAX_HISTORY) history.removeFirst();
        }

        originalToConversation.put(sbn.getKey(), parsed.conversationKey);
        rememberReplacement(sbn, parsed.conversationKey, stableId(parsed.conversationKey));
        ConversationShortcuts.publish(this, parsed.conversationKey, parsed.title, originalSenderIcon,
                original.contentIntent);
        postReplacement(sbn, parsed, history, original, fittedSenderIcon);
        hideOriginal(sbn);
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
                                 Icon senderIcon) {
        CharSequence contentText = parsed.groupConversation
                ? parsed.sender + ": " + parsed.text
                : parsed.text;
        Icon smallIcon = resolveSmallIcon(original);
        Notification.Builder builder = new Notification.Builder(this, NotificationChannels.WECHAT_MESSAGES)
                .setSmallIcon(smallIcon)
                .setContentTitle(parsed.title)
                .setContentText(contentText)
                .setStyle(buildMessageStyle(parsed, history, true))
                .setWhen(sbn.getPostTime())
                .setShowWhen(true)
                .setDefaults(Notification.DEFAULT_ALL)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setColor(0xff33b332)
                .setGroup(MESSAGE_GROUP_KEY);
        if (senderIcon != null) {
            builder.setLargeIcon(senderIcon);
        }

        PendingIntent contentIntent = original.contentIntent;
        if (contentIntent != null) builder.setContentIntent(contentIntent);
        PendingIntent deleteIntent = original.deleteIntent;
        if (deleteIntent != null) builder.setDeleteIntent(deleteIntent);

        if (Build.VERSION.SDK_INT >= 29) {
            builder.setAllowSystemGeneratedContextualActions(true);
        }
        builder.setShortcutId(parsed.conversationKey);
        if (Build.VERSION.SDK_INT >= 29) {
            builder.setLocusId(new android.content.LocusId(parsed.conversationKey));
        }

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        try {
            Log.i(TAG, "post replacement notification"
                    + ", replacementId=" + stableId(parsed.conversationKey)
                    + ", conversation=" + parsed.conversationKey
                    + ", originalKey=" + sbn.getKey());
            nm.notify(stableId(parsed.conversationKey), builder.build());
        } catch (RuntimeException e) {
            Log.w(TAG, "failed to post with original icons, falling back", e);
            smallIcon = Icon.createWithResource(this, R.drawable.ic_wechat_scan_24dp);
            builder.setSmallIcon(smallIcon);
            builder.setLargeIcon((Icon) null);
            builder.setStyle(buildMessageStyle(parsed, history, false));
            Log.i(TAG, "post fallback replacement notification"
                    + ", replacementId=" + stableId(parsed.conversationKey)
                    + ", conversation=" + parsed.conversationKey
                    + ", originalKey=" + sbn.getKey());
            nm.notify(stableId(parsed.conversationKey), builder.build());
        }
        if (histories.size() >= 2) {
            postMessageGroupSummary(contentIntent, smallIcon);
        } else {
            nm.cancel(MESSAGE_GROUP_SUMMARY_ID);
        }
    }

    private void postMessageGroupSummary(PendingIntent contentIntent, Icon smallIcon) {
        Notification.Builder builder = new Notification.Builder(this, NotificationChannels.WECHAT_MESSAGES)
                .setSmallIcon(smallIcon)
                .setContentTitle(getString(R.string.channel_wechat_messages))
                .setContentText(getString(R.string.app_name))
                .setGroup(MESSAGE_GROUP_KEY)
                .setGroupSummary(true)
                .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setColor(0xff33b332);
        if (contentIntent != null) builder.setContentIntent(contentIntent);
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
        if (!NotificationChannels.WECHAT_MESSAGES.equals(channel)
                && !NotificationChannels.WECHAT_CALLS.equals(channel)) {
            return;
        }
        if (isMessageGroupSummary(sbn.getNotification())) {
            return;
        }
        if (isCallTestNotification(sbn)) {
            Log.d(TAG, "keep test call notification"
                    + ", id=" + sbn.getId()
                    + ", channel=" + channel
                    + ", key=" + sbn.getKey());
            return;
        }
        if (hasPersistedReplacement(sbn.getId())) {
            Log.d(TAG, "keep tracked replacement notification"
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

    private boolean isCallTestNotification(StatusBarNotification sbn) {
        int id = sbn.getId();
        return id == TEST_VOICE_CALL_NOTIFICATION_ID || id == TEST_VIDEO_CALL_NOTIFICATION_ID;
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
                    if (childCount >= 2) return;
                }
            }
        }
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                .cancel(MESSAGE_GROUP_SUMMARY_ID);
    }

    private void forgetReplacement(CancelEventKey eventKey) {
        replacementsByCancelEvent.remove(eventKey);
        removePersistedReplacement(eventKey);
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
        if (contentIntent != null) builder.setContentIntent(contentIntent);
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
        return Icon.createWithResource("me.jaspr.wemodern", R.drawable.ic_wechat_scan_24dp);
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
        try {
            selfHiddenOriginals.add(sbn.getKey());
            if (shouldSnoozeOriginal(sbn.getNotification().flags)) {
                snoozeNotification(sbn.getKey(), ORIGINAL_SNOOZE_DURATION_MS);
                Log.d(TAG, "snooze protected original wechat notification: " + sbn.getKey());
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

    private static String reasonName(int reason) {
        if (reason == 0) return "unknown";
        if (reason == NotificationListenerService.REASON_CANCEL) return "cancel";
        if (reason == NotificationListenerService.REASON_APP_CANCEL) return "app_cancel";
        if (reason == NotificationListenerService.REASON_APP_CANCEL_ALL) return "app_cancel_all";
        if (reason == 10) return "listener_cancel";
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
        return stableId("wechat:voip:" + sbn.getId() + ":" + sbn.getTag());
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
