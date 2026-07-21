# Trampoline PendingIntent bridge

## Status

Implemented as the mandatory launch path whenever **Bubble trampoline** is enabled. Each eligible
conversation owns a Bridge Bubble. Pixel testing confirmed that direct Back collapses the Bubble,
multiple conversations remain in the Bubble stack, and a notification update reopens the matching
conversation's latest target.

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
while stabilizing WeChat bubble launch. A later fixed-host experiment validated the persistent
Bridge root, but its single latest-conversation identity could not support concurrent chats. After
the per-conversation path passed device validation, the experiment switch and WeChat Home host
fallback were removed.

## Implemented route

1. Each eligible rewritten conversation notification receives the original WeChat message
   `contentIntent` separately from its normal click proxy.
2. When trampoline is enabled and the token is a WeChat Activity PendingIntent, that notification
   creates a conversation-identified mutable PendingIntent targeting non-exported
   `TrampolineBridgeActivity`.
3. SystemUI launches the bridge as the bubble root. The bridge validates the creator and type,
   records the embedded task, remains as its persistent root, opts into the current
   pending-intent background-launch mode, and forwards the untouched WeChat token. When the
   WeChat child returns to the bridge, it moves the task to the background so SystemUI can
   collapse the bubble rather than remove it. Re-expanding relaunches the current target.
4. A missing, canceled, or invalid target does not receive trampoline BubbleMetadata. The ordinary
   rewritten notification remains available instead of launching an unrelated WeChat Home task.
5. Stable per-conversation notification IDs, shortcut IDs, LocusIds, Bridge URIs and request codes
   keep different conversations in separate SystemUI document tasks. Removing one task does not
   cancel the others.

The original token is recoverable from the in-process `ConversationBubbleStore`. If process
recreation loses that state, active-notification synchronization leaves that notification without
a Bridge Bubble; the next real WeChat message supplies a new exact-conversation token and restores
the per-conversation Bubble.

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
- Two real conversation Bubbles remained in the stack, opened their matching chats, collapsed on
  direct Back, and reopened to the correct updated target after a later notification.
- WeChat batch-canceled multiple source messages on embedded launch. Preserving every active
  notification that still matched its Bubble ID, shortcut, metadata and `FLAG_BUBBLE` prevented
  opening the latest Bubble from removing the earlier one.

The initial exact-conversation stack made `ChattingMainUI` the only Activity. Reopening the
collapsed bubble worked, but pressing Back caused SystemUI to remove the task as `TASK_FINISHED`.
An attempted two-stage Home seed did not fix this: the device reused WeChat's independent
full-screen `LauncherUI` task, while `dumpsys activity` still showed only `ChattingMainUI` in the
Bubble task. Updating the fixed host also kept that old TaskView, so expanding after a different
conversation continued to show the previous chat. These results led to the persistent bridge
root and rotating host identity described above. After installing that revision on the same
device, follow-up interaction confirmed the expected Back-collapse behavior and latest-chat
replacement.

Still to cover: process recreation, rapid repeated switches, stale targets, and other Android or
WeChat versions. They may encode or reuse the immutable token differently and can still open a
full-screen task or collapse instead of remaining embedded.
