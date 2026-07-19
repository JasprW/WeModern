package me.jaspr.wemodern;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.app.Person;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.util.SparseArray;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

/** Records unmodified WeChat notifications for later classifier design. */
final class WeChatNotificationCapture {
    static final String TAG = "WeModern.Capture";
    static final String FILE_NAME = "wechat_notification_capture.jsonl";
    private static final String PREVIOUS_FILE_NAME = "wechat_notification_capture.previous.jsonl";
    private static final long MAX_FILE_BYTES = 16L * 1024L * 1024L;
    private static final int LOGCAT_CHUNK_LENGTH = 3000;
    private static final int MAX_VALUE_DEPTH = 5;
    private static final Object FILE_LOCK = new Object();

    private WeChatNotificationCapture() {
    }

    static File captureFile(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    static void record(
            Context context,
            String event,
            StatusBarNotification sbn,
            NotificationListenerService.RankingMap rankingMap,
            Integer removalReason
    ) {
        if (sbn == null) return;
        try {
            JSONObject root = new JSONObject();
            root.put("schemaVersion", 1);
            root.put("capturedAt", System.currentTimeMillis());
            root.put("event", event);
            if (removalReason != null) root.put("removalReason", removalReason);
            root.put("statusBarNotification", statusBarNotificationJson(sbn));
            root.put("notification", notificationJson(sbn.getNotification()));
            JSONObject ranking = rankingJson(sbn.getKey(), rankingMap);
            if (ranking != null) root.put("ranking", ranking);

            String jsonLine = root.toString();
            append(context, jsonLine);
            logChunked(event + " key=" + sbn.getKey() + " " + jsonLine);
        } catch (RuntimeException | JSONException e) {
            Log.w(TAG, "failed to capture WeChat notification event=" + event, e);
        }
    }

    private static JSONObject statusBarNotificationJson(StatusBarNotification sbn)
            throws JSONException {
        JSONObject json = new JSONObject();
        json.put("package", sbn.getPackageName());
        if (Build.VERSION.SDK_INT >= 29) {
            json.put("opPackage", sbn.getOpPkg());
            json.put("uid", sbn.getUid());
        }
        json.put("id", sbn.getId());
        json.put("tag", nullable(sbn.getTag()));
        json.put("key", sbn.getKey());
        json.put("groupKey", sbn.getGroupKey());
        json.put("overrideGroupKey", nullable(sbn.getOverrideGroupKey()));
        json.put("postTime", sbn.getPostTime());
        json.put("user", String.valueOf(sbn.getUser()));
        json.put("ongoing", sbn.isOngoing());
        json.put("clearable", sbn.isClearable());
        return json;
    }

    private static JSONObject notificationJson(Notification notification) throws JSONException {
        JSONObject json = new JSONObject();
        if (notification == null) return json;
        json.put("toString", notification.toString());
        json.put("when", notification.when);
        json.put("flags", notification.flags);
        json.put("flagsHex", String.format("0x%08x", notification.flags));
        json.put("priority", notification.priority);
        json.put("defaults", notification.defaults);
        json.put("visibility", notification.visibility);
        json.put("category", nullable(notification.category));
        json.put("channelId", nullable(notification.getChannelId()));
        json.put("number", notification.number);
        json.put("color", notification.color);
        json.put("legacyIconResourceId", notification.icon);
        json.put("iconLevel", notification.iconLevel);
        json.put("sound", notification.sound == null
                ? JSONObject.NULL : notification.sound.toString());
        json.put("audioAttributes", notification.audioAttributes == null
                ? JSONObject.NULL : notification.audioAttributes.toString());
        json.put("vibrate", valueJson(notification.vibrate, 0));
        json.put("ledArgb", notification.ledARGB);
        json.put("ledOnMs", notification.ledOnMS);
        json.put("ledOffMs", notification.ledOffMS);
        json.put("tickerText", nullable(notification.tickerText));
        json.put("group", nullable(notification.getGroup()));
        json.put("sortKey", nullable(notification.getSortKey()));
        json.put("groupSummary",
                (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0);
        json.put("groupAlertBehavior", notification.getGroupAlertBehavior());
        json.put("badgeIconType", notification.getBadgeIconType());
        json.put("shortcutId", nullable(notification.getShortcutId()));
        json.put("settingsText", nullable(notification.getSettingsText()));
        if (Build.VERSION.SDK_INT >= 29) {
            json.put("locusId", notification.getLocusId() == null
                    ? JSONObject.NULL : notification.getLocusId().getId());
        }
        json.put("smallIcon", iconJson(notification.getSmallIcon()));
        json.put("largeIcon", iconJson(notification.getLargeIcon()));
        json.put("contentIntent", pendingIntentJson(notification.contentIntent));
        json.put("deleteIntent", pendingIntentJson(notification.deleteIntent));
        json.put("fullScreenIntent", pendingIntentJson(notification.fullScreenIntent));
        json.put("contentView", remoteViewsJson(notification.contentView));
        json.put("bigContentView", remoteViewsJson(notification.bigContentView));
        json.put("headsUpContentView", remoteViewsJson(notification.headsUpContentView));
        json.put("actions", actionsJson(notification.actions));
        json.put("extras", bundleJson(notification.extras, 0));
        if (Build.VERSION.SDK_INT >= 29) {
            json.put("bubbleMetadata", valueJson(notification.getBubbleMetadata(), 0));
        }
        return json;
    }

    private static JSONObject rankingJson(
            String key,
            NotificationListenerService.RankingMap rankingMap
    ) throws JSONException {
        if (rankingMap == null) return null;
        NotificationListenerService.Ranking ranking =
                new NotificationListenerService.Ranking();
        if (!rankingMap.getRanking(key, ranking)) return null;
        JSONObject json = new JSONObject();
        json.put("rank", ranking.getRank());
        json.put("importance", ranking.getImportance());
        json.put("importanceExplanation", nullable(ranking.getImportanceExplanation()));
        json.put("ambient", ranking.isAmbient());
        json.put("matchesInterruptionFilter", ranking.matchesInterruptionFilter());
        json.put("suppressedVisualEffects", ranking.getSuppressedVisualEffects());
        if (Build.VERSION.SDK_INT >= 28) json.put("suspended", ranking.isSuspended());
        if (Build.VERSION.SDK_INT >= 29) {
            json.put("canBubble", ranking.canBubble());
            json.put("lastAudiblyAlertedMillis", ranking.getLastAudiblyAlertedMillis());
        }
        if (Build.VERSION.SDK_INT >= 31) {
            json.put("isConversation", ranking.isConversation());
        }
        NotificationChannel channel = ranking.getChannel();
        if (channel != null) {
            JSONObject channelJson = new JSONObject();
            channelJson.put("id", channel.getId());
            channelJson.put("name", nullable(channel.getName()));
            channelJson.put("description", nullable(channel.getDescription()));
            channelJson.put("importance", channel.getImportance());
            channelJson.put("sound", channel.getSound() == null
                    ? JSONObject.NULL : channel.getSound().toString());
            channelJson.put("vibrationEnabled", channel.shouldVibrate());
            channelJson.put("lightsEnabled", channel.shouldShowLights());
            channelJson.put("showBadge", channel.canShowBadge());
            json.put("channel", channelJson);
        }
        return json;
    }

    private static JSONArray actionsJson(Notification.Action[] actions) throws JSONException {
        JSONArray array = new JSONArray();
        if (actions == null) return array;
        for (Notification.Action action : actions) {
            if (action == null) {
                array.put(JSONObject.NULL);
                continue;
            }
            JSONObject json = new JSONObject();
            json.put("title", nullable(action.title));
            json.put("icon", iconJson(action.getIcon()));
            json.put("intent", pendingIntentJson(action.actionIntent));
            json.put("allowGeneratedReplies", action.getAllowGeneratedReplies());
            if (Build.VERSION.SDK_INT >= 28) {
                json.put("semanticAction", action.getSemanticAction());
            }
            if (Build.VERSION.SDK_INT >= 29) json.put("contextual", action.isContextual());
            if (Build.VERSION.SDK_INT >= 31) {
                json.put("authenticationRequired", action.isAuthenticationRequired());
            }
            json.put("extras", bundleJson(action.getExtras(), 0));
            json.put("remoteInputs", valueJson(action.getRemoteInputs(), 0));
            array.put(json);
        }
        return array;
    }

    private static Object iconJson(Icon icon) throws JSONException {
        if (icon == null) return JSONObject.NULL;
        JSONObject json = new JSONObject();
        json.put("toString", icon.toString());
        if (Build.VERSION.SDK_INT >= 28) {
            json.put("type", icon.getType());
            if (icon.getType() == Icon.TYPE_RESOURCE) {
                json.put("resourcePackage", icon.getResPackage());
                json.put("resourceId", icon.getResId());
            }
        }
        return json;
    }

    private static Object pendingIntentJson(PendingIntent intent) throws JSONException {
        if (intent == null) return JSONObject.NULL;
        JSONObject json = new JSONObject();
        json.put("creatorPackage", nullable(intent.getCreatorPackage()));
        json.put("creatorUid", intent.getCreatorUid());
        if (Build.VERSION.SDK_INT >= 31) json.put("immutable", intent.isImmutable());
        json.put("toString", intent.toString());
        return json;
    }

    private static Object remoteViewsJson(RemoteViews views) throws JSONException {
        if (views == null) return JSONObject.NULL;
        JSONObject json = new JSONObject();
        json.put("package", views.getPackage());
        json.put("layoutId", views.getLayoutId());
        json.put("toString", views.toString());
        return json;
    }

    private static JSONObject bundleJson(Bundle bundle, int depth) throws JSONException {
        JSONObject json = new JSONObject();
        if (bundle == null) return json;
        for (String key : bundle.keySet()) {
            try {
                json.put(key, valueJson(bundle.get(key), depth + 1));
            } catch (RuntimeException e) {
                json.put(key, errorJson(e));
            }
        }
        return json;
    }

    private static Object valueJson(Object value, int depth) throws JSONException {
        if (value == null) return JSONObject.NULL;
        if (depth > MAX_VALUE_DEPTH) return typedString(value, "max-depth");
        if (value instanceof String || value instanceof Boolean
                || value instanceof Integer || value instanceof Long
                || value instanceof Double || value instanceof Float
                || value instanceof Short || value instanceof Byte) {
            return value;
        }
        if (value instanceof CharSequence) return value.toString();
        if (value instanceof Bundle) return bundleJson((Bundle) value, depth);
        if (value instanceof Icon) return iconJson((Icon) value);
        if (value instanceof PendingIntent) return pendingIntentJson((PendingIntent) value);
        if (value instanceof Bitmap) {
            Bitmap bitmap = (Bitmap) value;
            JSONObject json = new JSONObject();
            json.put("type", value.getClass().getName());
            json.put("width", bitmap.getWidth());
            json.put("height", bitmap.getHeight());
            json.put("config", bitmap.getConfig() == null
                    ? JSONObject.NULL : bitmap.getConfig().name());
            json.put("byteCount", bitmap.getByteCount());
            return json;
        }
        if (Build.VERSION.SDK_INT >= 28 && value instanceof Person) {
            Person person = (Person) value;
            JSONObject json = new JSONObject();
            json.put("type", value.getClass().getName());
            json.put("name", nullable(person.getName()));
            json.put("key", nullable(person.getKey()));
            json.put("uri", nullable(person.getUri()));
            json.put("bot", person.isBot());
            json.put("important", person.isImportant());
            json.put("icon", iconJson(person.getIcon()));
            return json;
        }
        if (value instanceof SparseArray) {
            SparseArray<?> sparseArray = (SparseArray<?>) value;
            JSONObject json = new JSONObject();
            for (int i = 0; i < sparseArray.size(); i++) {
                json.put(String.valueOf(sparseArray.keyAt(i)),
                        valueJson(sparseArray.valueAt(i), depth + 1));
            }
            return json;
        }
        if (value instanceof Collection) {
            JSONArray array = new JSONArray();
            for (Object item : (Collection<?>) value) {
                array.put(valueJson(item, depth + 1));
            }
            return array;
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            JSONArray array = new JSONArray();
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                array.put(valueJson(Array.get(value, i), depth + 1));
            }
            return array;
        }
        if (value instanceof Parcelable) return typedString(value, "parcelable");
        return typedString(value, "object");
    }

    private static JSONObject typedString(Object value, String encoding) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("type", value.getClass().getName());
        json.put("encoding", encoding);
        json.put("value", String.valueOf(value));
        return json;
    }

    private static JSONObject errorJson(Throwable throwable) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("error", throwable.getClass().getName());
        json.put("message", nullable(throwable.getMessage()));
        return json;
    }

    private static Object nullable(Object value) {
        return value == null ? JSONObject.NULL : String.valueOf(value);
    }

    private static void append(Context context, String jsonLine) {
        synchronized (FILE_LOCK) {
            File current = captureFile(context);
            if (current.length() >= MAX_FILE_BYTES) rotate(context, current);
            try (FileOutputStream output = new FileOutputStream(current, true)) {
                output.write(jsonLine.getBytes(StandardCharsets.UTF_8));
                output.write('\n');
            } catch (IOException e) {
                Log.w(TAG, "failed to append capture file " + current, e);
            }
        }
    }

    private static void rotate(Context context, File current) {
        File previous = new File(context.getFilesDir(), PREVIOUS_FILE_NAME);
        if (previous.exists() && !previous.delete()) {
            Log.w(TAG, "unable to delete previous capture file " + previous);
            return;
        }
        if (!current.renameTo(previous)) {
            Log.w(TAG, "unable to rotate capture file " + current);
        }
    }

    private static void logChunked(String text) {
        int parts = Math.max(1, (text.length() + LOGCAT_CHUNK_LENGTH - 1)
                / LOGCAT_CHUNK_LENGTH);
        for (int part = 0; part < parts; part++) {
            int start = part * LOGCAT_CHUNK_LENGTH;
            int end = Math.min(text.length(), start + LOGCAT_CHUNK_LENGTH);
            Log.i(TAG, "part=" + (part + 1) + "/" + parts + " "
                    + text.substring(start, end));
        }
    }
}
