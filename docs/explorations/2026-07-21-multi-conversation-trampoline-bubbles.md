# 多会话 Trampoline Bubble

## 状态

已实现为默认关闭的 **多会话气泡（实验性）**，仅在 Chat bubbles、Bubble trampoline
和 **精确打开会话（实验性）** 都生效时可用。JVM 测试、Debug 构建与 lint 是合入
门槛；两个真实微信会话的 Pixel 真机验收尚未完成。

## 目标与平台模型

Android Bubble 的稳定身份是 conversation shortcut 与通知，而不是单条消息。因此可行
语义是“每个会话一个 Bubble”：同一会话的后续消息更新原 Bubble，不同会话使用不同
Bubble。Android 11 及以上会把 conversation Bubble 作为 document task 启动；Activity
也必须允许多实例，且每条 Bubble 通知要引用对应 long-lived conversation shortcut。

## 已实现路径

1. 沿用普通改写消息的稳定 per-conversation 通知 ID、shortcut ID 和 LocusId，不再为
   实验路径新增一组并行 host 通知。
2. 每条合资格消息的 BubbleMetadata 指向 conversation-specific mutable
   `TrampolineBridgeActivity` PendingIntent；Bridge 再验证并转发该会话最新、由微信创建的
   immutable Activity PendingIntent。
3. Bridge 声明 `documentLaunchMode="always"`，PendingIntent 的 request code 与 URI 都包含
   会话身份。不同会话可形成独立 SystemUI task，同一会话通知更新保持稳定身份。
4. session 状态从单 task 改为 task ID → 会话 ID / host 类型映射。固定 host 替换或清理
   只清除共享 host session；独立会话 task 的移除不会影响其他 Bubble。
5. 微信打开聊天后会批量撤销多个源通知。嵌入 session 期间，多会话模式保留所有仍带
   BubbleMetadata、匹配 shortcut 且被系统标记为 `FLAG_BUBBLE` 的活动改写通知，而不是
   只保留当前 Bridge 会话；Bubble 的 delete intent、全屏微信启动和全量清理仍会删除
   通知与 session，未真正 Bubble 的普通通知继续同步删除。

## 权限和 channel 边界

固定 trampoline host 的 selected-conversation 许可不能迁移给多个普通会话。多会话模式
下，Android 选择 **All conversations** 才能让所有合资格通知直接 Bubble，并允许它们路由
到低重要性静音消息 channel。若系统选择 **Selected conversations**，每个目标会话需要
单独允许；实现保留 alerting parent channel，避免切换 parent 后丢失已选择会话的 Bubble
许可，因此普通通知仍可能 heads-up。固定 host channel 设置卡在多会话模式下隐藏。

## 风险与待验收

Pixel 9 Pro / API 37 已完成单会话发布检查：真实群聊替换使用普通稳定通知 ID，记录带
`BUBBLE` flag、`isBubble=true`、有效的 per-conversation shortcut，并落在
`wechat_messages_bubbled_quiet`；启用实验后固定 trampoline host 未重新发布。这证明
per-conversation host 主链路已生效，但不等同于双会话 task 切换验收。

首次双 Bubble 测试发现，展开最后一个会关闭前一个。同步日志显示微信在嵌入启动时连续
发出 `id=4097…4101` 的 `APP_CANCEL`；旧实现只按“当前已嵌入会话”保护 replacement，
因此尚未展开的前一个 host 被删除。实现已改为检查活动 replacement 的真实 Bubble flag、
metadata 和 shortcut，修复版本仍需按下方用例复验。

- 微信可能在不同版本上把目标 Activity 复用到现有全屏 task；immutable token 的内部
  flags 仍无法由 WeModern 改写。
- SystemUI 同时只展开一个 Bubble；“并存”指 Bubble 栈中保留多个入口，不是同时显示
  多个聊天窗口。
- launcher dynamic shortcut 有可见数量限制；SystemUI 对活跃 long-lived conversation
  shortcut 的缓存行为需要用超过两个会话继续观察。
- 必须在 Pixel 9 Pro / API 37 上用两个真实私聊验证：A/B 同时出现、各自直达、A 的 Back
  仅收起、B 新消息后重新展开进入 B、拖走 A 不影响 B，以及全屏微信启动清理全部 Bubble。
