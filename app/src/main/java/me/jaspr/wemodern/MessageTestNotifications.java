package me.jaspr.wemodern;

import android.app.Person;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Build;

import java.util.Collections;

final class MessageTestNotifications {
    static final int CURRENT_ID = 100;
    static final String SHORTCUT_ID = "wemodern_test_message";

    private MessageTestNotifications() {
    }

    static boolean isTestId(int id) {
        return id == CURRENT_ID;
    }

    static boolean isTestShortcutId(String shortcutId) {
        return SHORTCUT_ID.equals(shortcutId);
    }

    static void publishConversationShortcut(Context context, CharSequence senderName, Icon avatar) {
        if (Build.VERSION.SDK_INT < 30) return;
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        if (manager == null) return;

        Intent intent = new Intent(context, MainActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        ShortcutInfo.Builder builder = new ShortcutInfo.Builder(context, SHORTCUT_ID)
                .setActivity(new ComponentName(context, LauncherActivity.class))
                .setShortLabel(senderName)
                .setLongLabel(senderName)
                .setIcon(avatar)
                .setIntent(intent)
                .setLocusId(new android.content.LocusId(SHORTCUT_ID))
                .setPerson(new Person.Builder()
                        .setName(senderName)
                        .setKey(SHORTCUT_ID)
                        .setIcon(avatar)
                        .build())
                .setLongLived(true)
                .setCategories(Collections.singleton(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION));
        // A bubble's conversation shortcut must remain available to SystemUI. Android 17
        // does not treat a test shortcut excluded from every launcher surface as valid.
        manager.pushDynamicShortcut(builder.build());
    }

    static void removeDynamicConversationShortcut(Context context) {
        if (Build.VERSION.SDK_INT < 30) return;
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        if (manager != null) manager.removeDynamicShortcuts(Collections.singletonList(SHORTCUT_ID));
    }

    static void removeConversationShortcut(Context context) {
        if (Build.VERSION.SDK_INT < 30) return;
        ShortcutManager manager = context.getSystemService(ShortcutManager.class);
        if (manager != null) manager.removeLongLivedShortcuts(Collections.singletonList(SHORTCUT_ID));
    }
}
