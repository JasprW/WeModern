# Trampoline PendingIntent bridge

## Status

Implemented behind the default-off **Open exact conversation (Experimental)** setting. A real
private-message launch placed WeChat's exact `ChattingMainUI` inside the SystemUI-owned bubble
task. Follow-up Pixel testing confirmed that direct Back collapses the Bubble and that a host
update for another conversation opens the latest chat.

## Evidence and constraint

Pixel 9 Pro / Android 17 (API 37) captures from WeChat 8.0.69 show that every sampled message
notification uses an Activity `contentIntent` created by `com.tencent.mm` with
`isImmutable() == true`. This token can open the exact conversation, but WeModern cannot read
its private conversation extras or recreate it as a mutable PendingIntent.

`Notification.BubbleMetadata` requires a mutable Activity PendingIntent so SystemUI can provide
the embedded-task launch options. Using the immutable WeChat token directly as bubble metadata
is therefore not valid. Sending it from a mutable WeModern activity preserves the conversation
capability, but Android may ignore attempts to clear task-separating flags on the immutable
target. The second launch may remain in the bubble, switch to an existing full-screen WeChat
task, or collapse the bubble.

The repository previously tried an equivalent handoff in `9e6ce68` and removed it in `97f74bd`
while stabilizing WeChat bubble launch. The new implementation intentionally exposes the behavior
only as an explicit experiment and retains the proven WeChat Home path as fallback.

## Implemented route

1. The trampoline host receives the original WeChat message `contentIntent` separately
   from the rewritten notification's normal click proxy.
2. When the experiment is enabled and the token is a WeChat Activity PendingIntent, the host
   creates a conversation-identified mutable PendingIntent targeting non-exported
   `TrampolineBridgeActivity`.
3. SystemUI launches the bridge as the bubble root. The bridge validates the creator and type,
   records the embedded task, remains as its persistent root, opts into the current
   pending-intent background-launch mode, and forwards the untouched WeChat token. When the
   WeChat child returns to the bridge, it moves the task to the background so SystemUI can
   collapse the bubble rather than remove it. Re-expanding relaunches the current target.
4. A missing, canceled, or invalid target falls back to the existing mutable WeChat Home launch.
   A non-bubble host-notification click forwards normally after clearing the bubble.
5. A different latest conversation alternates between two owned host notification IDs and a
   different bridge PendingIntent identity. The successor is posted before the predecessor is
   canceled, forcing SystemUI to discard the old conversation TaskView. Old-task removal is
   detached from host cleanup so it cannot cancel the successor.

The original token is recoverable from the in-process `ConversationBubbleStore`. If the process
is recreated without that state, active-notification synchronization falls back to WeChat Home;
the next real WeChat message supplies a new exact-conversation token and updates the host.

## Device validation

Completed on Pixel 9 Pro / Android 17 (API 37), WeChat 8.0.69:

- Two fresh private-message host updates reported `launchMode=conversation-bridge`.
- `ActivityTaskManager` started `com.tencent.mm/.ui.chatting.variants.ChattingMainUI` from the
  bridge source record and inherited the bridge's multi-window task. `dumpsys activity` then
  showed `ChattingMainUI` as the root and only Activity in that task, with the bubble bounds.
- The bridge remains as the persistent task root after submitting `startIntentSender()`, so the
  WeChat child can return to it instead of finishing the Bubble task.
- A later SystemUI collapse did not remove the task: `ChattingMainUI` remained stopped in the
  same multi-window task and reopened with the bubble. One separate removal was explicitly
  logged by SystemUI as `USER_GESTURE` after the stack was dragged to dismiss.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` passes.

The initial exact-conversation stack made `ChattingMainUI` the only Activity. Reopening the
collapsed bubble worked, but pressing Back caused SystemUI to remove the task as `TASK_FINISHED`.
An attempted two-stage Home seed did not fix this: the device reused WeChat's independent
full-screen `LauncherUI` task, while `dumpsys activity` still showed only `ChattingMainUI` in the
Bubble task. Updating the fixed host also kept that old TaskView, so expanding after a different
conversation continued to show the previous chat. These results led to the persistent bridge
root and rotating host identity described above. After installing that revision on the same
device, follow-up interaction confirmed the expected Back-collapse behavior and latest-chat
replacement.

Still to cover:

- Group chat, process recreation, rapid repeated switches, and a stale target remain to cover.
- Disabling the experiment should restore `launchMode=wechat-home` without removing the main
  trampoline preference.
- Other WeChat and Android versions may encode the immutable token differently and can still
  fall back, open full-screen, or collapse instead of remaining embedded.
