# Android 17 Conversation Bubbles Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add native, per-conversation WeChat notification bubbles that remain usable across window resizing and process recreation, and migrate WeModern to Android 17 / API 37.

**Architecture:** Keep WeModern's existing notification rewrite and conversation shortcut pipeline. A new clean-room bubble layer builds `Notification.BubbleMetadata` from each parsed WeChat conversation, opens a resizable WeModern conversation activity, carries a restorable message snapshot in the bubble intent, and launches the original WeChat `PendingIntent` only after a visible user action. Android's per-app/per-conversation bubble preference remains the source of truth, so notifications still work normally when bubbles are disabled.

**Tech Stack:** Android API 29-37 notification bubbles, `Notification.MessagingStyle`, dynamic conversation shortcuts, Java notification services, Kotlin/Jetpack Compose, JUnit 4, Gradle/AGP 8.13.2.

---

### Task 1: Add deterministic bubble policy tests

**Files:**
- Create: `app/src/test/java/me/jaspr/wemodern/ConversationBubblesTest.java`
- Create: `app/src/main/java/me/jaspr/wemodern/ConversationBubbles.java`

**Steps:**

1. Write tests proving bubbles are unavailable below API 29, available from API 29, and request codes are stable and conversation-specific.
2. Run `./gradlew :app:testDebugUnitTest --tests me.jaspr.wemodern.ConversationBubblesTest` and verify the tests fail before implementation.
3. Implement the pure policy methods and Android bubble builder entry points.
4. Re-run the focused test and verify it passes.

### Task 2: Add restorable conversation bubble state

**Files:**
- Create: `app/src/main/java/me/jaspr/wemodern/ConversationBubbleState.java`
- Create: `app/src/main/java/me/jaspr/wemodern/ConversationBubbleStore.java`
- Create: `app/src/test/java/me/jaspr/wemodern/ConversationBubbleStateTest.java`

**Steps:**

1. Test bounded history, duplicate suppression, and per-conversation isolation.
2. Implement immutable snapshots containing the conversation ID, title, recent sender/text/timestamp entries, and the original WeChat `PendingIntent`.
3. Add Intent serialization so System UI can reopen a bubble after WeModern process death.
4. Add an in-process observer store so an already-expanded bubble receives new messages.
5. Run the focused state tests.

### Task 3: Add the adaptive bubble activity

**Files:**
- Create: `app/src/main/java/me/jaspr/wemodern/BubbleConversationActivity.kt`
- Modify: `app/src/main/java/me/jaspr/wemodern/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/styles.xml`

**Steps:**

1. Register a non-exported, embedded, resizable, always-document activity.
2. Restore the initial snapshot from the incoming Intent and subscribe to live in-process updates.
3. Build a compact Compose message history that responds to arbitrary bubble window sizes.
4. Launch the original WeChat `PendingIntent` using `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE`; fall back to the existing shortcut/WeChat launch path if it is stale.
5. Leave root Back handling to Android so Back collapses the bubble.

### Task 4: Attach bubbles to rewritten notifications

**Files:**
- Modify: `app/src/main/java/me/jaspr/wemodern/WeChatNotificationService.java`
- Modify: `app/src/main/java/me/jaspr/wemodern/ConversationShortcuts.java`
- Modify: `app/src/main/java/me/jaspr/wemodern/MessageTestNotifications.java`
- Modify: `app/src/main/res/values/dimens.xml`

**Steps:**

1. Update per-conversation snapshots before posting each message notification.
2. Attach bubble metadata only to message children on API 29+, never call or group-summary notifications.
3. Use explicit, mutable, updateable, unique bubble pending intents and adaptive conversation
   icons. SystemUI must be able to add bubble launch options, and Android 17 rejects immutable
   bubble pending intents.
4. Keep more recent conversation shortcuts on Android 13+ while excluding non-launcher entries from the launcher surface.
5. Add bubble metadata to the built-in message test and retain its shortcut until cleanup.
6. Clear bubble state when its corresponding replacement notification is removed.

### Task 5: Add setup UI and translations

**Files:**
- Modify: `app/src/main/java/me/jaspr/wemodern/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`
- Modify: `app/src/main/java/me/jaspr/wemodern/NotificationChannels.java`

**Steps:**

1. Read Android's app-level bubble permission from `NotificationManager`.
2. Add one setup row showing whether chat bubbles are allowed and opening `ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS` when configuration is needed.
3. Mark the WeChat message channel as bubble-capable without overriding user notification choices.
4. Add English, Simplified Chinese, and Traditional Chinese strings.

### Task 6: Target and document Android 17

**Files:**
- Modify: `app/build.gradle`
- Modify: `README.md`
- Modify: `PRODUCT.md`

**Steps:**

1. Set `compileSdk` and `targetSdk` to 37.
2. Document per-conversation bubble behavior, Android system controls, and the full-screen WeChat handoff boundary.
3. Record that API 26-28 continue to receive normal rewritten notifications.

### Task 7: Verify the complete change

**Steps:**

1. Run `./gradlew :app:testDebugUnitTest` and fix all failures.
2. Run `./gradlew :app:assembleDebug` and fix compilation/resource problems.
3. Run `./gradlew :app:lintDebug` and fix new lint findings.
4. Inspect the final manifest and APK output.
5. Record the remaining Android 17 device checks: None/Selected/All bubble preferences, multiple conversations, resize/rotation, collapsed-process kill/reopen, direct WeChat launch, original-notification removal, and VoIP exclusion.
