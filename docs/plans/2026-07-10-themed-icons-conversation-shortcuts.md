# Themed Icons and Conversation Shortcuts Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a Material You themed launcher icon and expose the three most recently notified WeChat conversations as launcher shortcuts that open the matching chat.

**Architecture:** Add an Android 13 resource override with a monochrome adaptive-icon layer. Publish a persisted three-entry MRU list through `ShortcutManager` whenever a WeChat message is rewritten; each shortcut targets a no-UI trampoline activity that forwards the original notification `PendingIntent` to WeChat. Associate rewritten notifications with the same long-lived conversation shortcut and recover pending intents from active rewritten notifications after service recreation.

**Tech Stack:** Android resources, `ShortcutManager`/`ShortcutInfo`, `NotificationListenerService`, Java, Kotlin, Gradle.

---

### Task 1: Add the Material You icon layer

**Files:**
- Modify: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`

**Steps:**
1. Add `<monochrome android:drawable="@mipmap/ic_launcher_foreground" />` to the adaptive icon, as supported by the compiled resource format and ignored by pre-Android 13 launchers.
2. Run `./gradlew :app:processDebugResources` and expect `BUILD SUCCESSFUL`.

### Task 2: Publish the latest conversation shortcuts

**Files:**
- Create: `app/src/main/java/me/jaspr/wemodern/ConversationShortcuts.java`
- Modify: `app/src/main/java/me/jaspr/wemodern/WeChatNotificationService.java`

**Steps:**
1. Persist an MRU array of shortcut ID and conversation title in `SharedPreferences`.
2. Keep at most three unique conversations and publish them in newest-first rank order.
3. Mark API 30+ entries as long-lived conversation shortcuts with `Person` and `LocusId` metadata.
4. Register each notification's content `PendingIntent` before posting its replacement.
5. Set the replacement notification's shortcut ID and locus ID to the published conversation.
6. On listener connection, recover shortcut forwarding targets from active rewritten notifications.

### Task 3: Route shortcut clicks into WeChat

**Files:**
- Create: `app/src/main/java/me/jaspr/wemodern/ConversationShortcutActivity.java`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/styles.xml`

**Steps:**
1. Add a non-exported, no-history, empty-task-affinity translucent trampoline activity.
2. Read the conversation ID and send the registered original notification `PendingIntent`.
3. If that pending intent is no longer valid, fall back to WeChat's launcher activity.

### Task 4: Verify the integration

**Files:**
- Verify: all files above

**Steps:**
1. Run `./gradlew testDebugUnitTest assembleDebug lintDebug`.
2. Expect the unit-test task to complete (the project currently has no unit sources), the debug APK to assemble, and lint to report no new fatal errors.
3. On Android 13+, enable themed icons and verify the launcher icon follows the wallpaper palette.
4. Receive messages from four different conversations, long-press the launcher icon, and verify only the newest three appear in recency order and open the matching WeChat chats.
