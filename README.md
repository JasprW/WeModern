# WeModern

WeModern is a standalone Android app that rewrites WeChat message and call
notifications with the notification features available on modern Android.

## Features

- Rebuilds WeChat messages with Android's conversation-style notification UI,
  including sender avatars, message history, conversation grouping, and direct
  links back to each chat.
- Adds one native Android chat bubble per WeChat conversation on Android 10 and
  later. Each resizable bubble keeps recent messages visible. An experimental
  option instead keeps one bubble for the latest conversation and opens WeChat
  Home directly as that bubble's content.
- Turns ongoing WeChat voice and video calls into Live Updates with elapsed call
  time on supported Android versions.
- Keeps rewritten notifications in sync when WeChat removes the originals, with
  optional synchronous removal for the cases Android does not expose normally.
- Publishes up to three recent conversations and reserves the fourth and final
  visible launcher shortcut for WeModern settings. Tapping an optional app-icon
  action opens WeChat.
- Includes a guided Material You setup screen, themed launcher icon, Simplified
  and Traditional Chinese translations, and built-in test notifications.

## Android 17 Support

WeModern compiles and targets Android 17 (API 37). Unlike the original
Nevolution-based setup, it is a self-contained app with no separate Nevolution
platform or decorator plug-in required. It handles modern notification access, sensitive
notification access, and background `PendingIntent` launch restrictions used by
recent Android releases. On Android 16 and later, WeChat calls can also appear as
promoted Live Updates using `Notification.ProgressStyle`.

Android 17 treats bubbles as a windowing mode. WeModern's conversation bubble
activity is embedded, resizable, supports multiple document instances, and
restores its message snapshot after process recreation. The normal conversation
action uses WeChat's original notification `PendingIntent`; the experimental
Bubble trampoline uses one dedicated, ungrouped host notification with a stable
shortcut and mutable launcher `PendingIntent`, so WeChat Home is the bubble task
root from the beginning. New messages update that same host to the latest
conversation instead of creating competing WeChat tasks. Ordinary rewritten
notifications remain independently removable, including when synchronous
removal is enabled. When bubbles are ready, ordinary message notifications use
a low-importance quiet channel, while the fixed Bubble host uses a separate
silent, minimized channel so SystemUI can update the bubble without adding a
second status-bar icon, text heads-up, sound, or vibration. When bubbles are off or unavailable,
messages return to the alerting channel. Conversation notifications request
full lock-screen visibility by default; the user's Android notification and
lock-screen settings remain authoritative.
WeModern's own Chat bubbles switch controls whether
rewritten notifications include bubble metadata; Android separately controls
whether all or only selected conversations may bubble. When the WeModern switch
is off, notifications remain standard and do not offer a bubble action. On
Android 8 and 9, rewritten messages continue to work as normal notifications
without bubble metadata.

## Credits

This project is inspired by and based on the approach pioneered by
[Nevolution](https://github.com/Nevolution/sdk), especially its
[WeChat Modernized decorator](https://github.com/Nevolution/decorator-wechat).
Nevolution introduced the idea of upgrading an existing app's notifications
without requiring changes from that app's developer. Many thanks to Oasis Feng
and all Nevolution contributors for the original concept and implementation.

## Build

```bash
./gradlew :app:assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Release

Push a tag named `v*` to build the debug APK and publish it as a GitHub Release:

```bash
git tag v1.0
git push origin v1.0
```

The release workflow attaches `wemodern-<tag>.apk`.

## Device Setup

After installing the APK, enable notification listener access for `WeModern` and grant notification permission. For Android versions that require sensitive notification access during development, grant it with adb:

```bash
adb shell cmd appops set me.jaspr.wemodern RECEIVE_SENSITIVE_NOTIFICATIONS allow
```

To use chat bubbles on Android 10 or later, first open **Android bubble setting**
in the **Setup** section and allow all or selected conversations. The WeModern
feature switch in **Bubbles** can only be enabled after that system setting is
allowed. Private and group chats both bubble
by default. Their defaults can be changed independently, and any known
conversation can override its default with Always allow or Never allow.
Known conversations can be sorted by recency or total notification count.
Disabling bubble support for a conversation removes its bubble metadata without
disabling its normal notification; that conversation uses the alerting message
channel so new messages can still appear as heads-up notifications.

Conversation overrides are keyed by the source name in WeChat's notification.
Changing a nickname or remark, renaming a group, or changing the WeChat language
can therefore make an existing override stop matching.

On Android 12 or later, enabling **Bubble trampoline** replaces the
per-conversation bubble set with one stable bubble representing the latest
eligible message. The same private/group defaults and conversation overrides
decide which conversations may create, update, or take over that shared bubble.
A disabled conversation keeps its heads-up notification but does not affect the
current trampoline bubble. Synchronous removal
continues to remove ordinary rewritten notifications without closing the
dedicated WeChat bubble host. Both the Message test and incoming messages keep
the bubble collapsed; a new message updates the bubble's visible preview and
unread state instead of opening WeChat automatically. When the Message test
successfully updates the trampoline host, it does not also retain notification
ID `100`; this keeps the test path consistent with a real replacement after its
source notification has been removed.

To enable synchronous removal of rewritten WeChat notifications when WeChat
cancels its original notification, also grant log access and enable debug
notification service logs, then reboot:

```bash
adb shell pm grant me.jaspr.wemodern android.permission.READ_LOGS
adb shell setprop persist.log.tag.NotificationService DEBUG
adb reboot
```

## Launcher Behavior

Tapping the WeModern app icon opens WeModern settings by default. After notification access and notification permission are enabled, **Open WeChat from icon** can be turned on under **Advanced**. Touch and hold the icon to open one of the three most recent WeChat conversations or select **Settings**, which is always the fourth and final visible shortcut, to open the WeModern setup screen. The Android shortcut publishing limit can be higher than the number rendered by a launcher, so WeModern caps the visible list at four instead of allowing hidden contacts to push Settings out of Pixel Launcher's menu.
