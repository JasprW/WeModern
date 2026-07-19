# Repository Guidelines

## Project Structure & Module Organization

This is a single-module Android application. Production code is in
`app/src/main/java/me/jaspr/wemodern/`; the setup UI is primarily Kotlin and
Jetpack Compose (`MainActivity.kt`), while notification integration is Java.
Resources and translations live under `app/src/main/res/`, including
`values-zh-rCN/` and `values-zh-rTW/`. Local JVM tests are in
`app/src/test/java/me/jaspr/wemodern/`. Keep product requirements in
`PRODUCT.md`, and place implementation plans or design artifacts under `docs/`.

## Documentation Maintenance

`docs/README.md` is the entry point for repository documentation. Before implementing a feature or fix, check the relevant current-state, change-log, and exploration documents. Every completed feature implementation or bug fix must update documentation in the same change:

- Update `docs/current-implementation.md` when behavior, architecture, configuration, limitations, or verification requirements change.
- Append a user-visible entry to `docs/changelog.md`, including the version when one is published; record the problem and its resolution for bug fixes.
- Record unimplemented ideas, investigations, dependencies, and conclusions in `docs/explorations/` (or update its index) rather than presenting them as shipped functionality.

Keep documentation factual and aligned with code and device validation. Link to a plan or design document when it explains non-obvious implementation decisions, and do not leave a behavior change undocumented.

## Build, Test, and Development Commands

Use the checked-in Gradle wrapper:

```bash
./gradlew :app:assembleDebug       # Build the debug APK
./gradlew :app:testDebugUnitTest   # Run local JUnit tests
./gradlew :app:lintDebug           # Run Android lint for the debug variant
```

The debug artifact is `app/build/outputs/apk/debug/app-debug.apk`. The project
targets Java/Kotlin JVM 17; configure a matching JDK before building. For
device-only notification behavior, follow the access and adb setup commands in
`README.md`.

## Coding Style & Naming Conventions

Follow the existing Android style: four-space indentation, braces on the same
line, and one top-level class per file. Use `PascalCase` for classes and
activities, `camelCase` for methods and fields, and `UPPER_SNAKE_CASE` for
constants. Keep package names under `me.jaspr.wemodern`. Name Java unit tests
`*Test.java`, with descriptive behavior-oriented methods such as
`bubbledLaunchRemovesNewTaskFlag`. Use Android resource naming conventions,
for example `ic_settings_shortcut_24.xml` and lower_snake_case string keys.

## Testing Guidelines

Add or update JUnit 4 tests for deterministic notification parsing, launch
policy, and other non-UI logic. Tests should cover both positive and negative
cases, especially Chinese and English notification content. Run
`./gradlew :app:testDebugUnitTest` before submitting. Device validation is
required when changing notification listeners, permissions, shortcuts, or Live
Updates; state the Android version and behavior checked in the PR.

## Test Notification Icon Invariant

The Message test notification must always use
`R.drawable.ic_wechat_notification_small` as its small icon. This is the real
WeChat notification glyph extracted from WeChat 8.0.69 `drawable/bdo`
(`0x7f080bc8`, APK entry `res/j/yt.png`) and stored at
`app/src/main/res/drawable-xxhdpi/ic_wechat_notification_small.png`. Its SHA-256
is `59593725a489d8471618ba7b0d8891f6e9a6eff2c117de7f87256dad3a0a5b99`.

Do not replace this icon with the launcher icon, a conversation avatar, the
colored `ic_wechat_notification_fallback` artwork, or an icon cached from a
recent WeChat notification. Android renders notification small icons from
their alpha mask, so opaque colored artwork appears as a solid square. Do not
change this invariant unless the user explicitly requests a new icon extracted
from the small-icon resource of an installed WeChat package.

After touching the Message test notification or related icon resources, post
notification ID `100` on a device, confirm with `dumpsys notification` that it
uses `ic_wechat_notification_small`, and visually verify that the status bar
shows the WeChat double-bubble glyph rather than a square.

## Commit & Pull Request Guidelines

Use concise Conventional Commit-style subjects, as in `feat: add themed
conversation shortcuts` and `fix: improve notification shortcut handling`.
Keep each commit focused. Pull requests should explain user-visible behavior,
link relevant issues or plans, list verification commands and device checks,
and include screenshots for setup-screen or launcher UI changes. Do not commit
`local.properties`, generated build outputs, or device-specific credentials.
