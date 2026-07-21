# 多会话 Trampoline Bubble

## 状态

Pixel 9 Pro / API 37 已完成双会话真机验收，现作为 **Bubble trampoline** 的唯一预设
行为交付，不再保留“精确打开会话”或“多会话气泡”实验子开关。历史固定单 host 架构
及其通知、shortcut、channel 和设置入口已删除。

## 平台模型

Android Bubble 的稳定身份是 conversation shortcut 与通知，而不是单条消息。因此当前
语义是“每个会话一个 Bubble”：同一会话的后续消息更新原 Bubble，不同会话使用不同
Bubble。Android 11 及以上会把 conversation Bubble 作为 document task 启动；Activity
也必须允许多实例，且每条 Bubble 通知要引用对应 long-lived conversation shortcut。

## 当前实现

1. 沿用普通改写消息的稳定 per-conversation 通知 ID、shortcut ID 和 LocusId，不新增
   并行固定 host 通知。
2. 每条合资格消息的 BubbleMetadata 指向 conversation-specific mutable
   `TrampolineBridgeActivity` PendingIntent；Bridge 验证后转发该会话最新、由微信创建的
   immutable Activity PendingIntent。
3. Bridge 声明 `documentLaunchMode="always"`，PendingIntent request code 与 URI 都包含
   会话身份。不同会话形成独立 SystemUI task，同一会话通知更新保持稳定身份。
4. session 状态保存 task ID → 会话 ID。移除一个 task 或拖走一个 Bubble 不影响其他
   会话；全屏微信启动或关闭 Bubble 功能仍可执行全量清理。
5. 微信打开聊天后可能批量撤销多个源通知。嵌入 session 期间，服务保留所有仍带
   BubbleMetadata、匹配 shortcut 且被系统标记为 `FLAG_BUBBLE` 的活动改写通知；未真正
   Bubble 的普通通知继续同步删除。
6. Bridge 常驻为 task 根。聊天页 Back 返回 Bridge 时调用 `moveTaskToBack(true)`，让
   SystemUI 收起 Bubble；再次展开时重新转发当前会话的最新目标。

## Channel 结论

常规和 trampoline 模式统一使用 `wechat_messages_alerts`。Pixel 9 Pro / API 37 对照实验
把 Message 测试明确发布到该 importance 4 channel，同时保留 BubbleMetadata、conversation
shortcut 和相同通知内容。系统接受后 `dumpsys notification` 显示 `FLAG_BUBBLE`、
`isBubble=true`；屏幕只出现 Bubble flyout，SystemUI row 为 `isHeadsUpState=false`、
`isPinned=false`，没有普通顶部 heads-up。

这不是 BubbleMetadata 改写了 channel 行为：同一记录仍有系统 `mSound`、`mVibration`，且
`mIsInterruptive=true`。合并的产品语义是“Bubble 由 SystemUI 用 flyout 取代普通 HUN，
但仍按消息 channel 发声和振动”，不是静音 Bubble。

升级时先把历史 quiet channel 上的活动会话通知和摘要重发到 `wechat_messages_alerts`，再
删除 `wechat_messages_bubbled_quiet`、`wechat_messages_bubbles_quiet`、
`wechat_messages_bubbles_quiet_v2`。旧固定 host 的 `wechat_bubble_host_visual_alerts` 也继续
作为迁移项删除。

## 真机证据与边界

Pixel 9 Pro / API 37 已确认：

- 两个真实会话可在 Bubble 栈中同时保留，并分别打开对应微信聊天。
- 直接在聊天页按 Back 会收起当前 Bubble，而不是删除它。
- 会话收到新通知后重新展开，可转发到该会话的新聊天目标。
- 微信进入聊天时会连续 APP_CANCEL 多个源通知。将保护范围扩大到所有真实活动 Bubble
  host 后，展开后一个 Bubble 不再关闭前一个。
- 真正 Bubble 的替换通知使用稳定会话 ID、有效 shortcut、`FLAG_BUBBLE` 和统一消息 channel；
  没有重新创建固定 host。

后续三会话测试发现新的 host 清理竞态：米老鼠 Bubble 已经展开并收起后，JASPR 与群聊测试
两个新 Bubble 到达；首次展开群聊测试时两个新入口都退出，只留下旧 Bubble。安装第一版推测
修复后，单个群聊测试 Bubble 仍可稳定复现，完整应用日志还原出以下顺序：

- SystemUI 为群聊测试创建 `TrampolineBridgeActivity` 与微信 `ChattingMainUI` 的 Bubble task。
- Bridge 报告 `platformBubble=true`，并成功转发微信 immutable 会话 token。
- 微信 app-cancel 源通知；服务确认嵌入 session 有效并记录 `preserve trampoline bubble host`。
- 保留分支消费源 replacement 映射后，SystemUI 回送同一通知的 `FLAG_BUBBLE` 状态更新。
- `onNotificationPosted` 的 orphan 清理因映射已删除而主动取消 host；约 570ms 后 task 被移除。

因此根因不是 Bridge 启动身份或微信 task flags，而是“已消费源映射”和“仍存活 Bubble host”
被错误地视为同一生命周期。孤儿清理现在单独识别仍有内存会话状态、稳定通知 ID、匹配
shortcut、BubbleMetadata 与 `FLAG_BUBBLE` 的 trampoline host；它不再依赖已消费的源映射。
进程重建后没有内存会话状态的残留仍会清理，避免永久保留真正 orphan。对应纯策略单测覆盖
无持久映射但活动 trampoline host 为 true 的组合；单会话与精确三会话交互仍作为安装后的
真机回归项。

SystemUI 同时只展开一个 Bubble；“并存”指 Bubble 栈中保留多个入口。其他 Android 或
微信版本仍可能复用现有全屏 task，因为 WeModern 无法修改 immutable PendingIntent 的
内部 flags。目标缺失、类型无效或 token 已取消时，真实微信通知不会附加 trampoline
Bubble，而不是回退到已经删除的固定 Home host。
