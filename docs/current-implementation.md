# WeModern 当前实现

- 当前版本：1.6.1（`versionCode` 32）
- 支持范围：`minSdk` 26；编译和目标 API 37（Android 17）
- 最近代码状态：2026-07-19

## 0. 微信通知采集调试模式

当前开发分支先撤回了所有 `v1.6.1` 之后缺少完整证据的语音/视频通话改写实验，随后根据 capture-only JSONL 的完整样本重新实现了窄范围状态机。Pixel 9 Pro / API 37 已确认：把微信 `id=41` 来电 channel 和 `id=40` 的 `reminder_channel_id` 都手动设为 Silent + Minimize 后，语音标准 CallStyle 表现符合预期，接通后的 Live Update 能显示。针对原有约 5 秒延迟，当前实现增加由活跃微信状态通知门控的音频模式接通确认；语音真机复测确认升级时机已紧随实际接通，体感延迟问题解决。最新完整视频 capture 在 ID、channel、flags 转换、RemoteViews、PendingIntent 和结束 reason 上均与语音同构，因此视频复用相同优化，仅切换视频文案、图标和 `CallStyle.setIsVideo(true)`；视频 rewrite 可见结果仍待下一通真机验收。设置界面提供两个互不依赖、运行时立即生效的 Debug 开关：总开关“采集与日志”控制微信原始通知采集及其 logcat / JSONL 输出，“改写通知”单独控制解析、隐藏、取消或 snooze、替换通知、Live Update、气泡 host 和同步移除日志 watcher。开发阶段默认开启采集、关闭改写。

本轮每次通话实验、真机异常、回滚内容、新样本证据和当前可用基线均保存在[微信语音/视频通话通知实验与采集记录](explorations/2026-07-18-wechat-call-notification-capture.md)。历史启发式方案仍属已撤回探索；当前开发代码只包含其中 2026-07-19 证据驱动的窄范围状态机，语音和视频共用该状态机，不引入独立视频启发式分支。

关闭改写时，通知监听器不会执行消息或通话解析，不取消或 snooze 微信通知，也不创建任何替换通知、Live Update 或 bubble host；同时取消残留的 WeModern 自有通知并清空临时 replacement 映射。每次事件入口和 `hideOriginal()` 都直接读取当前 rewrite preference，后者作为防御性保护，即使被误调用也不会移除微信原始通知，并避免开关异步通知期间使用过期缓存值。清理残留项使用 WeModern 自己的 `NotificationManager.cancelAll()`，只影响本应用发布的通知。采集开启时仍记录 `com.tencent.mm` 的 `ACTIVE_SCAN`、`POSTED` 和 `REMOVED` 事件；采集关闭时不再写入新记录或输出 `WeModern.Capture` 日志。改写开启后运行消息基线和本节记录的新通话状态机，采集可以独立保持开启或关闭，且原始事件总是在改写发生前记录。

每条记录包含 StatusBarNotification 标识、时间、flags、channel、category、标准 extras、图标描述、RemoteViews 包名与 layout ID、actions、PendingIntent 创建方、声音/振动字段及当前 ranking/channel 信息。记录同时输出到 logcat tag `WeModern.Capture`，并追加到应用私有文件 `files/wechat_notification_capture.jsonl`；文件达到 16 MiB 时轮换为 `wechat_notification_capture.previous.jsonl`。

## 1. 微信通知重写

“改写通知”开启时，`WeChatNotificationService` 作为 `NotificationListenerService` 监听 `com.tencent.mm`，识别消息和 VoIP 通知，并以 WeModern 通知替换原始通知。消息使用 `MessagingStyle`，保留会话标题、发送者头像、最多 8 条去重历史、会话 shortcut 与原始 `contentIntent`；普通消息归入 `wechat.rewritten` 分组并维护摘要通知。关闭改写会完全绕过本节行为。

原始微信通知在成功重写后会被隐藏，避免并存重复内容。通知服务重连时会扫描活动通知、恢复会话入口、刷新快捷方式图标并清理孤儿替换通知。不能解析的微信通知不会被改写。

### 同步移除

系统正常回调会删除相应替换通知、历史与气泡状态。对于 Android 未可靠暴露的微信应用取消场景，`NotificationCancelLogWatcher` 可在授予 `READ_LOGS`、开启 `NotificationService` DEBUG 日志并重启后，从日志中定位原始通知并清理替换项。替换映射会持久化，服务重启后仍可恢复清理。

这是一项可选的高级可靠性能力：没有 ADB 条件时，重写和通常的移除回调仍可用；不能承诺覆盖所有厂商系统日志行为。

## 2. 会话通知与气泡

### 常规会话气泡

Android 10（API 29）及以上可为每个允许的会话附加 `BubbleMetadata`。气泡内容是 `BubbleConversationActivity`：显示本地最近消息快照，并通过微信原始 `PendingIntent` 打开精确聊天。气泡 activity 可嵌入、可调整大小、以 document 模式多实例启动，并可在进程重建后恢复状态。

气泡依赖两层开关：Android 的全局/选中会话气泡许可，以及 WeModern 的“Chat bubbles”开关。气泡准备就绪后，允许的会话使用低重要性安静消息通道；关闭或不可用的会话走提醒通道，因而仍可显示正常消息和 heads-up。

会话默认按私聊和群聊分别配置；每个已知会话还可覆盖为“始终允许”或“永不允许”。会话身份由微信通知提供的标题构成（`wechat:<title>`），昵称、群名或语言变化会使旧覆盖失配；这不是微信稳定内部 ID。

### Experimental: Bubble trampoline

Android 12（API 31）及以上可启用实验性 trampoline 模式。它不为每个会话创建 WeChat task，而是使用固定 ID、固定 long-lived shortcut 的独立 host 通知，只保留一个代表最新合资格会话的气泡。用户展开气泡时才把微信 Home 作为嵌入任务根启动。

host 与普通消息分离且不属于消息分组，因此普通替换通知仍可被同步移除，host 不会因原通知消失而被误删。新消息只更新这个 host；关闭 trampoline 或 Chat bubbles 时会移除 host 并恢复常规模式。host 使用独立的静默、最小化通道，避免产生额外状态栏图标、文字 heads-up、声音或振动。

为处理微信 task 生命周期，应用结合 activity 日志跟踪嵌入任务：仅在实际 bubble task 移除时清除 host，避免普通微信前台切换、通知移除或新会话更新错误关闭气泡。

## 3. 通话 Live Update

“改写通知”开启后，新的通话分类器把微信当前语音/视频来电序列建模为三阶段，并为整个会话只使用一个固定 WeModern 通知 ID。动态 channel ID 仅作上下文，不单独决定状态：

- `id=41` 的 `CATEGORY_CALL + fullScreenIntent + custom RemoteViews + 邀请文案`组合代表来电展示。命中后始终尝试发布 Android 标准 `Notification.CallStyle.forIncomingCall`，不以异步 activity 日志维护的微信前台状态作为发布门槛；替换通知同时设置转发微信目标的 `fullScreenIntent`，满足 API 37 对非 FGS/UIJ CallStyle 的发布要求。Android 14+ 设置页显示系统“来电持续弹出”特殊访问状态，未开启时可跳转到 `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`；服务每次发布 CallStyle 也记录 `canUseFullScreenIntent()` 结果。权限有效且通话 channel 保持 High 时，系统 heads-up 可持续到用户打开微信或通知被应用取消。WeModern 成功发布后以 2 秒短周期滚动 snooze 隐藏 Android 不允许 listener cancel 的微信 ongoing 来电项，真正观察到微信 activity resume 时再移除 CallStyle。联系人名称从邀请文案或关联状态标题取得，头像优先读取普通微信消息已保存的会话头像缓存。没有缓存时由系统标准布局显示 fallback。
- CallStyle 的 Answer 和 Decline action 使用两个身份独立的 WeModern activity proxy PendingIntent，但都转发微信原通知的同一个 `fullScreenIntent`（缺失时使用 `contentIntent`）。这是已接受的行为折衷：两个按钮都只打开微信全屏接听/挂断页，用户仍需在微信内完成真正操作；action 点击和微信 activity resume 只移除来电卡片，不代表接通。
- `id=40` 从来电开始就显示 `Voice call in use` 或 `Video call in progress`。flags 仅为 `0x02` 时被明确排除消息、会话历史和 bubble host 管线，并在 CallStyle 已可承接来电状态时尝试 listener cancel；该取消不会 snooze 复用 key，因此后续状态更新仍可进入监听器。进入微信接听页本身就会让 flags 变为 `0x62`（`ONGOING_EVENT | NO_CLEAR | FOREGROUND_SERVICE`），因此首个 `0x62` 只作为连接候选。候选存在期间，API 31+ 通过 `AudioManager.addOnModeChangedListener()` 监听 `MODE_IN_COMMUNICATION` / `MODE_IN_CALL`，API 26–30 每 250ms 读取一次 `getMode()`；只有改写开启、微信 `id=40` 状态仍被跟踪且尚未接通时，音频模式才允许立即确认接通。若音频模式不可用，至少 1 秒后的新 `0x62` 更新仍可确认；微信不再更新时保留 10 秒兜底，避免整通电话没有 Live Update。

接通后，WeModern 成功发布 Live Update 后对受系统保护的微信 `id=40/0x62` 使用 2 秒滚动 snooze；到期重现时清除 post dedupe 身份并再次隐藏。短周期仅在替换 Live Update 已发布后启用，不参与连接判定，也不会像已撤回的 30 分钟 snooze 一样长时间压住快速重拨复用的 key。微信状态通知移除或 app-cancel 日志结束 Live Update；短周期边界仍可能出现瞬时重现，等待真机回归确认。来电卡片另有两分钟防残留超时。

此前真机观察中，Live Update 能稳定显示，但可能在实际接通后约 5 秒才出现；日志证明延迟来自首个 `0x62` 之后的 10 秒软件兜底，而不是系统 promotion。设备 `dumpsys audio` 显示微信在实际接通附近切换到 `MODE_IN_COMMUNICATION`，一次语音样本中比旧兜底早约 6 秒，因此当前实现优先使用该事件；语音真机复测已确认 Live Update 紧随实际接通升级，原延迟问题解决。音频模式是设备全局状态，必须继续由活跃微信通知会话门控；视频、蓝牙路由、快速重拨和与其他电话并发的结果仍需真机回归。`id=40` 的结束回调继续是结束 Live Update 的权威信号，暂不单独用 `MODE_NORMAL` 结束通话。

当前可用的语音通话体验还包含设备侧配置：微信动态 `id=41` channel 与 `id=40` 所在的 `reminder_channel_id` 均保持开启，但设为 Silent + Minimize。前者可能被微信以新 ID 重建，需要重新设置；后者系统显示为“其他通知”，降级可能同时影响同 channel 的其他微信提醒。不能把这两个分类完全关闭，否则 WeModern 可能失去生成替换通知所需的源事件。完整优化清单和历史取舍见[语音通话当前优化基线](explorations/2026-07-18-wechat-call-notification-capture.md#2026-07-19-语音通话当前优化基线)。

旧版固定 `voip_notify_channel_new_id` 兼容路径仍保留，但也与新状态机共用同一个 WeModern 通话通知 ID，避免 `id=40` 与 `id=41` 分别生成替换通知。

关闭“改写通知”时不会运行上述分类、CallStyle 或 Live Update 路径，因此所有微信语音/视频通知保持原样显示；若“采集与日志”开启，则只产生采集记录。当前 Pixel 9 Pro / API 37 已完成语音可见结果验收，并确认视频原始序列与语音同构；视频 rewrite 可见结果、去电、拒接、无人接听、锁屏和快速重拨仍未完成完整真机回归。

## 4. 启动器和快捷方式

应用图标默认进入 WeModern 设置。完成核心通知授权后，用户可选择让图标直接打开微信。长按图标最多显示 4 项：3 个最近微信会话与始终位于末位的 Settings。限制为 4 是为了避免不同启动器的可见上限把 Settings 挤出菜单。

快捷方式会缓存原始微信 `contentIntent`，从而能够回到对应聊天。头像从通知图标生成并缓存；群聊发送者头像不会复用到其他发送者。trampoline host 的内部 shortcut 不得取代可见的 3 个会话和 Settings。

## 5. 设置与测试

`MainActivity` 是 Compose / Material 3 设置界面，显示通知使用权、发送通知权限、Android 14+ 来电持续弹出特殊访问、Live Update、免电池优化、气泡系统设置与 channel 设置、同步移除前置条件、独立的采集/改写 Debug 开关，以及消息/语音/视频测试。顶部运行状态把已授权后的模式明确分为“关闭”“仅调试”和“已就绪”，分别使用电源、调试和勾选图标及不同卡片色调；关闭态的电源图标使用高对比度深色中性底板，避免与中性卡片背景融为一体。正文同步显示“原样透传”“只采集”或“正在改写”。必要授权未完成时仍使用闪电图标和剩余步骤。Debug 和 Bubbles 区域标题不显示额外的说明或状态尾标，具体状态只由各自开关和正文表达。提供简体中文、繁体中文和默认资源；Android 动态色与深色模式可用。

设置页的 `LazyColumn` 以单独的 header、设置行和卡片作为 lazy item，并为静态内容提供稳定 key 与准确 content type；1.5 个 viewport 的前向 cache window 会提前准备后续项目，0.5 个 viewport 的后向窗口用于快速折返。Setup 与 Bubbles 不再各自作为包含多张卡片的超高单一 item 一次性组合和布局，从而把首次滚入区块时的 UI 线程工作分散到可见及预取项目。Pixel 9 Pro / API 37 的 Debug 构建在相同 10 次滚动脚本下，优化前一次基线为 533 帧、6 个 deadline miss、99 分位 34 ms；优化后多轮 99 分位为 15–21 ms，未再出现基线中的 300 ms 极端帧。调试构建和合成 ADB 手势仍有测量波动，不能把该数据视为发布构建的绝对帧率保证；完整调查见[设置页滚动性能检查](explorations/2026-07-18-settings-scroll-performance.md)。

Message 测试通知 ID 为 `100`，小图标必须是 `R.drawable.ic_wechat_notification_small`。该 PNG 是从 WeChat 8.0.69 的真实状态栏小图标提取，不能用启动器图标、头像或彩色 fallback 替换；否则 Android alpha-mask 渲染会显示方块。trampoline host 成功更新时，测试消息不会同时保留 ID `100`。

## 6. 已处理的关键问题与方案

| 问题 | 解决方案 |
| --- | --- |
| 重复 listener 回调会重复重写/堆积历史 | `NotificationPostDeduplicator` 按通知 key、post time 和 `when` 去重。 |
| Android 12+ bubble 启动缺少嵌入任务选项 | bubble Activity `PendingIntent` 使用 mutable flag，删除回调保持 immutable。 |
| 多个 trampoline 会话互相结束 WeChat task | 以固定 host 替代每会话 host，永远只维护一个最新会话 bubble。 |
| 原通知移除误杀 trampoline bubble | host 不加入消息分组、不参与 replacement 映射，并按嵌入 task 移除事件清理。 |
| 气泡关闭后普通通知仍静默 | 依据全局准备状态和会话策略切换 quiet/alerting message channel。 |
| 群聊头像误用于其他会话/发送者 | 快捷方式图标按会话缓存，避免复用群聊发送者头像。 |
| 某些取消事件没有 listener 回调 | 可选日志监视加持久化 replacement 映射补偿清理。 |

## 7. 验证基线

每次影响通知解析、启动、气泡、快捷方式、权限或 Live Update 的改动，至少运行：

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
```

相关设备验证须记录 Android 版本和实际行为。改动 Message 测试通知或其图标时，必须在设备发送 ID `100`，通过 `dumpsys notification` 确认 `ic_wechat_notification_small`，并目视确认状态栏是微信双气泡 glyph 而不是方块。

## 8. 非目标与当前限制

- WeModern 不拥有微信联系人内部 ID，不能稳定地把外部分享直接投递到指定微信联系人。
- 微信通知标题不是稳定会话标识；名称变更会影响 per-conversation 覆盖和对应快捷方式。
- Bubble trampoline 是实验能力，依赖 Android / 厂商 SystemUI 与微信 task 行为；必须进行真机回归。
- Android 8/9 保留重写通知，但不附加 bubble metadata。

有关 Direct Share 和已读状态的证据及结论见 [功能探索](explorations/README.md)。
