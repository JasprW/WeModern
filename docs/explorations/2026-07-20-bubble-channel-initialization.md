# Bubble channel 初始化调查

## 初始问题

v1.7.1 中普通微信消息与 Bubble 看似同时消失。允许气泡的会话被发布到低重要性、无声音、
无振动的 `wechat_messages_bubbles_quiet`；当 Android 尚未真正允许该会话 Bubble 时，系统
既不显示 Bubble，也不提供声音或 heads-up。历史固定 trampoline host 还存在独立的
selected-conversation 许可问题；该 host 架构现已删除。

## 平台边界

Android 11+ 不接受普通应用通过 `NotificationChannel.setAllowBubbles(true)` 替用户默认
开启会话 Bubble。用户仍可禁止全部、仅允许所选会话或允许所有会话。Channel 的 importance、
声音和振动在创建后由系统与用户持久化；BubbleMetadata 不会逐通知改写这些字段。

这意味着把 Bubble 放到高重要性 channel 后，声音和振动仍会生效。但是否同时显示普通
heads-up 还取决于 SystemUI 已经把该通知接受为 Bubble 后的展示策略，需要真机验证，不能只
从 channel API 推导。

## Pixel 对照实验

Pixel 9 Pro / Android 17（API 37）上，把 Message 测试强制发布到
`wechat_messages_alerts`，其余条件保持不变：

- `dumpsys notification`：importance 4、`channel=wechat_messages_alerts`、BubbleMetadata、
  `FLAG_BUBBLE`、`isBubble=true`。
- 屏幕：只出现锚定在 Bubble 图标旁的 Bubble flyout，没有普通顶部 heads-up 卡片。
- SystemUI：通知 row 的 `isHeadsUpState=false`、`isPinned=false`，HeadsUpManager 没有该 key。
- 通知记录仍包含系统 `mSound` 和 `mVibration`，并标记 `mIsInterruptive=true`。

因此在该设备/系统上可以合并消息 channel：Bubble 的 flyout 取代普通 HUN，但消息仍按统一
channel 发声和振动。该结论不能表述为“BubbleMetadata 把高重要性通知静音”。

## 当前实现

- 所有改写消息只发布到 `wechat_messages_alerts`。
- Chat bubbles 关闭、会话禁用或系统未接受 Bubble 时，同一 channel 保留正常通知提醒。
- 升级时先恢复并迁移历史 quiet channel 上的活动消息及摘要，再删除
  `wechat_messages_bubbled_quiet`、`wechat_messages_bubbles_quiet` 和
  `wechat_messages_bubbles_quiet_v2`。
- 固定 host channel `wechat_bubble_host_visual_alerts`、host shortcut、三个历史 host 通知
  ID 和对应设置卡也保持删除。

## 验证要求

- 确认 Message 测试和真实 Bubble 消息都使用 `wechat_messages_alerts`，被系统接受为 Bubble
  后只有 flyout、没有普通 pinned HUN。
- 确认未成为 Bubble 的消息仍以高重要性正常提醒。
- 升级安装后确认三个 quiet 消息 channel 与固定 host channel 均为 deleted，且活动真实
  Bubble 已迁移到统一 channel。
- 在其他 Android / SystemUI 实现上回归；如果厂商同时显示 Bubble flyout 和普通 HUN，需
  重新评估是否恢复独立 quiet channel。
