# WeModern

WeModern is a standalone Android app that rewrites WeChat message and call
notifications with the notification features available on modern Android.

## Features

- Rebuilds WeChat messages with Android's conversation-style notification UI,
  including sender avatars, message history, conversation grouping, and direct
  links back to each chat.
- Turns ongoing WeChat voice and video calls into Live Updates with elapsed call
  time on supported Android versions.
- Keeps rewritten notifications in sync when WeChat removes the originals, with
  optional synchronous removal for the cases Android does not expose normally.
- Publishes the three most recent conversations as launcher shortcuts. Tapping
  an optional app-icon action opens WeChat, while the final shortcut opens WeModern settings.
- Includes a guided Material You setup screen, themed launcher icon, Simplified
  and Traditional Chinese translations, and built-in test notifications.

## Android 17 Support

Unlike the original Nevolution-based setup, WeModern is a self-contained app
with no separate Nevolution platform or decorator plug-in required. It supports
Android 17 (API 37) and handles the modern notification access, sensitive
notification access, and background `PendingIntent` launch restrictions used by
recent Android releases. On Android 16 and later, WeChat calls can also appear as
promoted Live Updates using `Notification.ProgressStyle`.

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

To enable synchronous removal of rewritten WeChat notifications when WeChat
cancels its original notification, also grant log access and enable debug
notification service logs, then reboot:

```bash
adb shell pm grant me.jaspr.wemodern android.permission.READ_LOGS
adb shell setprop persist.log.tag.NotificationService DEBUG
adb reboot
```

## Launcher Behavior

Tapping the WeModern app icon opens WeModern settings by default. After notification access and notification permission are enabled, **Open WeChat from icon** can be turned on under **Advanced**. Touch and hold the icon to open one of the three most recent WeChat conversations or select **Settings**, which is always the final shortcut, to open the WeModern setup screen.
