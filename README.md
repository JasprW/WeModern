# WeModern

An Android notification listener that rewrites WeChat message and call notifications for newer Android versions.

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
git tag v0.11
git push origin v0.11
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

Tapping the WeModern app icon opens WeChat. Touch and hold the icon to open one of the three most recent WeChat conversations or select **Settings**, which is always the final shortcut, to open the WeModern setup screen.
