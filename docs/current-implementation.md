# WeModern 当前实现

- 当前版本：1.8.0（`versionCode` 35）
- 支持范围：`minSdk` 26；编译和目标 API 37（Android 17）
- 最近代码状态：2026-08-26

## 0. 微信通知采集调试模式

当前开发分支先撤回了所有 `v1.6.1` 之后缺少完整证据的语音/视频通话改写实验，随后根据 capture-only JSONL 的完整样本重新实现了窄范围状态机。Pixel 9 Pro / API 37 已确认：把微信 `id=41` 来电 channel 和 `id=40` 的 `reminder_channel_id` 都手动设为 Silent + Minimize 后，语音来电 CallStyle 表现符合预期，接通后的 promoted ongoing CallStyle 也正常显示；针对原有约 5 秒延迟，当前实现增加由活跃微信状态通知门控的音频模式接通确认，语音真机复测确认升级时机已紧随实际接通，体感延迟问题解决。本轮复测同时发现 ongoing 改造后的来电阶段 `shortService` 没有成功承载 CallStyle，现已把 API 37 来电与接通后的整段生命周期统一到真机已验证的 `specialUse`，后续语音来电复测获用户确认为正常。锁屏测试确认：全屏通知权限关闭时，系统会把来电呈现为带操作按钮的 CallStyle，而不会直接启动微信；当前实现据此删除该权限声明和设置入口，只保留 CallStyle 通知体验。最新完整视频 capture 在 ID、channel、flags 转换、RemoteViews、PendingIntent 和结束 reason 上均与语音同构，因此视频复用相同优化，仅切换视频文案、图标和 `CallStyle.setIsVideo(true)`；视频 rewrite 可见结果仍待下一通真机验收。设置界面提供两个互不依赖、运行时立即生效的 Debug 开关：总开关“采集与日志”控制微信原始通知采集及其 logcat / JSONL 输出，“改写通知”单独控制解析、隐藏、取消或 snooze、替换通知、Live Update、气泡 host 和同步移除日志 watcher。开发阶段默认开启采集、关闭改写。

本轮每次通话实验、真机异常、回滚内容、新样本证据和当前可用基线均保存在[微信语音/视频通话通知实验与采集记录](explorations/2026-07-18-wechat-call-notification-capture.md)。历史启发式方案仍属已撤回探索；当前开发代码只包含其中 2026-07-19 证据驱动的窄范围状态机，语音和视频共用该状态机，不引入独立视频启发式分支。

关闭改写时，通知监听器不会执行消息或通话解析，不取消或 snooze 微信通知，也不创建任何替换通知、Live Update 或 bubble host；同时取消残留的 WeModern 自有通知并清空临时 replacement 映射。每次事件入口和 `hideOriginal()` 都直接读取当前 rewrite preference，后者作为防御性保护，即使被误调用也不会移除微信原始通知，并避免开关异步通知期间使用过期缓存值。清理残留项使用 WeModern 自己的 `NotificationManager.cancelAll()`，只影响本应用发布的通知。采集开启时仍记录 `com.tencent.mm` 的 `ACTIVE_SCAN`、`POSTED` 和 `REMOVED` 事件；采集关闭时不再写入新记录或输出 `WeModern.Capture` 日志。改写开启后运行消息基线和本节记录的新通话状态机，采集可以独立保持开启或关闭，且原始事件总是在改写发生前记录。

每条记录包含 StatusBarNotification 标识、时间、flags、channel、category、标准 extras、图标描述、RemoteViews 包名与 layout ID、actions、PendingIntent 创建方、声音/振动字段及当前 ranking/channel 信息。记录同时输出到 logcat tag `WeModern.Capture`，并追加到应用私有文件 `files/wechat_notification_capture.jsonl`；文件达到 16 MiB 时轮换为 `wechat_notification_capture.previous.jsonl`。

## 1. 微信通知重写

“改写通知”开启时，`WeChatNotificationService` 作为 `NotificationListenerService` 监听 `com.tencent.mm`，识别消息和 VoIP 通知，并以 WeModern 通知替换原始通知。消息使用 `MessagingStyle`，保留会话标题、发送者头像、最多 8 条去重历史、会话 shortcut 与原始 `contentIntent`；普通消息归入 `wechat.rewritten` 分组并维护摘要通知。关闭改写会完全绕过本节行为。

应用只创建有实际发布者的消息、Bubble 消息、来电、通话中 channel。旧 `status_alerts` 原本仅服务于已移除的通用测试通知，当前没有任何生产或测试通知引用；升级初始化会删除它，不再让无效“状态”分类留在 Android 通知设置中。历史固定 trampoline host 使用的 `wechat_bubble_host_visual_alerts` 也不再有发布者并继续删除；新的可选共享 host 复用统一消息 channel、主固定 ID 与 shortcut，更新或清理时会取消两个不再使用的 legacy / secondary ID。

原始微信通知只会在 Android 已接受对应的 WeModern 消息替换后被隐藏，避免并存重复内容。如果会话状态、shortcut、头像、BubbleMetadata 或通知发布过程中出现运行时错误，服务会撤销该条 replacement 映射并保留微信原通知；原始图标发布失败时会先改用应用内安全图标重试，再回退为不带 BubbleMetadata 的普通通知。消息分组摘要属于主替换之后的 best-effort 更新，其失败不会使已经发布的消息替换失效。通知服务重连时会扫描活动通知、恢复会话入口、刷新快捷方式图标并清理孤儿替换通知。不能解析的微信通知不会被改写。

点击 WeModern 普通消息通知时，`WeChatLaunchProxyActivity` 从通知创建时写入的 `message:<conversationId>` 恢复精确会话。只有原微信 PendingIntent 成功发送后，proxy 才通过与 Bubble Bridge 共用的会话已打开入口，清空该会话的服务内 `MessagingStyle` 历史和本地 Bubble 消息快照。这一边界由成功的点击转发主动触发，即使通知 `AUTO_CANCEL` 已先删除 replacement 映射，也不再依赖微信后续 APP_CANCEL 的回调时序；目标 token 已失效、只能回退到微信 Home 时不会错误标记该会话已打开。

### 同步移除

系统正常回调会删除相应替换通知、历史与气泡状态。对于 Android 未可靠暴露的微信应用取消场景，`NotificationCancelLogWatcher` 可在授予 `READ_LOGS`、开启 `NotificationService` DEBUG 日志并重启后，从日志中定位原始通知并清理替换项。替换映射会持久化，服务重启后仍可恢复清理。当 trampoline 的活动 Bubble host 需要继续保留时，同步移除仍会单独清空该会话的 `MessagingStyle` 历史和 Bubble 消息快照，使下一条新消息从新的未读批次开始，而不删除 host、shortcut 或最新会话 token。Bubble 刚打开后的 5 秒保护窗口会携带精确 conversationId：被点击会话可清历史，微信同批撤销的其他会话仍保留未读历史。

这是一项可选的高级可靠性能力：没有 ADB 条件时，重写和通常的移除回调仍可用；不能承诺覆盖所有厂商系统日志行为。

## 2. 会话通知与气泡

### 常规会话气泡

Android 10（API 29）及以上可为每个允许的会话附加 `BubbleMetadata`。气泡内容是 `BubbleConversationActivity`：显示本地最近消息快照，并通过微信原始 `PendingIntent` 打开精确聊天。气泡 activity 可嵌入、可调整大小、以 document 模式多实例启动，并可在进程重建后恢复状态。当 activity 事件跟踪确认全屏微信位于前台时，新消息仍按普通通知改写，但不附加 `BubbleMetadata`；常规会话气泡和 trampoline 都遵循这条规则。Bubble task 内嵌入的微信仍被视为 Bubble 会话，不误判为全屏微信。该前台判定复用同步移除的 activity log watcher，因此依赖 `READ_LOGS` 与相关 ADB 初始化；未完成该初始化时，Android 没有向普通应用提供等价的跨应用顶层 Activity 信号。

气泡依赖两层开关：Android 的全局/选中会话气泡许可，以及 WeModern 的“Chat bubbles”开关。所有替换消息统一使用 `wechat_messages_alerts` 高重要性 channel，不再根据 Bubble 状态切换 parent channel。Pixel 9 Pro / API 37 的对照实验确认：通知实际为 importance 4、带 BubbleMetadata 且被系统标记为 `FLAG_BUBBLE` 时，SystemUI 只显示 Bubble flyout，通知 row 的 `isHeadsUpState=false`、`isPinned=false`，不会再显示普通顶部 heads-up；channel 的系统声音和振动仍然生效。未成为 Bubble 的消息继续按同一 channel 正常提醒。

升级时先把仍在历史 quiet channel 上的活动会话通知和消息摘要重发到 `wechat_messages_alerts`，再删除 `wechat_messages_bubbled_quiet`、`wechat_messages_bubbles_quiet` 与 `wechat_messages_bubbles_quiet_v2`。这样只保留一个消息分类，并尽量避免升级时丢失现有 Bubble。

会话默认按私聊和群聊分别配置；每个已知会话还可覆盖为“始终允许”或“永不允许”。会话身份由微信通知提供的标题构成（`wechat:<title>`），昵称、群名或语言变化会使旧覆盖失配；这不是微信稳定内部 ID。

### Bubble trampoline

Android 12（API 31）及以上可启用 trampoline，并通过“会话级气泡”开关选择两种正式模式。该偏好默认开启，以保持现有“一会话一 Bubble”行为；切换模式会立即重建活动通知的 BubbleMetadata，并清理另一模式不再使用的固定 host 或逐会话 task。

会话级模式下，每条合资格的会话替换通知直接承载自己的 BubbleMetadata。稳定的 per-conversation 通知 ID、shortcut、LocusId、Bridge URI 与 PendingIntent identity 共同把不同会话拆成独立 document task；同一会话的新消息只更新原 Bubble，不为每条消息新增 Bubble。该模式能直接打开精确聊天，但微信独立 `ChattingMainUI` 在当前 Pixel / 微信版本中可能不响应 Bubble 可见区域的 IME resize，导致键盘遮挡输入区域。

Bubble 的 mutable Activity PendingIntent 先启动 non-exported、可嵌入的 `TrampolineBridgeActivity`，Bridge 验证目标是 `com.tencent.mm` 创建的 Activity token 后，再以 result 方式转发该会话最新通知的 immutable `contentIntent`。目标提交成功时，Bridge 同步把该会话标记为已打开：只清空服务内的 `MessagingStyle` 历史和本地 Bubble 消息快照，继续保留 Bubble host、shortcut、最新微信 `PendingIntent` 与 task；因此下一条新消息从新的未读批次开始，不会重新带上已经打开过的消息，也不会误清其他会话。Bridge 常驻为 Bubble task 根。微信对话结束时如果直接露出 Bridge，生命周期兜底会把 task 移到后台；如果微信先启动 `LauncherUI` 再结束 `ChattingMainUI`，result 回调会在同一个 AppTask 中以 `CLEAR_TOP | SINGLE_TOP` 恢复 Bridge、清除其上的微信 Home，然后再把 task 移到后台。两条路径都让第一次 Back 收起而不是删除 Bubble，再次展开时重新转发会话目标。由于 WeModern 不能修改微信 immutable token 的内部 task flags，其他 Android 或微信版本仍可能复用全屏 task；目标缺失、类型无效或 token 已取消时，该条真实微信通知不会附加 trampoline Bubble。

Bridge session 以 task ID 和会话 ID 并行跟踪，移除一个 task 不会清理其他会话。微信进入聊天后会批量撤销多个源通知；嵌入 session 期间，所有仍带 BubbleMetadata、匹配 shortcut 且被系统标记为 `FLAG_BUBBLE` 的活动会话替换都会继续作为 host 保留，避免展开后一个 Bubble 时关闭前一个。源映射消费后，SystemUI 可能再次回送同一个 host 的 Bubble 状态更新；只要内存会话状态、稳定通知 ID、shortcut、metadata 与 `FLAG_BUBBLE` 仍完整，孤儿清理也会保留它。进程重建后缺少会话状态的真正 orphan、未成为 Bubble 的普通通知、用户显式拖走 Bubble、打开全屏微信或关闭功能仍会清理对应范围。

关闭“会话级气泡”后进入共享微信 Home 模式。合资格消息仍各自保留 replacement、shortcut、历史和 sync-removal 映射，但逐会话通知不再携带 BubbleMetadata，并通过 `GROUP_ALERT_SUMMARY` 禁止重复提醒。最新合资格消息会更新一个固定通知 ID、固定 long-lived shortcut 和固定 mutable 微信 Home PendingIntent 的共享 host；只有该 host 脱离消息分组、携带 BubbleMetadata 并负责 flyout、声音和振动。展开时只在 Bubble task 中启动微信 `LauncherUI`，不会调用通知中的精确会话 token，因此需要用户在微信内自行进入对话，但 `LauncherUI` 内部导航对 IME resize 的适配更好，不会触发已知的独立 `ChattingMainUI` 输入栏遮挡路径。activity log watcher 使用 action 为空的微信 Home create 事件识别共享 Bubble task，使其不被误判为全屏微信；task 移除、关闭 trampoline/聊天气泡/改写，或通过 WeModern 入口进入全屏微信时会清理固定 host 和 shortcut。

Pixel 9 Pro / API 37 已完成双会话真机验收：不同会话 Bubble 可同时保留、分别进入对应聊天，直接 Back 收起当前 Bubble；后续消息更新后重新展开会进入该会话的新目标。另一次明确走 `ChattingMainUI → LauncherUI` 的返回测试确认，result + AppTask clear-top 路径会在第一次 Back 直接收起，不再先显示微信 Home。此前“一开后一个就关前一个”的问题已通过扩大 APP_CANCEL 期间的活动 Bubble host 保护范围修复。证据与平台风险见 [Trampoline PendingIntent bridge](explorations/2026-07-20-trampoline-pending-intent-bridge.md)和[多会话 Trampoline Bubble](explorations/2026-07-21-multi-conversation-trampoline-bubbles.md)。

两种 trampoline 与常规模式共享唯一消息 channel `wechat_messages_alerts`。会话级模式依赖 Pixel SystemUI 对“已实际启动的 Bubble”抑制普通 heads-up；共享模式则由固定 host 承担 alert，逐会话 child 使用公开的分组 alert 行为静默。声音、振动和用户对该 channel 的设置仍统一适用。旧 quiet 消息 channel 和固定 host 专用 channel 继续作为升级清理项；共享模式复用统一消息 channel，并只使用主固定 host ID，历史 legacy / secondary ID 会在更新和清理时取消。

Message 测试始终发布固定通知 ID `100` 并使用 `ic_wechat_notification_small`。会话级 trampoline 开启时测试通知仍使用本地测试 Bubble，因为它没有微信创建的精确会话 PendingIntent；共享模式下 ID `100` 作为静默 child 保留，同时更新打开微信 Home 的固定 host。

## 3. 通话 CallStyle Live Update

“改写通知”开启后，新的通话分类器把微信当前语音/视频来电序列建模为三阶段，并为整个会话只使用一个固定 WeModern 通知 ID。动态 channel ID 仅作上下文，不单独决定状态：

WeModern 为两种用户可理解的通话状态使用独立且稳定的系统 channel：`wechat_calls_incoming` 在设置中显示为“微信来电提醒”，使用高重要性、系统默认来电铃声、`USAGE_NOTIFICATION_RINGTONE` 音频属性和系统默认振动；incoming CallStyle 同时设置 `FLAG_INSISTENT`，使铃声和振动持续到用户处理来电、微信撤回来电源、保护超时，或通知被接通状态替换。`wechat_calls_ongoing` 显示为“微信通话中”，使用默认重要性并明确关闭声音与振动。接通时不取消或另发一条通知，而是用相同 tag / ID 把 incoming CallStyle 原地更新到 ongoing channel；新通知不携带 `FLAG_INSISTENT`，`setOnlyAlertOnce(true)` 防止状态更新再次提醒，静音 channel 同时使系统停止来电阶段的持续反馈。两个 channel 分离后，用户可在 Android 设置中独立调整来电和通话中行为；静音模式、勿扰模式以及用户对 channel 的修改仍由系统决定。

Pixel 9 Pro / API 37 已执行卸载后的全新安装迁移。`dumpsys notification` 确认来电 channel 为 importance 4、声音 `content://settings/system/ringtone`、`USAGE_NOTIFICATION_RINGTONE`、振动开启；通话中 channel 为 importance 3、声音为空、振动关闭，旧 `wechat_calls_live_quiet` 不再存在。迁移前的 Debug、rewrite、气泡、trampoline、会话规则、快捷方式和头像缓存已恢复；旧 replacement 映射和运行状态没有恢复。后续真实语音来电已确认 CallStyle 的持续提醒效果符合预期；视频来电以及更多设备仍需回归。

- `id=41` 的 `CATEGORY_CALL + fullScreenIntent + custom RemoteViews + 邀请文案`组合代表来电展示。命中后始终尝试发布 Android 标准 `Notification.CallStyle.forIncomingCall`，不以异步 activity 日志维护的微信前台状态作为发布门槛。API 37 会拒绝既非 FGS/UIJ、又没有 `fullScreenIntent` 字段的 CallStyle；为避免申请会启动锁屏 Activity 的全屏权限，该版本由现有通知监听服务以声明了具体用途的 `specialUse` foreground type 承载来电与接通后的整段 CallStyle 生命周期，通知对象本身不带 FSI。来电源消失、用户点击或应用自己的两分钟保护超时时退出前台状态；真正接通时用同一通知 ID 更新成 promoted ongoing CallStyle，不再切换 foreground type。API 26–36 不需要该 foreground 兼容路径，继续发布普通通知。整卡、Answer 和 Decline 均通过身份独立的 WeModern proxy 在用户主动点击后打开微信。WeModern 成功发布后以 2 秒短周期滚动 snooze 隐藏 Android 不允许 listener cancel 的微信 ongoing 来电项，真正观察到微信 activity resume 时再移除来电 CallStyle。联系人名称从邀请文案或关联状态标题取得，头像优先读取普通微信消息已保存的会话头像缓存。没有缓存时由系统标准布局显示 fallback。
- 来电 CallStyle 的 Answer 和 Decline action 使用两个身份独立的 WeModern activity proxy PendingIntent，但都转发微信原通知的同一个 `fullScreenIntent`（缺失时使用 `contentIntent`）。接通后的 `CallStyle.forOngoingCall` 使用另一个 Hang up proxy；整卡和 Hang up 都只打开微信通话页，不会直接接听、拒绝或挂断。用户仍需在微信内完成真正操作，这是现阶段无法取得微信内部控制 Intent 时明确接受的行为折衷；点击 action 或微信 activity resume 本身也不改变接通判定。
- `id=40` 从来电开始就显示 `Voice call in use` 或 `Video call in progress`。flags 仅为 `0x02` 时被明确排除消息、会话历史和 bubble host 管线；Pixel 9 Pro / API 37 的真实来电 capture 证明 Android 会忽略 notification listener 对这个 ongoing 通知的 cancel，因此在 WeModern CallStyle 已成功建立后改用 1 秒可续短 snooze。若替换没有成功发布则保持微信原通知，避免分类或平台失败造成无提醒。由于 Android 可能忽略复用已 snooze key 的更新，用户点击来电 CallStyle proxy 时会通过公开的 `snoozeNotification()` 把现有租期缩到 1ms，并留下 2.5 秒切换窗口；直接观察到微信 Activity resume 时也执行相同保护，使接听页触发的 `0x62` 能进入监听器。进入微信接听页本身就会让 flags 变为 `0x62`（`ONGOING_EVENT | NO_CLEAR | FOREGROUND_SERVICE`），因此首个 `0x62` 只作为连接候选；候选被记录并启动音频模式检测后立即进入 2 秒短压制，同一突发组的重复更新也继续压制，不再在确认等待期间留下原通知。候选存在期间，API 31+ 通过 `AudioManager.addOnModeChangedListener()` 监听 `MODE_IN_COMMUNICATION` / `MODE_IN_CALL`，API 26–30 每 250ms 读取一次 `getMode()`；只有改写开启、微信 `id=40` 状态仍被跟踪且尚未接通时，音频模式才允许立即确认接通。若音频模式不可用，至少 1 秒后的新 `0x62` 更新仍可确认；微信不再更新时保留 10 秒兜底，避免整通电话没有 Live Update。

接通后，WeModern 使用 `Notification.CallStyle.forOngoingCall`、联系人 `Person`、头像、正向 chronometer 和 promoted ongoing 请求构造通话中 Live Update；API 31 以下回退为带 Hang up 文案的标准 action，Android 16 以下则按系统能力显示为普通 ongoing CallStyle。对受系统保护的微信 `id=40/0x62` 使用 2 秒滚动 snooze；首次 `0x62` 已先登记为连接候选，再执行隐藏，因此不会丢失启动音频模式检测所需的信号。到期重现时清除 post dedupe 身份并再次隐藏。微信状态通知移除或 app-cancel 日志结束 CallStyle Live Update 并退出 `specialUse` FGS；短周期边界仍可能出现瞬时重现，等待真机回归确认。来电卡片另有两分钟防残留超时。

此前 `ProgressStyle` 真机观察中，Live Update 能稳定显示，但可能在实际接通后约 5 秒才出现；日志证明延迟来自首个 `0x62` 之后的 10 秒软件兜底，而不是系统 promotion。设备 `dumpsys audio` 显示微信在实际接通附近切换到 `MODE_IN_COMMUNICATION`，一次语音样本中比旧兜底早约 6 秒，因此当前实现优先使用该事件；语音真机复测已确认升级时机紧随实际接通，原延迟问题解决。本次 ongoing CallStyle + promotion 已由同一 Pixel 9 Pro / API 37 真机确认正常，系统归档同时记录 `FOREGROUND_SERVICE | PROMOTED_ONGOING`；音频模式仍是设备全局状态，必须继续由活跃微信通知会话门控，视频、蓝牙路由、快速重拨和与其他电话并发结果仍需真机回归。`id=40` 的结束回调继续是结束 CallStyle Live Update 和 foreground 状态的权威信号，暂不单独用 `MODE_NORMAL` 结束通话。

当前可用的语音通话体验还包含设备侧配置：微信动态 `id=41` channel 与 `id=40` 所在的 `reminder_channel_id` 均保持开启，但设为 Silent + Minimize。前者可能被微信以新 ID 重建，需要重新设置；后者系统显示为“其他通知”，降级可能同时影响同 channel 的其他微信提醒。不能把这两个分类完全关闭，否则 WeModern 可能失去生成替换通知所需的源事件。完整优化清单和历史取舍见[语音通话当前优化基线](explorations/2026-07-18-wechat-call-notification-capture.md#2026-07-19-语音通话当前优化基线)。

旧版固定 `voip_notify_channel_new_id` 兼容识别仍保留，但通知展示也已迁移到同一个 ongoing CallStyle 构造器：Content 与 Hang up 使用身份不同、目标相同的微信 proxy，API 37 同样由 `specialUse` FGS 承载。它与新状态机共用一个 WeModern 通话通知 ID；只有替换发布成功后才隐藏原通知。项目已不再包含 `Notification.ProgressStyle` 生产代码或样式。

关闭“改写通知”时不会运行上述分类、CallStyle 或 Live Update 路径，因此所有微信语音/视频通知保持原样显示；若“采集与日志”开启，则只产生采集记录。Pixel 9 Pro / API 37 已完成统一 `specialUse` 后的语音来电、接通后 ongoing CallStyle + promotion 和实时接通时机验收；视频 rewrite 可见结果、去电、拒接、无人接听、锁屏和快速重拨仍需完成真机回归。

## 4. 启动器和快捷方式

应用图标默认进入 WeModern 设置。完成核心通知授权后，用户可选择让图标直接打开微信。长按图标最多显示 4 项：3 个最近微信会话与始终位于末位的 Settings。限制为 4 是为了避免不同启动器的可见上限把 Settings 挤出菜单。

快捷方式会缓存原始微信 `contentIntent`，从而能够回到对应聊天。头像从通知图标生成并缓存；群聊发送者头像不会复用到其他发送者。常规与会话级 Bubble 使用现有会话 shortcut；共享 Home 模式额外维护一个固定 long-lived host shortcut，并在离开该模式或清理 host 时移除。

## 5. 设置与测试

`MainActivity` 是 Compose / Material 3 设置界面，显示通知使用权、发送通知权限、Live Update、免电池优化、气泡系统设置与 channel 设置、同步移除前置条件、独立的采集/改写 Debug 开关，以及消息/语音/视频测试。应用不申请全屏通知特殊访问，也不提供相关设置入口。顶部运行状态把已授权后的模式明确分为“关闭”“仅调试”和“已就绪”，分别使用电源、调试和勾选图标及不同卡片色调；关闭态的电源图标使用高对比度深色中性底板，避免与中性卡片背景融为一体。正文同步显示“原样透传”“只采集”或“正在改写”。必要授权未完成时仍使用闪电图标和剩余步骤。Debug、Bubbles 与 Tests 区域标题不显示额外的说明或状态尾标，具体状态只由各自开关和正文表达。Android 气泡设置与 Chat bubbles 开关使用 Material Symbol `bubble`。提供简体中文、繁体中文和默认资源；Android 动态色与深色模式可用。

Voice 和 Video 测试使用通知 ID `101` 与独立的 `CallTestService`。点击测试按钮先通过“微信来电提醒”channel 发布持续响铃和振动、但不计时的 incoming CallStyle；Answer 在同一 ID 上更新为和真实接通通知共用构造器及静音“微信通话中”channel 的 ongoing CallStyle，从该时刻启动 chronometer 并请求 promotion，同时停止持续提醒；Decline 和 Hang up 都直接停止测试 FGS 并移除通知。API 34+ 使用声明具体测试用途的 `specialUse` foreground type。两个通话测试与 Message 测试共用 mock 联系人名称 `Mia`（`test_message_sender`）和头像 `ic_test_message_avatar_48`，名称与头像在 incoming、ongoing 两阶段都写入 `Person` 和标准 large icon；语音/视频只改变正文、通话图标和 `setIsVideo`。状态机与操作语义完全一致；每次启动另带独立 session token，旧卡片延迟到达的 action 会被忽略，不能关闭刚启动的新测试。Android 12 以下没有平台 CallStyle 时回退为同样两阶段的标准 action 通知。

Pixel 9 Pro / API 37 已分别完成 Voice 与 Video 的 incoming → Answer → ongoing → Hang up 真机检查：incoming 记录包含两个系统 CallStyle action 且没有 chronometer；ongoing 使用同一 ID，带 `FOREGROUND_SERVICE | PROMOTED_ONGOING` 和一个 Hang up action；Hang up 后活动通知 ID `101` 与 `CallTestService` 均不存在。

设置页的 `LazyColumn` 以单独的 header、设置行和卡片作为 lazy item，并为静态内容提供稳定 key 与准确 content type；1.5 个 viewport 的前向 cache window 会提前准备后续项目，0.5 个 viewport 的后向窗口用于快速折返。Setup 与 Bubbles 不再各自作为包含多张卡片的超高单一 item 一次性组合和布局，从而把首次滚入区块时的 UI 线程工作分散到可见及预取项目。Chat bubbles 的依赖项继续保持独立 lazy item，并通过 Compose 原生 `AnimatedVisibility` 以无回弹的短展开/淡入过渡显示或隐藏；系统动画时长关闭时这些动画也随之关闭。单会话设置按稳定会话 key 拆分 lazy item，排序切换使用原生 `animateItem` 位置动画。Pixel 9 Pro / API 37 的 Debug 构建在相同 10 次滚动脚本下，优化前一次基线为 533 帧、6 个 deadline miss、99 分位 34 ms；优化后多轮 99 分位为 15–21 ms，未再出现基线中的 300 ms 极端帧。调试构建和合成 ADB 手势仍有测量波动，不能把该数据视为发布构建的绝对帧率保证；完整调查见[设置页滚动性能检查](explorations/2026-07-18-settings-scroll-performance.md)。

Message 测试通知 ID 始终为 `100`，小图标必须是 `R.drawable.ic_wechat_notification_small`。该 PNG 是从 WeChat 8.0.69 的真实状态栏小图标提取，不能用启动器图标、头像或彩色 fallback 替换；否则 Android alpha-mask 渲染会显示方块。会话级 trampoline 下测试消息使用本地测试 Bubble，不尝试桥接非微信创建的 PendingIntent；共享模式仍发布 ID `100`，并另行更新只打开微信 Home 的固定 host。

## 6. 已处理的关键问题与方案

| 问题 | 解决方案 |
| --- | --- |
| 重复 listener 回调会重复重写/堆积历史 | `NotificationPostDeduplicator` 按通知 key、post time 和 `when` 去重。 |
| Android 12+ bubble 启动缺少嵌入任务选项 | bubble Activity `PendingIntent` 使用 mutable flag，删除回调保持 immutable。 |
| 多个 trampoline 会话互相结束 WeChat task | 每个会话使用独立通知 host、document Bridge task 与按 task / 会话隔离的 session 状态。 |
| 微信批量撤销源通知误杀其他 trampoline Bubble | 嵌入期间保留所有 ID、shortcut、BubbleMetadata 和 `FLAG_BUBBLE` 都匹配的活动 host；普通通知继续同步移除。 |
| SystemUI 的 Bubble 状态更新被当成无映射 orphan | 孤儿清理额外验证内存会话状态、稳定 ID、shortcut、metadata 与 `FLAG_BUBBLE`，保留仍存活的 trampoline host。 |
| Bubble 未获准时低重要性消息无提醒 | 所有消息统一使用 alerting channel；真正 Bubble 由已验证的 SystemUI flyout 取代普通 HUN。 |
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
- Bubble trampoline 依赖 Android / 厂商 SystemUI 与微信 task 行为；必须进行真机回归。
- Android 8/9 保留重写通知，但不附加 bubble metadata。

有关 Direct Share 和已读状态的证据及结论见 [功能探索](explorations/README.md)。
