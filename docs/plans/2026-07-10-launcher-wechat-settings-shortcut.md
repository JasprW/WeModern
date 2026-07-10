# WeChat Launcher and Settings Shortcut Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make the WeModern launcher icon open WeChat by default while keeping WeModern settings accessible as the final app shortcut after up to three recent conversations.

**Architecture:** Replace `MainActivity` as the launcher entry with a translucent trampoline activity that publishes shortcuts and forwards to WeChat. Publish Settings as a dynamic shortcut, not a static shortcut, because Android always orders static shortcuts before dynamic shortcuts. Rebuild the full dynamic list as recent conversations followed by Settings, reserving one launcher slot for Settings.

**Tech Stack:** Android manifest activities, `ShortcutManager`/`ShortcutInfo`, Java, Kotlin, Android launcher verification with ADB.

---

### Task 1: Add a shared WeChat launcher helper

**Files:**
- Create: `app/src/main/java/me/jaspr/wemodern/WeChatLauncher.java`
- Modify: `app/src/main/java/me/jaspr/wemodern/ConversationShortcutActivity.java`

**Steps:**
1. Move the package lookup and `getLaunchIntentForPackage("com.tencent.mm")` behavior into a package-private helper.
2. Return a boolean so callers can provide their own fallback.
3. Keep conversation shortcuts opening their stored `PendingIntent` first, then fall back through the helper.
4. Run `./gradlew :app:compileDebugJavaWithJavac` and expect success.

### Task 2: Make the launcher icon open WeChat

**Files:**
- Create: `app/src/main/java/me/jaspr/wemodern/LauncherActivity.java`
- Modify: `app/src/main/AndroidManifest.xml`

**Steps:**
1. Add a no-history, no-recents, empty-task-affinity launcher trampoline.
2. Move the `MAIN`/`LAUNCHER` intent filter from `MainActivity` to `LauncherActivity`.
3. In the trampoline, ensure dynamic shortcuts are published, open WeChat, and fall back to `MainActivity` only when WeChat is unavailable.
4. Keep `MainActivity` as the internal settings screen.
5. Build and verify the manifest launcher component resolves to `LauncherActivity`.

### Task 3: Publish Settings as the final dynamic shortcut

**Files:**
- Modify: `app/src/main/java/me/jaspr/wemodern/ConversationShortcuts.java`
- Modify: `app/src/main/java/me/jaspr/wemodern/MainActivity.kt`
- Create: `app/src/main/res/drawable/ic_settings_shortcut_24.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`

**Steps:**
1. Reserve one dynamic shortcut slot for Settings.
2. Build at most `maxShortcutCountPerActivity - 1` recent conversation shortcuts.
3. Append the Settings shortcut with the largest rank and an explicit intent to `MainActivity`.
4. Associate every dynamic shortcut with `LauncherActivity`.
5. Rebuild the same ordered list when the notification listener connects, a conversation arrives, the launcher icon is tapped, or settings opens.
6. Add localized Settings labels and a dedicated icon.

### Task 4: Verify launcher and shortcut behavior

**Files:**
- Verify all files above.

**Steps:**
1. Run `./gradlew testDebugUnitTest assembleDebug lintDebug` and expect `BUILD SUCCESSFUL`.
2. Install on an API 36 launcher and tap the app icon; verify the foreground package becomes WeChat when installed.
3. Query `ShortcutManager` through ADB; verify Settings exists with the greatest dynamic rank.
4. Publish three conversation shortcuts; verify their ranks are `0`, `1`, `2` and Settings is rank `3`.
5. Launch the Settings shortcut; verify `MainActivity` opens.
6. Verify the existing conversation shortcut still opens its matching WeChat chat or falls back to WeChat.
