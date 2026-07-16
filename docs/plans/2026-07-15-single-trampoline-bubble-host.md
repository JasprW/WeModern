# Single Trampoline Bubble Host Implementation Plan

**Goal:** Make Bubble trampoline expose exactly one stable WeChat bubble that always
represents the latest rewritten conversation, while keeping normal mode's per-conversation
bubbles and restoring synchronous removal for ordinary replacement notifications.

**Problem:** Bubble trampoline currently attaches a distinct mutable WeChat launcher
`PendingIntent` to every conversation notification. Android creates one embedded task per
bubble, but WeChat's `LauncherUI` does not support those tasks concurrently. Launching a
second bubble finishes the first task. The first bubble's delete callback then cancels its
host notification; the message-group cleanup cancels the group summary and Android removes
the newly opened bubble with `GROUP_CANCELLED`.

**Architecture:** Introduce a dedicated, ungrouped trampoline-host notification with a fixed
notification ID, fixed long-lived conversation shortcut ID, and fixed mutable WeChat launcher
`PendingIntent`. Every new rewritten message updates this one notification's title, avatar,
style, timestamp, and normal content action. Per-conversation replacement notifications stay
separate and carry no `BubbleMetadata` while trampoline mode is enabled. In normal bubble
mode they retain the current per-conversation `BubbleMetadata` behavior.

## Invariants

1. At most one WeModern notification may carry trampoline `BubbleMetadata`.
2. The trampoline host is never a child or summary of `wechat.rewritten`.
3. The trampoline host is never recorded as a synchronous-removal replacement.
4. Removing an original WeChat notification may remove its replacement, but must not remove
   the trampoline host.
5. The trampoline host has no bubble delete callback. A WeChat task finishing must not be
   interpreted as a request to cancel the host notification.
6. Normal mode continues to publish one bubble per conversation with its existing delete
   behavior.
7. When the Message test posts an ordinary source notification, it keeps notification ID `100`
   and always uses `R.drawable.ic_wechat_notification_small` as its small icon. A successfully
   posted trampoline host replaces that source entry instead of retaining both notifications.
8. The fixed host shortcut must not displace the three recent launcher conversations or the
   Settings shortcut.
9. The fixed host shortcut and `BubbleMetadata` use an unmasked adaptive avatar. Circular
   pre-masking is reserved for notification content; otherwise Pixel SystemUI shrinks the avatar
   onto a white legacy-icon background.

## Runtime behavior

### Normal bubble mode

- Each rewritten conversation notification gets its own WeModern bubble activity.
- The dedicated trampoline host notification and shortcut are removed.
- Synchronous removal cancels matching replacement notifications as before trampoline was
  introduced.

### Bubble trampoline mode

- Each rewritten conversation notification remains a standard conversation notification and
  has `BubbleMetadata` cleared.
- The newest rewritten message updates the fixed trampoline host.
- Updating the fixed host reuses the existing SystemUI bubble task; it does not launch or
  destroy WeChat until the user expands the bubble.
- If synchronous removal removes the newest conversation notification, the host retains the
  last observed title/avatar instead of closing an active WeChat bubble.
- A later message updates the same host to the new latest conversation.
- A Message test that successfully updates the host omits its redundant ID `100` source
  notification, since no WeChat app-cancel event exists to remove that test-only entry later.
- Disabling trampoline mode cancels the host, removes its long-lived shortcut, and restores
  per-conversation bubble metadata to restorable active notifications.
- Disabling chat bubbles cancels the host and clears all active bubble metadata.

## Shortcut design

- Add a fixed shortcut ID such as `wemodern_wechat_bubble_host`.
- Publish it as a long-lived conversation shortcut before posting the host notification.
- Update its label, person, and icon from the latest conversation while keeping its ID and
  locus ID fixed.
- Keep it visible to `ShortcutManager` while dynamic; excluding it from
  `ShortcutInfo.SURFACE_LAUNCHER` makes Samsung NotificationManager reject it as an invalid
  conversation shortcut.
- Temporarily reserve a dynamic shortcut slot, push the host shortcut, post the notification,
  convert it to a cached long-lived shortcut, and restore the normal recent-conversation plus
  Settings dynamic list.

## Notification design

- Add a fixed notification ID in a namespace that does not overlap the message test, call
  tests, group summary, or conversation hash IDs.
- Recover the latest replacement notification builder so the host mirrors its messaging
  style, title, text, timestamp, small icon, large icon, and normal content action.
- Use a dedicated `IMPORTANCE_LOW` channel for ordinary messages while bubbles are ready. Keep
  the existing alerting message channel when bubbles are disabled or unavailable. Put the fixed
  host on a separate silent `IMPORTANCE_HIGH` channel because SystemUI requires high importance
  to show a Bubble update flyout.
- Clear group membership and summary state.
- Clear notification-level defaults, sound, and vibration so host updates remain quiet. Do not
  set `ONLY_ALERT_ONCE`, because each fixed-host update must remain visually interruptive for
  SystemUI to show the latest Bubble message preview.
- Set the fixed shortcut ID and locus ID.
- Attach bubble metadata with the fixed mutable WeChat launcher `PendingIntent`, desired
  height, no notification suppression, and no delete intent. Incoming messages and the Message
  test stay collapsed so SystemUI can show the updated message preview without opening WeChat.
- Migrate the fixed notification ID once from the auto-expanding implementation so SystemUI does
  not restore its stale expanded state; retain the existing shortcut ID and Bubble permission.
- If the WeChat launcher activity cannot be resolved, skip the host update and leave the
  ordinary notification usable.

## Synchronous removal changes

- Remove the trampoline-wide preservation branches from both normal removal callbacks and the
  log-based cancellation path.
- Ordinary replacement notifications are again canceled when their original notification is
  removed.
- Exempt the fixed host notification ID from orphan-replacement cleanup.
- Remove the setup UI's "paused while Bubble trampoline is on" state and explanatory copy.
- Do not derive host lifetime from the set of active replacements; otherwise opening WeChat
  could remove all originals and immediately tear down its own bubble task.

## Active notification synchronization

`ConversationBubbles.syncActiveNotifications()` becomes the mode reconciler:

1. If chat bubbles are disabled, remove the fixed host and clear bubble metadata from all
   active conversation/test notifications.
2. If trampoline is enabled, clear per-conversation bubble metadata. If an eligible active
   notification exists, choose the newest by post time and update the fixed host from it. If
   none exists, preserve an already active host but do not create a new one.
3. If normal bubble mode is enabled, remove the fixed host and rebuild per-conversation bubble
   metadata for notifications with restorable state.
4. Always exclude the group summary and the fixed host itself from source selection.

## Implementation tasks

### Task 1: Add deterministic host policy tests

- Add tests for the fixed ID/shortcut identity, host eligibility, source selection, and orphan
  cleanup exemption.
- Change trampoline preservation tests to prove replacement notifications are no longer
  preserved.
- Add tests proving normal mode applies conversation metadata while trampoline mode does not.

### Task 2: Implement the dedicated host

- Add `TrampolineBubbleHost.java` to own fixed identity, shortcut publication, notification
  cloning, bubble metadata, update, and cleanup.
- Reuse the existing adaptive/circular conversation icon handling where possible.
- Convert the temporarily dynamic host shortcut to a cached long-lived shortcut after posting,
  then restore normal dynamic shortcuts after every update.

### Task 3: Reconcile bubble modes

- Update `ConversationBubbles.applyTo()` so trampoline mode never adds per-conversation bubble
  metadata.
- Update `syncActiveNotifications()` to implement the three-mode reconciliation above.
- Update real notification posting to refresh the fixed host after its ordinary notification
  has been posted. For Message test, publish ID `100` only when the host update is unavailable;
  otherwise the fixed host is the sole test notification.

### Task 4: Restore synchronous removal

- Remove the trampoline preservation branches from `WeChatNotificationService`.
- Exempt the host from orphan cleanup.
- Fix group-summary cleanup so a summary is canceled only when no grouped children remain;
  never cancel it while one child is still grouped.
- Remove the paused sync-removal UI state and update all translations.

### Task 5: Document behavior

- Update README and PRODUCT descriptions from per-conversation trampoline tasks to one latest
  conversation host.
- Document that normal mode remains per-conversation and that synchronous removal continues to
  operate in trampoline mode.

### Task 6: Verify

- Run focused unit tests for bubble, trampoline, shortcut, and removal policy.
- Run `./gradlew :app:testDebugUnitTest`.
- Run `./gradlew :app:assembleDebug`.
- Run `./gradlew :app:lintDebug`.
- Install the current debug APK on the connected Pixel 9 Pro without clearing app data.
- With trampoline unavailable, confirm notification ID `100` still uses
  `ic_wechat_notification_small`.
- With trampoline enabled, confirm dumpsys shows exactly one WeModern notification with
  `BubbleMetadata`, using the fixed host shortcut and no group key/delete intent.
- Post/update the Message test and confirm the same host notification key is updated rather
  than a second bubble being added, and confirm ID `100` is not retained beside the host.
- Confirm an ordinary replacement removal does not remove the host notification.
- Disable trampoline and confirm the host is removed and restorable per-conversation bubble
  metadata returns.
