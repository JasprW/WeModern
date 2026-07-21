# Bubble channel 初始化调查

## 问题

v1.7.1 中普通微信消息与 Bubble 看似同时消失。真机检查确认消息解析和改写功能正常，但允许气泡的会话被发布到 `wechat_messages_bubbles_quiet`：该 channel 默认 `IMPORTANCE_LOW`、无声音且无振动。当 Android 仍处于“仅所选会话可气泡”且当前会话尚未获准时，系统既不显示气泡，也不会给这条低重要性消息声音或 heads-up，形成两项能力同时失效的观感。

历史固定 trampoline host 还有独立的 selected-conversation 许可问题；该架构现已删除。当前 trampoline 与常规模式都以普通 per-conversation 通知作为 Bubble 身份。

## 平台证据

Android 官方 Bubble 文档说明，用户可以禁止全部气泡、仅允许所选会话或允许所有会话；在“仅所选会话”模式中，用户通过通知上的气泡按钮选择具体会话。Android SDK 36 的 `NotificationChannel.setAllowBubbles(boolean)` 文档明确注明：Android 11 起该值不再被系统采用。因此目标 API 37 的普通应用不能在 channel 创建时替用户默认打开 host conversation 的气泡许可。

通知 channel 的重要性、声音和振动在首次创建后由用户掌控；用相同 ID 重新创建不会覆盖已有行为。修复旧的静音默认值必须停止使用并删除旧 channel，或使用新 channel。Pixel 上已删除的 `wechat_messages_bubbles_quiet` 仍保留 importance 3、系统声音、振动和用户锁定字段，重新创建会恢复这份错误历史，因此不能复用。最终方案保留高重要性的 `wechat_messages_alerts` 作为可靠回退，并以全新、无版本后缀的 `wechat_messages_bubbled_quiet` 创建受实际 Bubble 可用性保护的低重要性 channel；临时 `wechat_messages_bubbles_quiet_v2` 同样迁移删除。

## 已实现结论

- Bubble 实际可展示且会话策略允许时，普通替换消息发布到 `wechat_messages_bubbled_quiet`；该 channel 为 `IMPORTANCE_LOW`、无声音、无振动，不显示 heads-up。
- Chat bubbles 关闭、会话策略禁用或系统 Bubble 不可用时，普通消息发布到 `wechat_messages_alerts`，继续保留声音、振动与 heads-up。
- 常规与 trampoline Bubble 都只在 Android 允许所有会话时使用 quiet channel；“仅所选会话”继续使用 alerting parent，避免切换 parent 后丢失具体会话许可。
- 升级时删除 `wechat_messages_bubbles_quiet` 和 `wechat_messages_bubbles_quiet_v2`，不再重新创建或复用这两个旧 ID。
- BubbleMetadata 不能逐通知覆盖 channel importance、声音、振动或 heads-up，因此 alerting 与 quiet 两条消息 channel 不能合并。
- 固定 host channel `wechat_bubble_host_visual_alerts`、host shortcut、三个历史 host 通知 ID 和对应设置卡均已删除；升级时清理残留。

## 不可实现边界

Android 11+ 没有面向普通应用的公开 API 可以强制把某个 channel / conversation 默认设为可气泡。绕过用户选择需要系统权限或非公开接口，不适合产品实现。单条通知的 BubbleMetadata 也不能把高重要性 channel 的该条通知单独降为静音，所以仍须保留 `wechat_messages_alerts` 与 `wechat_messages_bubbled_quiet` 两个语义稳定的 channel。

## 验证要求

Pixel 9 Pro / Android 17（API 37）已完成稳定 ID 的升级检查：`wechat_messages_bubbled_quiet` 为 `mImportance=2`、`mSound=null`、`mVibrationEnabled=false`；`wechat_messages_bubbles_quiet` 与 `wechat_messages_bubbles_quiet_v2` 均为 `mDeleted=true`。旧无后缀 ID 的已删除记录仍保留 importance 3、系统声音、振动和用户锁定字段，进一步证明不能复用。

- 全新安装后确认 `wechat_messages_alerts` 为高重要性并使用默认通知声音与振动。
- 升级安装后确认 `wechat_messages_bubbles_quiet` 与 `wechat_messages_bubbles_quiet_v2` 均已删除，`wechat_messages_bubbled_quiet` 为低重要性、无声音、无振动。
- 分别在 Android 气泡设置的“所有会话”和“仅所选会话”模式下验证普通与 trampoline Bubble；后者需确认每个目标会话可单独允许。
- 确认“所有会话”下合资格微信消息不显示 heads-up；关闭 Bubble、禁用单会话策略或切回“仅所选会话”后，后续消息回到 alerting channel。
- 升级后确认固定 host channel 为 deleted，且没有固定 host 通知或 shortcut 残留。
