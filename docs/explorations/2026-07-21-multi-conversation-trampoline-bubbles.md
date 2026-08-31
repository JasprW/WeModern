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

host 保留也改变了旧同步移除逻辑的语义。微信进入某个聊天时会批量 APP_CANCEL 多个会话，
因此不能再把任意源通知 removal 当作精确的“该会话已打开”信号；直接恢复旧的
`histories.remove()` / `ConversationBubbleStore.remove()` 会同时删除其他仍未阅读的 Bubble。
但完全跳过清理又会让被打开会话的 `MessagingStyle` 历史持续累积，下一条新消息重新显示已看过
的内容。

当前实现使用两层已读边界。Bridge 确认 Bubble 启动且微信会话 token 提交成功后，
立即使用自带 conversationId 清空被点击会话的服务内消息历史和 Bubble 消息快照。同时
5 秒 APP_CANCEL 保护窗口也保存这个 conversationId：窗口内只允许被点击会话清历史，
微信同批撤销的其他 Bubble 仍保留未读内容。窗口外的正常 sync removal，或者全屏微信
前台导致的移除，即使活动 Bubble host 需继续保留，也会清空相应会话历史。状态对象
本身不删除，title、shortcut 对应关系、最新 immutable token、活动通知 host 与 task 均继续
保留。这样把“Bubble 还存活”与“旧消息是否已读”分成独立生命周期。

## 2026-08-25 普通 WeModern 通知点击的已读缺口

静态检查确认，“从 WeModern 普通通知进入微信后，下一条通知仍包含旧历史”有一条
不依赖设备日志即可证实的代码竞态：

1. 消息通知使用 `setAutoCancel(true)`，content intent 是
   `WeChatLaunchProxyActivity.wrap(..., "message:" + conversationKey, target)`。
2. Proxy 只检查来电 launch key；消息 launch key 虽然已携带精确 conversationId，但没有解析，
   也没有调用已存在的会话已打开清理入口。Proxy 只执行 `BubbleLaunchCleanup.clear()`、
   发送微信 target 然后 finish。
3. Android 点击后的 auto-cancel 会进入 WeModern 自通知 removal 分支。该分支会删除
   `ConversationBubbleStore` 状态，并通过 `forgetReplacementsForReplacementId()` 删除内存和
   持久化 replacement 映射，但没有删除服务内 `histories[conversationId]`。
4. 微信随后 APP_CANCEL 原通知时，服务已无法通过 replacement 映射恢复 conversationId，
   因此 sync removal 无法补做历史清理。后续新消息会继续 append 到旧 `histories` 并重新显示
   已读内容。

现已把 `markConversationOpenedFromBubble()` 的底层实现泛化为会话级入口。Proxy 从
`message:<conversationId>` 提取精确 ID，并只在微信 PendingIntent 成功发送后立即清空该会话
`histories` 与快照；发送失败而回退到微信 Home 时不标记精确会话已打开。该主动
已读边界不再依赖随后 auto-cancel / APP_CANCEL 的回调顺序。当前手机未连接，代码与
本地单测完成后仍需下次连接设备做真实通知点击回归。

同一轮测试还确认了另一条微信返回路径。第一次 Back 的输入事件后，微信以 app-request 在
同一个 Bubble task 中启动 `LauncherUI`，随后结束 `ChattingMainUI`；旧实现只有 Bridge 的
`onResume` 收起逻辑，因此必须第二次 Back 退出微信 Home 后才能获得控制。这不是 host 清理
修复刻意加入的行为，而是此前未覆盖的微信内部返回栈分支。

Bridge 现在使用 `startIntentSenderForResult` 转发会话 token。Pixel 9 Pro / API 37 上，
`ChattingMainUI` 结束时 result 会在 `LauncherUI` 仍位于 Bridge 之上时送达；Bridge 通过匹配
task ID 的 `ActivityManager.AppTask` 启动带 `CLEAR_TOP | SINGLE_TOP` 的内部 collapse intent，
清掉其上的 `LauncherUI`，再由恢复的 Bridge 调用 `moveTaskToBack(true)`。系统日志顺序为
`ChattingMainUI → LauncherUI → COLLAPSE_TRAMPOLINE_AFTER_TARGET_RESULT`，用户确认第一次 Back
直接收起 Bubble。若某个系统只在 Bridge 已恢复后才投递结果，原有 `onResume` 路径仍会收起，
但无法保证提前清掉微信 Home；这仍是跨应用 immutable Activity token 的平台边界。

## 2026-08-31 微信 8.0.72 返回状态机

后续真机重新复现证明上面的 result 路径不是稳定契约：task `24397` 中第一次 Back 先由微信
结束 `ChattingMainUI` 并恢复 `LauncherUI`，Bridge 始终处于 stopped，既没有恢复也没有收到
可执行 clear-top 的 result，因此用户看到微信 Home。当前修复不再等待单一生命周期信号，而是
复用已经为前台判定和同步移除运行的 activity events watcher：

- Bridge 登记逐会话 task 后，只在同一 task resume 过 `ChattingMainUI` 或旧版 `ChattingUI`
  的前提下，接受紧随其后的 `LauncherUI` resume；未登记 task、共享 Home task、不同 task、
  普通全屏微信和没有聊天前序的 Home 都不会触发。
- 命中后由通知服务在主线程通过对应 `ActivityManager.AppTask` 启动 Bridge 的
  `CLEAR_TOP | SINGLE_TOP` collapse intent，暂时找不到 task 时以 100ms / 200ms 做最多三次
  有界尝试，避免无限轮询。
- collapse intent 不替换 Bridge 原本保存的会话 target，因此收起后重新展开仍可转发同一
  微信会话。一次转换只触发一次；新一轮 Bridge 转发会重置 task 状态。
- result 先到但 AppTask 暂不可用时，如果微信仍覆盖 Bridge，不再立即调用
  `moveTaskToBack(true)`，而是等待 watcher clear-top 或 Bridge 真正恢复，避免此前调查到的
  “展开后无操作自动退出”风险。

这条兜底依赖 `READ_LOGS` 和 `persist.log.tag.NotificationService=DEBUG`；Android 没有提供
无需特权日志即可观察其他应用顶层 Activity 转换的普通应用 API。本地单元测试已经覆盖正向
转换、无聊天前序、不同 task、共享 task、重复事件和失败后重新武装；安装后的第一次 Back、
重新展开、多 Bubble 并存仍需 Pixel 9 Pro / API 37 真机复测。

## 2026-08-31 Bubble 启动视觉接力

逐会话 Bridge 原先沿用 `BubbleActivityTheme` 的 `windowDisablePreview=true`，自身又没有 content
view，SystemUI 展开 Bubble 到微信 `ChattingMainUI` 首帧之间因此只能显示透明窗口。当前实现为
Bridge 单独提供不透明 starting window，并将启动拆成连续的两层：

- 系统 starting window 只显示跟随 API 31 动态中性色板、同时支持浅色与深色模式的 opaque
  surface，不显示无法按 Intent 动态变化的静态应用头像。
- Bridge 使用轻量 Android View 同步读取已有 conversation shortcut 头像缓存，第一帧显示圆形
  会话头像，缺失时回退 Bubble glyph；第一帧完成后通过 `OnPreDrawListener` 立即提交微信 token，
  只增加一次绘制机会，不添加固定 splash 时长。
- 头像以 160ms 的轻微 alpha / scale 进入；启动超过 300ms 才显示 progress 与本地化会话标题。
  `ValueAnimator.areAnimatorsEnabled()` 为关闭系统动画的设备提供即时状态路径。
- 每次重新展开或 Bubble PendingIntent 更新都会重置头像和延迟状态；微信接管窗口后 Bridge
  `onPause` 会停止未发生的加载提示。共享 Home Bubble 不使用 Bridge，保持原行为。

本地资源编译和 JVM 回归已通过。Pixel 9 Pro / API 37 使用与 Bubble 相同的
`1184×1800 px` 窗口尺寸运行 debug-only visual harness，确认真实缓存会话头像能够圆形显示，
浅色和深色动态 surface 对比正常，1.3 倍字体可自然换为两行而不截断；harness 随后已从源码和
安装包移除。真实通知 Bubble 的系统 starting window、快速/冷启动交接仍需下一条会话通知复测。

## 2026-08-25 展开后偶发自动退出

Pixel 9 Pro / API 37 的 events buffer 保留了一次与“点击 Bubble 后立即退出并消失”
高度一致的完整窗口时序：

- `10:11:27.996`：会话 host `866529438` 仍带 `FLAG_BUBBLE` 并成功更新。
- `10:11:28.042`：SystemUI 创建 task `23663` 及 `TrampolineBridgeActivity`。
- `10:11:28.074`：同一 task 内成功创建微信 `ChattingMainUI`；`10:11:28.959`
  聊天窗口已获得 focus。
- `10:11:28.571`：微信批量 APP_CANCEL 源通知，但 WeModern host 没有在这一时刻
  被取消，证明现有 host 保留分支已生效，同步移除不是本次退出的起点。
- `10:11:29.905`：聊天窗口无用户 Back 记录即失去 focus；`10:11:29.965` task 被
  移到后台，原全屏应用恢复前台。
- `10:11:30.905`：Android 以 `remove-task-through-hierarchyOp` 同时销毁 Bridge 和
  `ChattingMainUI`。之后 `10:11:30.917` 才触发 Bubble delete intent，由 WeModern 取消 host；
  `10:11:31.817` task 完全移除。

因此 host 通知取消是 SystemUI 删除 Bubble 之后的结果，不是原因。当时 Bridge 没有
`onResume` 记录，而代码中另一条可在 Bridge 处于 stopped 时把 task 移到后台的路径是
`onActivityResult → requestCollapseAtBridgeRoot() → collapseBubble()` 的 AppTask 查找失败兜底。
日志中也没有成功执行 `CLEAR_TOP` collapse intent 应产生的 Bridge `wm_new_intent`，与该兜底
路径一致。现有 main log buffer 已无当时的 `WeModern` 应用日志，因此尚不能直接证明
是微信过早返回 result，但这是当前证据最支持的原因。

本轮只记录调查，未改动返回栈。后续修复应避免在 Bridge 未恢复且无法找到 Bubble
`AppTask` 时立即执行 `moveTaskToBack(true)`，同时不能破坏已验证的
`ChattingMainUI → LauncherUI → 第一次 Back 收起` 路径。下次复现后应立即保留
`WeModern` main log，以确认 `onActivityResult` 的 resultCode、Bridge resume 状态和 AppTask 查找结果。

返回验证后观察到全屏微信的系统手势导航条绘制在应用底部 tab 区域内。对照排除了 WeModern
启动 flags：从 Pixel Launcher 的真实微信图标启动时，调用方为 Nexus Launcher 且使用标准
`MAIN` / `LAUNCHER` / `0x10200000`，force-stop 后冷启动也显示相同。窗口状态显示微信 8.0.69
自身 `targetSdk=35`，`LauncherUI` 被系统标记为 `EDGE_TO_EDGE_ENFORCED`，并主动请求
`LAYOUT_HIDE_NAVIGATION`；WeModern 无法修改另一个应用窗口的 inset。该显示问题不引入额外
权限或设备级 compat workaround，也不归因于本次 Bridge 返回优化。

## IME 覆盖的 A/B 验证

Pixel 9 Pro / API 37、微信 8.0.69 按以下顺序完成了三组真机对照：

1. Bridge 临时从 `startIntentSenderForResult` 改回普通 `startIntentSender`。Bubble task 仍为
   Bridge 根、`ChattingMainUI` 位于其上，唤出 IME 后输入栏继续被覆盖。因此 result 启动方式
   不是根因。
2. Bubble 直接以微信 `LauncherUI` 为根，再从微信 Home 内点击会话。聊天内容仍由同一个
   `LauncherUI` Activity 承载，task 中没有独立 `ChattingMainUI`；唤出 IME 后输入栏会正确移到
   键盘上方。
3. Bridge 先把 `LauncherUI` 放进同一个 Bubble task，再调用该会话原始通知 token。最终同一
   task 内依次为 Bridge、`LauncherUI`、`ChattingMainUI`，输入栏仍被 IME 覆盖。因此仅仅预热
   微信 Home，甚至让 Home 与聊天共处同一 Bubble task，都不能修复通知 token 路径。

第三组的系统窗口已按 IME 正确裁剪到屏幕 y=1735，但 `ChattingMainUI` 的客户端 DecorView
仍按 1184×1800 布局，`ChatFooter` 的局部 y 范围为 1465..2286，落在裁剪后的可见区域之外。
这说明 SystemUI/Bubble task 的 resize 已发生，未响应这次可见区域变化的是微信独立
`ChattingMainUI` 内部布局。相比之下，微信 Home 内部导航没有创建该 Activity，仍由
`LauncherUI` 的内容布局响应 IME，所以表现正常。

结论是动态缩短 Bubble `desiredHeight` 只能改变外层裁剪范围，无法迫使另一个应用的
`ChattingMainUI` 重排 `ChatFooter`，甚至可能进一步减少可见区域。无需额外权限的干净路径只能
是继续使用精确通知 token 并接受该微信版本的 IME 限制，或者改走 `LauncherUI` 内部导航；
后者没有可稳定调用的会话定位 API，不能可靠保留当前“一会话一 Bubble、分别精确直达”的
行为。验证中曾临时解绑通知监听器，以避免微信撤销源通知时删除 Home-first 实验 Bubble；这只
用于保持对照环境，不是产品方案。

SystemUI 同时只展开一个 Bubble；“并存”指 Bubble 栈中保留多个入口。其他 Android 或
微信版本仍可能复用现有全屏 task，因为 WeModern 无法修改 immutable PendingIntent 的
内部 flags。目标缺失、类型无效或 token 已取消时，真实微信通知不会附加 trampoline
Bubble，而不是回退到已经删除的固定 Home host。

## 2026-08-26 重新提供共享微信 Home 模式

IME A/B 结论表明，遮挡差异不是 Bridge 或 result API 本身造成，而是通知精确 token 创建的
独立 `ChattingMainUI` 不响应 Bubble 可见区域 resize；由 `LauncherUI` 内部导航进入聊天则能
正确适配 IME。基于这个产品取舍，trampoline 不再只有会话级模式，而是由正式开关提供两种
互斥行为：默认会话级 Bubble 保留精确直达与多会话入口；关闭开关后使用固定共享 host，只启动
微信 Home，由用户在内部选择对话。

新的共享模式只恢复历史单 host 方案的稳定身份和 Home PendingIntent，不恢复已淘汰的实验性
精确桥接、双 host 轮换或专用 channel。逐会话 replacement 继续存在以承载通知内容、历史和
sync removal，但不携带 BubbleMetadata，并使用 `GROUP_ALERT_SUMMARY` 禁止重复提醒；固定 host
复用统一 `wechat_messages_alerts` channel、固定通知 ID 与 fixed long-lived shortcut，脱离消息
分组并负责唯一的 flyout、声音和振动。activity create 日志以 action 为空的 `LauncherUI`
识别共享 Bubble task，避免将它误判为全屏微信。该模式的实际 IME、提醒抑制、host 更新与
task 返回行为仍需 Pixel 真机验收。
