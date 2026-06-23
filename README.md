# WeChat Notify

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

The release workflow attaches `wechat-notify-<tag>.apk`.

## Device Setup

After installing the APK, enable notification listener access for `WeChat Notify` and grant notification permission. For Android versions that require sensitive notification access during development, grant it with adb:

```bash
adb shell cmd appops set wang.bofan.wechatnotify RECEIVE_SENSITIVE_NOTIFICATIONS allow
```
