# WeModern

WeModern is a standalone Android app that rewrites WeChat message and call
notifications with the notification features available on modern Android.

WeModern is an independent, unofficial project. It is not affiliated with,
endorsed by, or sponsored by Tencent, and it is not a WeChat client. "WeChat"
and 微信 are trademarks of Tencent Holdings Ltd.; they appear here only to
describe the app whose notifications this utility rewrites. WeModern ships no
Tencent artwork — notification icons come from the notification being rewritten
at runtime, or from original artwork in this repository.

## Privacy

WeModern declares **no `INTERNET` permission**, so it has no network capability:
notification content is read and rewritten in memory and never leaves the
device. No analytics, no ads, no accounts, no telemetry. A release build stores
no notification content by default. Full statement, in English and Chinese:
[PRIVACY.md](PRIVACY.md).

## Features

- Rebuilds WeChat messages with Android's conversation-style notification UI,
  including sender avatars, message history, conversation grouping, and direct
  links back to each chat.
- Adds native Android chat bubbles on Android 10 and later. Each resizable
  bubble keeps recent messages visible. On Android 12 and later, Bubble
  trampoline can either bridge one Bubble per conversation to its latest
  WeChat notification action, or keep one shared Bubble that opens WeChat Home.
- Rebuilds incoming WeChat voice and video calls as native `CallStyle`
  notifications that keep ringing and vibrating until handled, then switches
  the same notification to a silent, promoted ongoing `CallStyle` with elapsed
  call time after connection.
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
recent Android releases. On Android 16 and later, an active WeChat call can
appear as a promoted ongoing `Notification.CallStyle`; its system Hang up
action opens the original WeChat call page because WeModern cannot directly
control the call.

Android 17 treats bubbles as a windowing mode. WeModern's normal conversation
bubble activity is embedded, resizable, supports multiple document instances,
and restores its message snapshot after process recreation. On Android 12 and
later, Bubble trampoline offers two modes. The default conversation mode gives
each eligible notification its own mutable WeModern bridge task. The bridge
validates and forwards that conversation's latest immutable WeChat Activity
`PendingIntent`; later messages in the same conversation update its Bubble,
while different conversations keep separate Bubble entries. Returning from the
chat collapses the Bubble instead of deleting it. The shared mode keeps one
stable Bubble and launches only WeChat Home; the user then selects a chat inside
WeChat. This avoids the direct-chat task shape that can let the IME cover the
input bar. Other Android or WeChat versions can still reuse a full-screen task
because WeModern cannot rewrite the immutable target's private flags.

All rewritten messages use the high-importance `wechat_messages_alerts`
channel. Pixel 9 Pro / API 37 validation confirmed that when Android accepts a
notification as a Bubble, SystemUI shows the Bubble flyout and suppresses the
ordinary pinned heads-up view even on this channel. The channel's configured
sound and vibration still apply. Legacy quiet Bubble channels are migrated and
deleted. Conversation notifications request full lock-screen visibility by
default; the user's Android notification and lock-screen settings remain
authoritative.
WeModern's own Chat bubbles switch controls whether
rewritten notifications include bubble metadata; Android separately controls
whether all or only selected conversations may bubble. When the WeModern switch
is off, notifications remain standard and do not offer a bubble action. On
Android 8 and 9, rewritten messages continue to work as normal notifications
without bubble metadata.

## Credits

WeModern is an independent implementation and contains no source code from the
projects below. The design was informed by prior art, above all
[Nevolution](https://github.com/Nevolution/sdk) and its
[WeChat Modernized decorator](https://github.com/Nevolution/decorator-wechat),
which introduced the idea of upgrading an existing app's notifications without
requiring changes from that app's developer. Many thanks to Oasis Feng and all
Nevolution contributors.

Third-party components, font licensing, and trademark attribution are listed in
[NOTICE](NOTICE).

## Build

```bash
./gradlew :app:assembleDebug
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## WeChat notification debugging

The app has two independent controls under **Debug**. **Capture and logging**
records every intercepted active-scan, posted, and removed WeChat notification
under the `WeModern.Capture` logcat tag and in an app-private JSONL file.
**Rewrite notifications** separately controls parsing, hiding, replacing,
bubbles, and Live Update promotion. Debug builds default to capture and logging
on with rewriting off, so WeChat notifications remain unchanged while evidence
is collected. Release builds default the other way around — rewriting on,
capture off — so an installed release rewrites notifications immediately and
stores no notification content unless the user turns capture on.

Changes take effect immediately without rebuilding or reconnecting notification
access. Monitor and export captured events with:

```bash
adb logcat -v threadtime -s WeModern.Capture
adb exec-out run-as me.jaspr.wemodern cat files/wechat_notification_capture.jsonl > wechat_notification_capture.jsonl
```

The current file rotates at 16 MiB to
`files/wechat_notification_capture.previous.jsonl`. Turning off **Capture and
logging** stops both new JSONL records and `WeModern.Capture` output. Turning on
**Rewrite notifications** enables the current message, bubble, and
evidence-driven voice/video call rewrite behavior while capture can remain
either on or off.

## Release

Releases are built and signed by CI. Pushing a tag named `v*` runs
`.github/workflows/release.yml`, which builds a release (non-debuggable) APK,
verifies its signature, writes `SHA256SUMS`, and publishes both files as a
GitHub Release:

```bash
git tag v1.9.0
git push origin v1.9.0
```

Signing needs four repository secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD`. Without them the workflow fails instead of
publishing an unsigned or debug-signed APK. Publish the checksum next to every
download so users can verify what they installed:

```bash
shasum -a 256 -c SHA256SUMS
```

To rehearse a release — build, sign and verify, upload the APK as a workflow
artifact, publish nothing — run the workflow manually with `dry_run`:

```bash
gh workflow run release.yml --repo JasprW/WeModern -f tag=vX.Y.Z -f dry_run=true
```

The signing key is the app's only update identity. Back it up offline; never
commit it. For a local release build, point the same variables at the key:

```bash
export WEMODERN_KEYSTORE_PATH=/path/to/release.jks
export WEMODERN_KEYSTORE_PASSWORD=...
export WEMODERN_KEY_ALIAS=...
export WEMODERN_KEY_PASSWORD=...
./gradlew :app:assembleRelease
```

## Device Setup

After installing the APK, enable notification listener access for `WeModern` and grant notification permission. For Android versions that require sensitive notification access during development, grant it with adb:

```bash
adb shell cmd appops set me.jaspr.wemodern RECEIVE_SENSITIVE_NOTIFICATIONS allow
```

To use chat bubbles on Android 10 or later, first open **Android bubble setting**
in the **Setup** section and allow all or selected conversations. The WeModern
feature switch in **Bubbles** can only be enabled after that system setting is
allowed. Private chats, group chats, and the combined Service Accounts conversation
all bubble by default. Their defaults can be changed independently, and any known
conversation can override its default with Always allow or Never allow.
Known conversations can be sorted by recency or total notification count.
Disabling bubble support for a conversation removes its bubble metadata without
disabling its normal notification; that conversation uses the alerting message
channel so new messages can still appear as heads-up notifications.

Private and group conversation overrides are keyed by the source name in WeChat's notification.
Changing a nickname or remark, renaming a group, or changing the WeChat language
can therefore make an existing override stop matching. `Service Accounts`, `服务号`,
and `服務號` instead share one stable `wechat:service_accounts` identity. Each service is
represented as a participant with its own cached avatar, while the latest service supplies
the combined notification, shortcut, Bubble avatar, and WeChat launch action. Opening it
marks and clears the combined Service Accounts conversation as one unit.

On Android 12 or later, enabling **Bubble trampoline** reveals a mode switch.
The default conversation mode makes each eligible conversation notification its
own Bridge Bubble and forwards the latest WeChat-created conversation action.
It opens the chat directly, but WeChat's input bar may be covered by the IME in
the embedded task. Bubble expansion uses an opaque Material You launch surface;
the Bridge draws the cached conversation avatar for one frame before forwarding
to WeChat, and only reveals an opening label if the handoff lasts over 300 ms.
There is no artificial minimum delay, and a missing cached avatar falls back to
the Bubble glyph. When WeChat returns from that chat to Home inside the same
Bubble task, WeModern uses the activity event log to clear Home and collapse the
Bubble on the first Back. This reliable-return path requires the `READ_LOGS` and
`NotificationService` DEBUG setup below. Turning the mode switch off keeps one shared Bubble for all
eligible conversations. That Bubble opens WeChat Home rather than a specific
chat, which requires one more tap but gives WeChat the task shape that better
adapts to the IME. The same private/group/Service Accounts defaults and conversation overrides
decide which notifications may create or update either kind of Bubble. A
disabled conversation keeps its normal heads-up notification but cannot create,
update, or take over a Bubble. The Message test always remains notification ID
`100`; in shared mode it also updates the stable Home Bubble. With Android set
to Selected conversations, conversation mode may require each real conversation
to be allowed separately, while shared mode uses one stable shortcut.

To enable synchronous removal of rewritten WeChat notifications and reliable
first-Back collapse for conversation Bridge Bubbles, grant log access and enable
debug notification service logs, then reboot:

```bash
adb shell pm grant me.jaspr.wemodern android.permission.READ_LOGS
adb shell setprop persist.log.tag.NotificationService DEBUG
adb reboot
```

## Launcher Behavior

Tapping the WeModern app icon opens WeModern settings by default. After notification access and notification permission are enabled, **Open WeChat from icon** can be turned on under **Advanced**. Touch and hold the icon to open one of the three most recent WeChat conversations or select **Settings**, which is always the fourth and final visible shortcut, to open the WeModern setup screen. The Android shortcut publishing limit can be higher than the number rendered by a launcher, so WeModern caps the visible list at four instead of allowing hidden contacts to push Settings out of Pixel Launcher's menu.

## License

Copyright (C) 2026 Bofan Wang.

WeModern is free software, licensed under the
[GNU General Public License, version 3 or later](LICENSE). You may use, study,
change, and redistribute it, including commercially. If you distribute a
modified version, it must remain under the same license and ship its source.

That is deliberate. An app that holds notification access can read every message
that reaches your phone, so anyone who installs it should be able to audit what
it does — and anyone who does not trust this build should be able to build and
sign their own.
