package me.jaspr.wemodern;

import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.app.Person;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.util.Log;

import androidx.core.content.FileProvider;

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
    private static final String ICON_FORMAT_MARKER = ".adaptive_safe_zone_v2";
    private static final String SETTINGS_SHORTCUT_ID = "wemodern_settings";
    private static final int MAX_LAUNCHER_RECENT = 3;
    private static final int MAX_TRACKED_RECENT = 8;
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
        migrateLegacyShortcutIcons(context);

        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        if (manager == null || manager.getMaxShortcutCountPerActivity() == 0) return;

        String label = title == null ? "" : title.toString().trim();
        if (label.isEmpty()) label = context.getString(R.string.app_name);
        Icon currentShortcutIcon = persistIcon(context, conversationId, senderIcon);

        List<Entry> recent = load(context);
        recent.removeIf(entry -> conversationId.equals(entry.id));
        recent.add(0, new Entry(conversationId, label));
        while (recent.size() > MAX_TRACKED_RECENT) recent.remove(recent.size() - 1);
        save(context, recent);
        deleteStaleIcons(context, recent);

        if (replaceDynamicShortcuts(
                context, manager, recent, conversationId, currentShortcutIcon)) {
            manager.reportShortcutUsed(conversationId);
        }
    }

    static void ensureSettingsShortcut(Context context) {
        migrateLegacyShortcutIcons(context);
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        if (manager == null || manager.getMaxShortcutCountPerActivity() == 0) return;
        try {
            for (ShortcutInfo shortcut : manager.getDynamicShortcuts()) {
                if (SETTINGS_SHORTCUT_ID.equals(shortcut.getId())) return;
            }
        } catch (IllegalStateException e) {
            Log.w(TAG, "failed to inspect dynamic shortcuts", e);
            return;
        }
        replaceDynamicShortcuts(context, manager, load(context), null, null);
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
            Bitmap bitmap = renderAdaptiveIconDrawable(drawable);
            return bitmap == null ? source : Icon.createWithAdaptiveBitmap(bitmap);
        } catch (RuntimeException e) {
            Log.w(TAG, "failed to fit conversation avatar", e);
            return source;
        }
    }

    static void refreshIcons(Context context) {
        migrateLegacyShortcutIcons(context);
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        if (manager == null || manager.getMaxShortcutCountPerActivity() == 0) return;
        replaceDynamicShortcuts(context, manager, load(context), null, null);
    }

    static boolean openConversation(Context context, String conversationId) {
        PendingIntent contentIntent = CONTENT_INTENTS.get(conversationId);
        if (contentIntent == null) return false;
        return sendConversationIntent(context, conversationId, contentIntent);
    }

    static boolean openConversation(
            Context context,
            String conversationId,
            PendingIntent contentIntent
    ) {
        if (contentIntent != null) registerContentIntent(conversationId, contentIntent);
        return openConversation(context, conversationId);
    }

    private static boolean sendConversationIntent(
            Context context,
            String conversationId,
            PendingIntent contentIntent
    ) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setPendingIntentBackgroundActivityStartMode(
                        Build.VERSION.SDK_INT >= 36
                                ? ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                                : ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                contentIntent.send(options.toBundle());
            } else {
                contentIntent.send();
            }
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
                                              Icon shortcutIcon, boolean excludedFromLauncher) {
        Intent intent = new Intent(context, ConversationShortcutActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(EXTRA_CONVERSATION_ID, entry.id);
        if (shortcutIcon == null) {
            shortcutIcon = Icon.createWithResource(context, R.mipmap.ic_launcher);
        }
        ShortcutInfo.Builder builder = new ShortcutInfo.Builder(context, entry.id)
                .setActivity(new ComponentName(context, LauncherActivity.class))
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
        if (Build.VERSION.SDK_INT >= 33 && excludedFromLauncher) {
            builder.setExcludedFromSurfaces(ShortcutInfo.SURFACE_LAUNCHER);
        }
        return builder.build();
    }

    private static ShortcutInfo buildSettingsShortcut(Context context, int rank) {
        Intent intent = new Intent(context, MainActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return new ShortcutInfo.Builder(context, SETTINGS_SHORTCUT_ID)
                .setActivity(new ComponentName(context, LauncherActivity.class))
                .setShortLabel(context.getString(R.string.shortcut_settings_short_label))
                .setLongLabel(context.getString(R.string.shortcut_settings_long_label))
                .setRank(rank)
                .setIcon(Icon.createWithResource(context, R.drawable.ic_settings_shortcut_24))
                .setIntent(intent)
                .build();
    }

    private static boolean replaceDynamicShortcuts(
            Context context,
            ShortcutManager manager,
            List<Entry> recent,
            String currentConversationId,
            Icon currentShortcutIcon) {
        int maxShortcutCount = manager.getMaxShortcutCountPerActivity();
        if (maxShortcutCount == 0) return false;
        int retainedCount = Build.VERSION.SDK_INT >= 33
                ? MAX_TRACKED_RECENT
                : MAX_LAUNCHER_RECENT;
        int conversationCount = Math.min(
                recent.size(), Math.min(retainedCount, Math.max(0, maxShortcutCount - 1)));
        List<ShortcutInfo> shortcuts = new ArrayList<>(conversationCount + 1);
        for (int index = 0; index < conversationCount; index++) {
            Entry entry = recent.get(index);
            Icon shortcutIcon = loadIcon(context, entry.id);
            if (shortcutIcon == null && entry.id.equals(currentConversationId)) {
                shortcutIcon = currentShortcutIcon;
            }
            shortcuts.add(buildShortcut(
                    context,
                    entry,
                    index,
                    shortcutIcon,
                    index >= MAX_LAUNCHER_RECENT
            ));
        }
        shortcuts.add(buildSettingsShortcut(context, conversationCount));

        try {
            if (!manager.setDynamicShortcuts(shortcuts)) {
                Log.w(TAG, "launcher rejected dynamic shortcuts");
                return false;
            }
            return true;
        } catch (IllegalArgumentException | IllegalStateException e) {
            Log.w(TAG, "failed to publish dynamic shortcuts", e);
            return false;
        }
    }

    private static Icon persistIcon(Context context, String conversationId, Icon source) {
        if (source == null) return null;
        try {
            Drawable drawable = source.loadDrawable(context);
            if (drawable == null) return null;
            Bitmap bitmap = renderAdaptiveIconDrawable(drawable);
            if (bitmap == null) return null;

            File directory = iconDirectory(context);
            if (!directory.exists() && !directory.mkdirs()) {
                Log.w(TAG, "failed to create conversation shortcut icon directory");
                return null;
            }
            File iconFile = iconFile(context, conversationId);
            writeBitmap(iconFile, bitmap);
            return iconForFile(context, iconFile);
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "failed to persist conversation shortcut icon", e);
            return null;
        }
    }

    private static Icon loadIcon(Context context, String conversationId) {
        File iconFile = iconFile(context, conversationId);
        return iconFile.isFile() ? iconForFile(context, iconFile) : null;
    }

    private static Icon iconForFile(Context context, File iconFile) {
        if (Build.VERSION.SDK_INT < 30) {
            Bitmap bitmap = BitmapFactory.decodeFile(iconFile.getPath());
            return bitmap == null ? null : Icon.createWithAdaptiveBitmap(bitmap);
        }
        Uri iconUri = FileProvider.getUriForFile(
                context, context.getPackageName() + ".shortcuticons", iconFile);
        return Icon.createWithAdaptiveBitmapContentUri(iconUri);
    }

    static int[] adaptiveIconBounds(int size) {
        int inset = Math.round(size * ADAPTIVE_ICON_INSET_PX / (float) SHORTCUT_ICON_SIZE_PX);
        return new int[] {inset, inset, size - inset, size - inset};
    }

    private static Bitmap renderAdaptiveIconDrawable(Drawable drawable) {
        if (drawable == null) return null;
        Bitmap bitmap = Bitmap.createBitmap(
                SHORTCUT_ICON_SIZE_PX, SHORTCUT_ICON_SIZE_PX, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        int[] bounds = adaptiveIconBounds(SHORTCUT_ICON_SIZE_PX);
        drawable.setBounds(bounds[0], bounds[1], bounds[2], bounds[3]);
        drawable.draw(canvas);
        return bitmap;
    }

    private static void migrateLegacyShortcutIcons(Context context) {
        File directory = iconDirectory(context);
        File marker = new File(directory, ICON_FORMAT_MARKER);
        if (marker.isFile()) return;
        if (!directory.exists() && !directory.mkdirs()) {
            Log.w(TAG, "failed to create conversation shortcut icon directory for migration");
            return;
        }
        File[] files = directory.listFiles(file -> file.getName().endsWith(".png"));
        if (files == null) return;
        for (File file : files) {
            Bitmap source = BitmapFactory.decodeFile(file.getPath());
            if (source == null) {
                Log.w(TAG, "failed to decode conversation shortcut icon: " + file.getName());
                return;
            }
            Bitmap fitted = renderAdaptiveIconDrawable(
                    new BitmapDrawable(context.getResources(), source));
            if (fitted == null) {
                Log.w(TAG, "failed to migrate conversation shortcut icon: " + file.getName());
                return;
            }
            try {
                writeBitmap(file, fitted);
            } catch (IOException e) {
                Log.w(TAG, "failed to migrate conversation shortcut icon: " + file.getName(), e);
                return;
            }
        }
        try {
            if (!marker.createNewFile()) {
                Log.w(TAG, "failed to mark conversation shortcut icon migration");
            }
        } catch (IOException e) {
            Log.w(TAG, "failed to mark conversation shortcut icon migration", e);
        }
    }

    private static void writeBitmap(File file, Bitmap bitmap) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
    }

    private static void deleteStaleIcons(Context context, List<Entry> recent) {
        File[] files = iconDirectory(context).listFiles();
        if (files == null) return;
        for (File file : files) {
            if (ICON_FORMAT_MARKER.equals(file.getName())) continue;
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
            for (int index = 0; index < array.length() && result.size() < MAX_TRACKED_RECENT; index++) {
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
