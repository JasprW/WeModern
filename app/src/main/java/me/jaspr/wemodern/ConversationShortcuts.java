package me.jaspr.wemodern;

import android.app.PendingIntent;
import android.app.Person;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ConversationShortcuts {
    static final String EXTRA_CONVERSATION_ID = "me.jaspr.wemodern.extra.CONVERSATION_ID";

    private static final String TAG = "WeModern";
    private static final String STORE = "conversation_shortcuts";
    private static final String KEY_RECENT = "recent";
    private static final String ICON_DIRECTORY = "conversation_shortcut_icons";
    private static final int MAX_RECENT = 3;
    private static final int SHORTCUT_ICON_SIZE_PX = 192;
    private static final int ADAPTIVE_ICON_INSET_PX = 28;
    private static final Map<String, PendingIntent> CONTENT_INTENTS = new ConcurrentHashMap<>();

    private ConversationShortcuts() {
    }

    static void publish(Context context, String conversationId, CharSequence title, Icon senderIcon,
                        PendingIntent contentIntent) {
        if (contentIntent == null) {
            Log.w(TAG, "skip conversation shortcut without a direct content intent: "
                    + conversationId);
            return;
        }
        registerContentIntent(conversationId, contentIntent);

        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        if (manager == null || manager.getMaxShortcutCountPerActivity() == 0) return;

        String label = title == null ? "" : title.toString().trim();
        if (label.isEmpty()) label = context.getString(R.string.app_name);
        Icon currentShortcutIcon = persistIcon(context, conversationId, senderIcon);

        List<Entry> recent = load(context);
        recent.removeIf(entry -> conversationId.equals(entry.id));
        recent.add(0, new Entry(conversationId, label));
        while (recent.size() > MAX_RECENT) recent.remove(recent.size() - 1);
        save(context, recent);
        deleteStaleIcons(context, recent);

        int shortcutCount = Math.min(recent.size(),
                Math.min(MAX_RECENT, manager.getMaxShortcutCountPerActivity()));
        List<ShortcutInfo> shortcuts = new ArrayList<>(shortcutCount);
        for (int index = 0; index < shortcutCount; index++) {
            Entry entry = recent.get(index);
            Icon shortcutIcon = loadIcon(context, entry.id);
            if (shortcutIcon == null && entry.id.equals(conversationId)) {
                shortcutIcon = currentShortcutIcon;
            }
            shortcuts.add(buildShortcut(context, entry, index, shortcutIcon));
        }

        try {
            if (!manager.setDynamicShortcuts(shortcuts)) {
                Log.w(TAG, "launcher rejected dynamic conversation shortcuts");
            }
            manager.reportShortcutUsed(conversationId);
        } catch (IllegalArgumentException | IllegalStateException e) {
            Log.w(TAG, "failed to publish conversation shortcuts", e);
        }
    }

    static void registerContentIntent(String conversationId, PendingIntent contentIntent) {
        if (conversationId != null && contentIntent != null) {
            CONTENT_INTENTS.put(conversationId, contentIntent);
        }
    }

    static Icon fitAvatarIcon(Context context, Icon source) {
        if (source == null) return null;
        try {
            Drawable drawable = source.loadDrawable(context);
            Bitmap bitmap = renderDrawable(drawable, ADAPTIVE_ICON_INSET_PX);
            return bitmap == null ? source : Icon.createWithAdaptiveBitmap(bitmap);
        } catch (RuntimeException e) {
            Log.w(TAG, "failed to fit conversation avatar", e);
            return source;
        }
    }

    static void refreshIcons(Context context) {
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        if (manager == null) return;
        List<Entry> recent = load(context);
        List<ShortcutInfo> shortcuts = new ArrayList<>(recent.size());
        for (int index = 0; index < recent.size(); index++) {
            Entry entry = recent.get(index);
            shortcuts.add(buildShortcut(context, entry, index, loadIcon(context, entry.id)));
        }
        try {
            manager.updateShortcuts(shortcuts);
        } catch (IllegalArgumentException | IllegalStateException e) {
            Log.w(TAG, "failed to refresh conversation shortcut icons", e);
        }
    }

    static boolean openConversation(Context context, String conversationId) {
        PendingIntent contentIntent = CONTENT_INTENTS.get(conversationId);
        if (contentIntent == null) return false;
        try {
            contentIntent.send();
            ShortcutManager manager = context.getSystemService(ShortcutManager.class);
            if (manager != null) manager.reportShortcutUsed(conversationId);
            return true;
        } catch (PendingIntent.CanceledException e) {
            CONTENT_INTENTS.remove(conversationId, contentIntent);
            Log.w(TAG, "conversation pending intent is no longer valid: " + conversationId, e);
            return false;
        }
    }

    private static ShortcutInfo buildShortcut(Context context, Entry entry, int rank,
                                              Icon shortcutIcon) {
        Intent intent = new Intent(context, ConversationShortcutActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(EXTRA_CONVERSATION_ID, entry.id);
        if (shortcutIcon == null) {
            shortcutIcon = Icon.createWithResource(context, R.mipmap.ic_launcher);
        }
        ShortcutInfo.Builder builder = new ShortcutInfo.Builder(context, entry.id)
                .setActivity(new ComponentName(context, MainActivity.class))
                .setShortLabel(entry.label)
                .setLongLabel(entry.label)
                .setRank(rank)
                .setIcon(shortcutIcon)
                .setIntent(intent);
        if (Build.VERSION.SDK_INT >= 29) {
            builder.setLocusId(new android.content.LocusId(entry.id));
            builder.setPerson(new Person.Builder()
                    .setName(entry.label)
                    .setKey(entry.id)
                    .setIcon(shortcutIcon)
                    .build());
        }
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setLongLived(true);
            builder.setCategories(Collections.singleton(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION));
        }
        return builder.build();
    }

    private static Icon persistIcon(Context context, String conversationId, Icon source) {
        if (source == null) return null;
        try {
            Drawable drawable = source.loadDrawable(context);
            if (drawable == null) return null;
            Bitmap bitmap = renderDrawable(drawable, 0);
            if (bitmap == null) return null;

            File directory = iconDirectory(context);
            if (!directory.exists() && !directory.mkdirs()) {
                Log.w(TAG, "failed to create conversation shortcut icon directory");
                return fitBitmap(context, bitmap);
            }
            try (FileOutputStream output = new FileOutputStream(iconFile(context, conversationId))) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
            }
            return fitBitmap(context, bitmap);
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "failed to persist conversation shortcut icon", e);
            return null;
        }
    }

    private static Icon loadIcon(Context context, String conversationId) {
        Bitmap bitmap = BitmapFactory.decodeFile(iconFile(context, conversationId).getPath());
        return bitmap == null ? null : fitBitmap(context, bitmap);
    }

    private static Icon fitBitmap(Context context, Bitmap bitmap) {
        Bitmap fitted = renderDrawable(
                new BitmapDrawable(context.getResources(), bitmap), ADAPTIVE_ICON_INSET_PX);
        return fitted == null ? null : Icon.createWithAdaptiveBitmap(fitted);
    }

    private static Bitmap renderDrawable(Drawable drawable, int inset) {
        if (drawable == null) return null;
        Bitmap bitmap = Bitmap.createBitmap(
                SHORTCUT_ICON_SIZE_PX, SHORTCUT_ICON_SIZE_PX, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(inset, inset,
                SHORTCUT_ICON_SIZE_PX - inset, SHORTCUT_ICON_SIZE_PX - inset);
        drawable.draw(canvas);
        return bitmap;
    }

    private static void deleteStaleIcons(Context context, List<Entry> recent) {
        File[] files = iconDirectory(context).listFiles();
        if (files == null) return;
        for (File file : files) {
            boolean keep = false;
            for (Entry entry : recent) {
                if (file.equals(iconFile(context, entry.id))) {
                    keep = true;
                    break;
                }
            }
            if (!keep && !file.delete()) {
                Log.w(TAG, "failed to delete stale shortcut icon: " + file.getName());
            }
        }
    }

    private static File iconDirectory(Context context) {
        return new File(context.getFilesDir(), ICON_DIRECTORY);
    }

    private static File iconFile(Context context, String conversationId) {
        return new File(iconDirectory(context), Integer.toHexString(conversationId.hashCode()) + ".png");
    }

    private static List<Entry> load(Context context) {
        String raw = preferences(context).getString(KEY_RECENT, null);
        List<Entry> result = new ArrayList<>();
        if (raw == null) return result;
        try {
            JSONArray array = new JSONArray(raw);
            for (int index = 0; index < array.length() && result.size() < MAX_RECENT; index++) {
                JSONObject object = array.getJSONObject(index);
                String id = object.optString("id", "");
                String label = object.optString("label", "");
                if (!id.isEmpty() && !label.isEmpty()) result.add(new Entry(id, label));
            }
        } catch (JSONException e) {
            Log.w(TAG, "failed to restore recent conversation shortcuts", e);
        }
        return result;
    }

    private static void save(Context context, List<Entry> recent) {
        JSONArray array = new JSONArray();
        for (Entry entry : recent) {
            JSONObject object = new JSONObject();
            try {
                object.put("id", entry.id).put("label", entry.label);
                array.put(object);
            } catch (JSONException impossible) {
                throw new IllegalStateException(impossible);
            }
        }
        preferences(context).edit().putString(KEY_RECENT, array.toString()).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
    }

    private static final class Entry {
        final String id;
        final String label;

        Entry(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }
}
