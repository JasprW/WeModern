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

`wechat_messages_alerts` 与 `wechat_messages_bubbled_quiet` 不能合并为一个 channel 后只靠
BubbleMetadata 切换。Android channel 的 importance、声音、振动和 heads-up 策略会应用
到 channel 内所有通知，创建后还会由系统和用户持久化；BubbleMetadata 只声明某条通知
具备 Bubble 能力，不会把高重要性 channel 的该条通知单独降级。

因此常规和 trampoline 模式共用以下路由：

- Android 允许所有会话 Bubble、Chat bubbles 已就绪且会话策略允许时，使用低重要性、
  无声音、无振动的 `wechat_messages_bubbled_quiet`。
- Bubble 不可用、会话禁用或 Android 只允许所选会话时，使用高重要性的
  `wechat_messages_alerts`。Selected 模式下保持 parent channel 稳定，避免切换后丢失
  已选择会话的 Bubble 许可。

旧固定 host 的 `wechat_bubble_host_visual_alerts` 不再有发布者，升级时删除。

## 真机证据与边界

Pixel 9 Pro / API 37 已确认：

- 两个真实会话可在 Bubble 栈中同时保留，并分别打开对应微信聊天。
- 直接在聊天页按 Back 会收起当前 Bubble，而不是删除它。
- 会话收到新通知后重新展开，可转发到该会话的新聊天目标。
- 微信进入聊天时会连续 APP_CANCEL 多个源通知。将保护范围扩大到所有真实活动 Bubble
  host 后，展开后一个 Bubble 不再关闭前一个。
- 真正 Bubble 的替换通知使用稳定会话 ID、有效 shortcut、`FLAG_BUBBLE` 和 quiet channel；
  没有重新创建固定 host。

SystemUI 同时只展开一个 Bubble；“并存”指 Bubble 栈中保留多个入口。其他 Android 或
微信版本仍可能复用现有全屏 task，因为 WeModern 无法修改 immutable PendingIntent 的
内部 flags。目标缺失、类型无效或 token 已取消时，真实微信通知不会附加 trampoline
Bubble，而不是回退到已经删除的固定 Home host。
