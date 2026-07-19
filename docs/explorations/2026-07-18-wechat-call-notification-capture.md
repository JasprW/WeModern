# 微信语音/视频通话通知实验与采集记录

- 日期：2026-07-18；最近更新：2026-07-19
- 状态：历史实验已撤回；2026-07-19 语音通话和音频模式快速接通确认已完成真机验收，视频复用相同优化，待 rewrite 真机验收
- 发布基线：`v1.6.1`（commit/tag `d02341a`）
- 验证设备：Pixel 9 Pro，Android API 37
- 目的：完整保留本轮尝试、失败现象和结论，避免后续在缺少证据时重复叠加规则

## 结论

本轮没有得到能够可靠区分“来电待接听、进入接听/挂断页、真正接通、通话中、已结束”的稳定分类器。根据不完整实时日志增加 channel、notification ID、activity、flags、文案和头像等启发式规则后，先后出现重复通知、通知丢失、错误 promotion、错误计时和残留通知。

因此，所有早期基于不完整证据的 `v1.6.1` 之后语音/视频通话特殊逻辑都已删除，不能把下述历史实验当作已交付功能。随后取得的完整 JSONL 样本支持了本页 2026-07-19 的窄范围状态机；该方案已进入开发构建，并在 Pixel 9 Pro / API 37 上完成语音通话主路径验证。当前可用结果同时依赖微信源 channel 的手动降级，不能描述为完全由 WeModern 单独消除所有源通知。

## 2026-07-19 语音通话当前优化基线

这是当前用户认可的 voice call 配置。后续采集到的视频通话源序列已经证明可以复用相同状态机；本节仍保留为语音验收基线，视频证据和适用结论见下一节。

| 层级 | 优化手段 | 当前作用与限制 |
| --- | --- | --- |
| 微信 `id=41` 系统 channel | 将 `Voice and video call invitations` 设为 **Silent + Minimize**，但保持分类开启。 | 压低微信原生来电卡片的声音、弹出和通知栏显著程度，同时继续让通知进入 WeModern 监听链路。该 channel ID 会随微信动态创建，出现新 ID 时可能要重新设置。 |
| 微信 `id=40` 系统 channel | 将 `reminder_channel_id`（系统显示为“其他通知”）设为 **Silent + Minimize**，但保持分类开启。 | 压低 `Voice call in use` 常驻源通知。此 channel 不是通话专属名称，降级可能同时影响微信放在“其他通知”中的其他提醒，这是已接受的系统设置取舍。 |
| 来电识别 | 以 `id=41` 的 `CATEGORY_CALL + fullScreenIntent + custom RemoteViews + 邀请文案`组合识别来电，不把动态 channel ID 当作唯一条件。 | 避免微信重建 channel 后识别失效；未知或不完整组合不改写。 |
| 来电替换 | 使用 Android 标准 `Notification.CallStyle.forIncomingCall` 和 `Person`，联系人名称来自邀请文案或标题，头像优先复用已缓存的微信会话头像。 | 不使用自定义通知布局；首次出现且没有头像缓存的联系人显示系统 fallback。 |
| API 37 发布条件 | 现有通知监听服务临时用 `shortService` foreground type 发布不含 FSI 的 CallStyle；WeModern 只声明基础 `FOREGROUND_SERVICE`，不声明 `USE_FULL_SCREEN_INTENT`。 | 满足 API 37 的 FGS/FSI/UIJ 三选一校验，同时锁屏不会自动启动 Activity；两分钟保护超时早于系统约三分钟限制，接通前主动退出 FGS。API 26–36 不走该兼容路径。 |
| CallStyle 操作 | Content、Answer、Decline 使用身份不同的 WeModern proxy PendingIntent，但最终都打开微信原通话页面。 | Answer/Decline 不能直接控制微信接听或拒绝；两个按钮都打开微信页面是明确接受的 tradeoff。 |
| 来电计时 | CallStyle 阶段不启动 chronometer，也不请求 Live Update promotion。 | 来电待接听时间不会误计入通话时长。 |
| `id=41` 隐藏 | CallStyle 成功发布后，对微信 ongoing `id=41` 使用 2 秒滚动 snooze；每次到期重现前清除 post dedupe 身份并再次处理。 | 普通 listener cancel 已证实不可靠；短周期将快速重拨的最坏抑制窗口限制在约 2 秒。系统 channel 的 Silent + Minimize 是额外保险。 |
| 进入微信页面 | 微信 Activity resume 只移除 WeModern 来电 CallStyle。 | 进入接听/挂断页不等于接通，不能在这里创建 Live Update 或开始计时。 |
| 接通判定 | `id=40/0x02` 是待接听状态；首次 `0x62` 只登记候选。候选期间由活跃微信状态门控 `AudioManager`，出现 `MODE_IN_COMMUNICATION` / `MODE_IN_CALL` 时立即确认；至少 1 秒后的新 `0x62` 更新与 10 秒超时继续兜底。 | 避免刚进入微信 VoIP 页面便误判接通，同时绕开旧兜底造成的约 5 秒体感延迟。音频模式是全局状态，不能脱离已跟踪微信会话独立使用。 |
| Live Update | 接通后用同一个固定 WeModern 通知 ID 更新为标准 `ProgressStyle`，正文改为通话进行中，从确认时刻启动 chronometer，并请求 promotion。 | 来电与通话中不会生成两个 WeModern 通话身份；邀请文案不会沿用到通话中。 |
| `id=40` 隐藏 | 待接听 `0x02` 尝试 listener cancel；Live Update 成功后，对受保护的 `0x62` 前台服务通知使用 2 秒滚动 snooze。 | 不在 Live Update 建立前长期 snooze 状态源；不再使用曾导致快速重拨丢事件的 30 分钟 snooze。系统 channel 的 Silent + Minimize 负责进一步压低仍可能短暂出现的源项。 |
| 隐藏安全性 | listener cancel 的自隐藏标记 1 秒后自动失效；滚动 snooze 只在已连接替换存在时运行。 | 防止系统忽略 cancel 后，过期标记吞掉同一 key 的真实 `APP_CANCEL`；关闭 rewrite 时所有 cancel/snooze 均被运行时保护阻止。 |
| 通话结束 | 微信 `id=40` 的 reason 8 `APP_CANCEL` 结束 Live Update；可选 notification-cancel 日志 watcher 补偿 snooze 窗口中的结束事件。 | 来电 CallStyle 另有 2 分钟防残留超时；快速重拨继续复用单一受控通知 ID。 |
| 管线隔离 | `id=40`、`id=41` 和 WeModern 通话替换都明确排除消息历史、消息分组、会话 shortcut 与 bubble host。 | 防止此前出现的 2 条微信 + 3 条 WeModern（含 bubble host）通知膨胀。 |
| 调试能力 | “采集与日志”独立记录微信原始事件；“改写通知”单独控制分类、隐藏和替换。 | 可以 capture/logging 而不 rewrite，原微信通知不会因调试采集被取消；后续视频优化继续沿用此采样方式。 |

当前验收结论：在上述两个微信 channel 均设为 Silent + Minimize 后，语音来电 CallStyle 表现符合预期，接通后的 Live Update 能显示。约 5 秒 promotion 延迟的代码原因已定位并加入音频模式快速确认；后续语音真机复测确认 Live Update 紧随接通升级，用户确认延迟问题已完全解决。锁屏测试确认禁用全屏通知特殊访问后，Pixel 9 Pro / API 37 会显示带操作按钮的 CallStyle 而不自动打开微信，因此最终产品只保留该通知形态。系统 channel 降级属于设备侧配置，重装 WeModern 不会替用户自动完成，也不能关闭对应分类，否则可能切断 WeModern 的识别输入。

## 2026-07-19 持续 CallStyle 与实时接通优化

- 本轮曾把 Android 14+ full-screen intent 特殊访问作为持续 heads-up 的前置条件，并加入 `canUseFullScreenIntent()` 状态、授权入口和发布日志；后续锁屏验证证明权限开启会直接启动微信 Activity，不符合最终产品目标。这些 UI、状态和权限声明现已删除，仅保留本条作为已撤回尝试记录。
- 已捕获语音序列的首个 `id=40/0x62` 在进入微信页面时出现，随后同一突发组的两个更新只晚约 110ms，均被 1 秒保护窗口过滤；源通知没有新的接听更新时，Live Update 在约 10.3 秒后才建立，证明延迟来自 `CONNECTED_STATUS_FALLBACK_DELAY_MS=10_000`，不是 Android promotion 本身。
- 同一设备 `dumpsys audio` 历史记录显示微信进程在真实接通附近调用 `setMode(MODE_IN_COMMUNICATION)`。语音样本中该事件约为 11:16:06.655，旧兜底约在 11:16:12.8 执行，可提前约 6 秒；视频样本中 `id=40/0x62` 约在 11:22:45.5 出现，音频模式于 11:22:51.831 切换并在 11:23:05.052 恢复 Normal，与真实连接区间一致。音频路由本身会在响铃时提前切换，不能替代模式信号。
- 当前实现只在首个 `0x62` 候选存在时启动检测：API 31+ 注册 `OnModeChangedListener`，API 26–30 每 250ms 轮询 `getMode()`；确认前再次检查 rewrite 已开启、微信状态源仍被跟踪、候选仍有效且尚未连接。成功 promotion、候选撤销、会话结束、关闭 rewrite 或服务清理都会停止监听/轮询。
- 后续通知更新和 10 秒 fallback 保留，作为微信未切换音频模式、系统回调不可用或设备行为差异时的兼容路径。暂不使用 `MODE_NORMAL` 单独结束 Live Update，避免音频路由短暂重配造成提前结束。

## 2026-07-19 锁屏来电样本与展示取舍

- 两通锁屏语音邀请分别在 `capturedAt=1784434216836` 和 `1784434228850` 捕获到微信 `id=41/0x96`；两者都有 `CATEGORY_CALL`、custom RemoteViews、邀请文案和微信创建的 immutable `fullScreenIntent`。`id=40/0x02` 也分别在约 40–54ms 后出现，说明锁屏 pattern 与亮屏一致，不存在“锁屏不发布 id=41”的新分支。
- 两通都在约 7 秒后由微信以 reason 8 移除 `id=41` 和 `id=40`，期间没有 `VideoActivity` 创建或 resume。样本采集时偏好明确为 `capture_logging=true, rewrite=false`，所以没有 WeModern CallStyle 属于预期；微信动态来电 channel 又被用户设为 importance 1 / Silent + Minimize，系统没有执行它自己的 full-screen intent。
- Android 官方锁屏来电模型要求 full-screen notification 启动允许用户接听的 Activity。当前 CallStyle 原先把 WeModern proxy 同时用作 content 和 full-screen intent；proxy 一启动就会取消 CallStyle，再用 `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE` 二次发送微信 token。锁屏下 proxy 可能尚不可见，第二跳可能被推迟到解锁，并且无论成功与否都已经移除了 CallStyle。
- 一次候选修复把微信原始 token 直接交给 `setFullScreenIntent(..., true)`。Pixel 9 Pro / API 37 真机确认该路径会在锁屏上立即启动微信全屏接听 Activity；虽然解决了解锁后才跳转的问题，但用户明确不希望来电自动打开 Activity，因此该展示行为被否决。
- 同一构建关闭 WeModern 的全屏通知特殊访问后，系统不再发送 token，而是把通知降级为锁屏/AOD 上带 Answer/Decline 的展开式 CallStyle HUN；用户确认这种效果更好。最终实现删除 `USE_FULL_SCREEN_INTENT` 声明、设置入口和授权状态，整卡和操作按钮只在用户主动点击后通过 proxy 打开微信。
- AOSP API 37 实现进一步证明“无权限声明但保留 FSI 字段”不可作为最终方案：NotificationManagerService 会先清除无权限的 `fullScreenIntent`，且因为应用从未 requested 该权限，不会设置 `FLAG_FSI_REQUESTED_BUT_DENIED`；后续 CallStyle 校验仍会拒绝通知。最终改用通知监听服务自身的临时 `shortService` FGS，在通知对象中完全不设置 FSI；接通前先退出 foreground，再发布不受 short-service 时限约束的 Live Update。
- 调研结论是不引入假的 self-managed `ConnectionService`：它不会在普通情况下自动替应用生成来电 UI，且 WeModern 无法真正接听、拒绝或管理微信音频生命周期；伪造 Telecom call 还会污染当前基于音频模式的真实接通判断。

## 2026-07-19 视频通话源序列对照与优化复用

本轮视频通话在“采集与日志”开启、“改写通知”关闭时完成，因此 capture 展示的是未经 WeModern cancel/snooze 的微信原始序列。`id=41` 最终以 reason 1 移除代表用户进入通知目标，不是视频通知不支持滚动 snooze；当时 rewrite 关闭，所以没有 reason 18 属于预期。

| 阶段 | 已验证语音样本 | 最新视频样本 | 对照结论 |
| --- | --- | --- | --- |
| 来电展示 | `id=41`、flags `0x96`、category `call`、动态 `#voip_ringtone_channel_*`、微信 custom RemoteViews、微信 content/full-screen PendingIntent | 字段完全相同；文案为 `JASPRinvites you to video call` | 同一个来电分类和 CallStyle 发布路径；仅 `video=true`。 |
| 待接听状态 | `id=40`、`reminder_channel_id`、flags `0x02`、`Voice call in use` | 同 ID/channel/flags，文案为 `Video call in progress` | 同一个 WAITING_STATUS 和 listener cancel 策略。 |
| 进入通话页面 | `id=40` 从 `0x02` 更新为 `0x62`，首组更新间隔很短 | 同样从 `0x02` 更新为 `0x62`，并连续出现同一突发组 | 同一个“首次 `0x62` 只登记候选”的防误判规则。 |
| 通话结束 | `id=40/0x62` 由微信 reason 8 `APP_CANCEL` 移除 | `capturedAt=1784431384864` 同样 reason 8 移除 | 同一个 Live Update 结束和清理路径。 |
| 通知资源和权限输入 | small icon `0x7f080bc8`、RemoteViews layout `0x7f0c1cd1`、PendingIntent creator `com.tencent.mm` | 完全一致 | 不需要新增视频专用资源提取或跳转逻辑。 |
| 系统 channel 配置 | `id=41` 动态 channel 与 `reminder_channel_id` importance 1 | 两个 channel 与语音共用，importance 1 | 现有 Silent + Minimize 同时覆盖语音和视频，无需新增系统分类设置。 |

据此，视频通话正式复用语音的全部优化：标准 `CallStyle.forIncomingCall`、代理 full-screen/content/Answer/Decline PendingIntent、来电阶段不计时、`id=41` 2 秒滚动 snooze、`id=40/0x02` cancel、延迟确认 `0x62`、接通后标准 `ProgressStyle` Live Update、`id=40/0x62` 2 秒滚动 snooze、reason 8 结束清理、固定替换 ID，以及消息/气泡管线隔离。视频分支只改变来电/通话文案、material video icon 和 `CallStyle.setIsVideo(true)`。

真实 capture 样本已加入分类器回归测试。源行为同构已经确认；仍需在 rewrite 开启时再完成一通视频来电的可见结果验收，确认 CallStyle、Live Update、源通知压制和结束清理均与语音一致。

## 2026-07-19 语音通话实现与验证过程

本节是基于 capture-only JSONL 和人工行为对齐后实现的开发方案，不代表已经完成用户真机验收。采样时“采集与日志”开启、“改写通知”关闭，微信原通知未被 WeModern 取消、snooze 或替换。

### 新样本确认的阶段信号

- 用户不在微信内时，微信同时显示两类通知：`id=41` 是带联系人头像和名称的来电展示，`id=40` 是正文为 `Voice call in use` 的普通常驻通知。
- `id=41` 具有 `CATEGORY_CALL`、高重要性、`fullScreenIntent`、微信自定义 `RemoteViews` 和来电邀请文案；其 channel ID 在样本间动态变化，不能用固定 channel ID 单独分类。
- `id=40` 从来电开始就已经显示 `Voice call in use`。待接听时 flags 为 `0x02`（`ONGOING_EVENT`），因此该文案本身不能代表已经接听。
- 用户进入微信的接听/挂断页面后，`id=41` 会消失，但此时仍可能尚未接听；`id=41` 消失或通知点击都不能触发 Live Update。
- 早期样本曾显示真正接通附近 `id=40` 从 `0x02` 更新为 `0x62`，即同时包含 `ONGOING_EVENT | NO_CLEAR | FOREGROUND_SERVICE`，当时把该转换作为最强接通信号；第三轮人工对齐已证明仅进入微信接听页也会发生同样转换，因此这一早期结论已被修正，单次 `0x02 → 0x62` 不能证明接通。
- 通话结束后，`id=40` 由微信以 `APP_CANCEL` 移除；该事件可用于结束 WeModern Live Update，服务重连时还需用 active notification scan 补偿遗漏的结束回调。

### 来电 CallStyle 方案与已接受 tradeoff

- 来电阶段使用 Android 标准 `Notification.CallStyle.forIncomingCall`，以 `Person` 提供联系人名称和头像，不使用自定义 `RemoteViews`，也不启动 chronometer 或请求 Live Update promotion。
- Answer 和 Decline 两个 `CallStyle` 必需 action 都允许指向同一个“打开微信通话页面”目标。目标优先复用微信 `id=41` 原通知的 `fullScreenIntent`，不可用时回退到 `contentIntent`，并可通过 WeModern 的 activity proxy 转发；不得猜测或硬编码微信内部 Activity component。
- 这是明确接受的产品 tradeoff：Answer 和 Decline 按钮都只进入微信，并不直接执行接听或拒接。微信打开后会展示自己的全屏接听/挂断界面，由用户在微信内完成最终动作；系统生成的 Answer/Decline 按钮文案不能自定义。
- 用户进入微信后 `id=41` 消失时，移除 WeModern 来电 `CallStyle`，但不创建 Live Update。首个关联 `id=40/0x62` 只登记候选；至少 1 秒后的新 `0x62` 更新才用同一受控通知身份发布标准 Live Update，并从确认更新的 `when` 开始计时。10 秒无更新兜底只用于避免漏掉整通电话。
- `id=40` 被微信移除时结束 Live Update。重复的 `0x62` 更新必须去重；整个通话路径必须显式排除消息分组、会话历史和 bubble host。

### 2026-07-19 首轮改写失败与修正

- 开启采集和改写后的首轮语音验证中，`id=41` 在 `capturedAt=1784425920094` 发布，直到 13.7 秒后才由微信以 reason 8 撤销；期间没有 listener-cancel removal，用户实际仍看到微信 `id=41`。同轮偏好文件确认两个开关均为 `true`，排除设置未开启。
- 前一轮视频样本同样保留 `id=41`，但接通后的 `id=40/0x62` 出现 reason 18 snooze，说明服务和连接态改写路径确实在工作。问题收敛到来电发布前额外读取 `WeChatForegroundState`：该状态依赖异步 activity 日志，离开微信后可能仍保留旧 foreground component，于是 CallStyle 根本没有进入发布流程。
- 修正后，特征命中的 `id=41` 本身是来电展示的权威信号，代码始终先尝试发布 CallStyle，只有 `notify()` 成功后才隐藏原通知；真实的微信 activity resume 事件仍会撤下来电替换卡片。Answer、Decline 和整卡点击使用三个身份独立的 proxy PendingIntent，三个目标都保持为同一个微信通话页面。
- 第二轮仍失败后，使用 debug-only 真机探针复用正式 CallStyle builder，API 37 从 `NotificationManager.notify()` 返回明确异常：`CallStyle notifications must be for a foreground service or user initated job or use a fullScreenIntent`。当时补充权限和代理 full-screen intent 后，同一探针成功发布，系统记录为 `category=call`、两项 action、`ONGOING_EVENT|HIGH_PRIORITY|NO_DISMISS`；验证完成后导出探针已删除。后续锁屏取舍否决了 FSI 路径，最终改用同一异常明确允许的临时 FGS 分支。
- 同轮系统日志还显示新的 `id=40` 多次被 `Ignored enqueue for snoozed notification` 丢弃。原因是上一通连接态曾对固定 key `0|com.tencent.mm|40|null|10349` snooze 30 分钟，微信快速重拨仍复用该 key。设备残留已通过 shell `unsnooze` 清除，正式实现删除连接态 snooze，改为替换成功后仅对当前通知执行 listener cancel。
- 第三轮首次同时成功显示 CallStyle 和 Live Update，但微信 `id=41` 仍保留；capture 中没有 listener-cancel removal，直到进入微信后才出现 reason 8，证明系统忽略对 ongoing `id=41` 的普通 cancel。来电源现改用 2 秒滚动 snooze；每次 snooze 前清除 post dedupe 身份，使同一系统记录到期重现时可立即再次隐藏，同时把快速重拨最坏抑制窗口限制为约 2 秒。
- 同一轮行为对齐显示：`id=41` 在 `1784427321908` 因进入微信撤销，随后 `id=40` 在约 0.4 秒内从 `0x02` 变成首组 `0x62`；用户尚未接听。真正接听约 8.6 秒后，`id=40` 才发布另一组 `0x62`。早晚两组的标准字段除 `when/postTime` 外完全一致，因此 foreground flags 不能单独证明接通。当前窄规则等待首个 `0x62` 之后至少 1 秒的新更新；10 秒兜底是可用性折衷，并非精确接通信号。

### 仍需验证的边界

- `id=41` 在已采集标准字段中 `largeIcon=null`，头像位于微信自定义 `RemoteViews`；优先方案是按联系人名称复用普通微信消息中可获取的 `154×154` bitmap 头像缓存，首次联系人无缓存时使用明确的 fallback。直接从 `RemoteViews` 提取头像仍是独立的脆弱性实验，不能阻塞状态机。
- 微信 `id=40` 接通后是前台服务 / `NO_CLEAR` 通知。长时间 snooze 会抑制后续通话复用的同一个 key，已确认不可使用；普通 listener cancel 又无法可靠移除受保护的 `0x62`。当前实现仅在 Live Update 发布成功后使用 2 秒滚动 snooze，把快速重拨的最坏抑制窗口限制在约 2 秒，并依靠 app-cancel 日志补偿 snooze 窗口中的结束事件。
- 第四轮真机验证中，用户把微信动态来电 channel 降级后，WeModern 标准 CallStyle 显示正确，接通后的 Live Update 也能显示；这证明两阶段替换主路径可用。Live Update 相对人工接通约晚 5 秒出现，当前作为避免 VoIP 页面前台误判的可接受延迟，后续继续寻找更快的接通信号。
- 同轮确认 `id=40` 在待接听和 Live Update 阶段都仍可见。接通样本为 `reminder_channel_id`、`flags=0x62`，系统保护导致 listener cancel 调用不能可靠移除；通话结束仍以 reason 8 `APP_CANCEL` 移除。最新实现因此只对待接听 `0x02` 尝试普通 cancel，在 Live Update 已成功发布后才对 `0x62` 使用 2 秒滚动 snooze，并让自隐藏标记自动过期，避免吞掉同 key 的真实结束事件。该调整等待下一轮设备验证。
- 需要验证两个 CallStyle action 在锁屏、微信前台/后台、快速重复来电和 PendingIntent 已失效时都能打开正确通话页面。action 打开微信不等于接听，分类器不得把 action 点击、proxy Activity resume 或 `id=41` removal 当作连接态。
- 当前证据只覆盖 Pixel 9 Pro / API 37 上的微信语音来电。视频、去电、拒接、无人接听、双方分别挂断、中英文界面和微信版本变化仍需按完整时间线采集。
- 在上述只读判定和跳转验证完成前，保持默认 capture on / rewrite off；未知或冲突状态继续保留微信原通知。

## 完整尝试时间线

下表按本轮实际反馈顺序记录。现象来自真机观察、用户反馈、logcat 和通知列表检查；没有完整原始采集文件支撑的判断均只作为线索，不作为分类规则。

| 顺序 | 目标或实现尝试 | 真机结果 | 结论与处理 |
| --- | --- | --- | --- |
| 1 | 排查“语音通话已经进行，但没有 WeModern voice call 通知”；确认 ADB 已开启并开始读取通知/系统日志。 | 已有通话没有生成预期通知。 | 仅凭通话 activity 或单个活动通知快照无法还原此前的状态转换，需要观察一次完整新来电。 |
| 2 | 安装第一轮修复并在用户重新拨入时持续监控；扩大 VoIP 识别范围，尝试把微信通话通知重发为 WeModern 通话通知。 | 能看到通话相关输出，但来电阶段出现双重通知。 | 新规则同时命中了微信的多个通话相关通知，或旧/新替换项没有形成一对一关系。 |
| 3 | 尝试合并/去重通话通知，让来电与通话中共用受控的 WeModern 状态。 | 重复有所变化；接听后状态看似正确，但 Live Update 文案仍为 `invites you to voice call`。 | 通知是否存在与通知内容是否进入“已连接”是两个问题；不能沿用来电邀请文案作为通话中内容。 |
| 4 | 把流程拆成两阶段：来电待接听使用弹出式普通通知，不使用 Live Update；接听后再更新为 Live Update。 | 一轮结果中普通弹出通知和 Live Update 都没有显示。 | 分阶段条件过窄或取消原通知的时机早于替换通知成功发布，造成整个通知链路消失。 |
| 5 | 调整发布和 promotion 顺序，保留接听后的常驻通知，再请求 Live Update promotion。 | 来电时仍无通知；接听后只有常驻普通通知，没有 promotion 为 Live Update。 | “持续通知”不等于“可 promotion 的 Live Update”；发布时机、样式、channel 和 promotable characteristics 必须同时满足。 |
| 6 | 继续处理阶段通知、companion 通知和替换 ID 的关系。 | 通知栏同时出现 5 条：2 条微信、3 条 WeModern，其中包含 1 条 bubble host。 | 通话通知误入了消息/气泡或 companion 路径；只在末端去重无法解决多条源通知分别生成替换项的问题。 |
| 7 | 观察到一条带联系人头像和名称的微信通知反复弹出、隐藏；尝试以它作为 WeModern 通话通知的身份来源，并增加头像提取逻辑。 | 头像来源是短暂通知，生命周期与主来电/通话状态不同。 | 该通知可作为联系人身份线索，但不能单独代表来电或已接通状态，也不能假设它会持续存在。 |
| 8 | 根据要求取消通话自定义布局，改回 Android 标准通知布局；头像和名称通过标准 `Notification.Builder` / `Person` / icon 字段表达。 | 布局方向被纠正，但没有解决阶段识别和重复通知问题。 | 通话通知后续必须继续使用 Android 标准布局，不再通过自定义 `RemoteViews` 拼装来电卡片。 |
| 9 | 调整计时规则：来电待接听不计时；只有升级为 Live Update 后才开始 elapsed time。 | 单次观察中“状态和是否计时”符合预期。 | 计时展示可以与来电阶段分离，但 promotion 的起始事件仍未被可靠识别。 |
| 10 | 曾把进入微信 VoIP Activity 当作接听开始，借 activity 日志切换到 Live Update。 | 进入页面后实际还需要选择接听或挂断，Live Update 提前开始。 | VoIP Activity 只说明通话 UI 被打开，不代表通话已接通；该触发条件被明确否决并撤回。 |
| 11 | 改为等待微信通知内容显示“通话进行中/通话中”后再 promotion；同时根据日志组合 channel、ID、flags 和文案识别连接状态。 | promotion 时机仍不稳定，微信 `id=41` 通知还会间歇性跳出。 | 单个 ID 或局部文案仍不足以稳定关联整组通知；`id=41` 的出现/消失不能直接作为唯一状态。 |
| 12 | 对动态 VoIP channel、`id=40` / `id=41` 配对、companion 通知、snooze 租期和残留项继续做定点修补。 | 最终用户判断“结果上还是不对”。 | 停止继续追加启发式规则，完整撤回本轮特殊逻辑。 |
| 13 | 将 `WeChatNotificationService.java`、`WeChatCallClassifierTest.java` 和三套 strings 恢复到 `v1.6.1`；删除实验用 `VoipAvatarExtractor.java` 及动态/连接态/companion 等特殊符号。 | 本地搜索确认实验符号不再存在；发布行为回到 `v1.6.1` 基线。 | 回滚完成。基线仍可由独立“改写通知”开关启用，但当前默认关闭。 |
| 14 | 先实现显式 `-PwechatCaptureOnly=true` 的 capture-only 构建，随后根据要求改成应用内两个运行时 Debug 开关。 | capture-only APK 和最终运行时开关 APK 均已安装验证；后者显示“采集与日志”开启、“改写通知”关闭，服务日志与通知列表一致。 | 构建属性方案已删除；最终方案是“采集与日志”和“改写通知”两个独立开关。 |

## 尝试过并已删除的特殊逻辑

以下内容都只属于本轮实验，不在当前新实现中：

- 用动态 VoIP channel、`id=40` / `id=41`、flags、ongoing 属性和中英文文案组合扩大通话识别。
- 把多条微信通话通知建模为主通知与 companion，并尝试合并到固定 WeModern 通知身份。
- 在普通来电 heads-up 与接听后 Live Update 之间做分阶段更新和 promotion。
- 以进入微信 VoIP Activity 作为连接态触发条件。
- 对微信原通知使用实验性 snooze/租期来抑制反复出现；该方案会遇到通知 key/ID 复用和到期重现问题。
- 从短暂出现的微信通知图标或 `RemoteViews` 中提取联系人头像；实验类 `VoipAvatarExtractor.java` 已删除。
- 为通话身份、阶段、头像和 companion 通知增加的专用缓存、分类器分支与测试样例。
- 任何非 Android 标准通知布局的来电呈现尝试。

上述条目描述的是已经删除的历史实验。当前开发代码另行实现本页 2026-07-19 状态机，并继续保留 `v1.6.1` 固定 VoIP channel 作为兼容路径；两者都仅在用户主动开启“改写通知”时运行。

## 已确认的技术事实

1. 进入微信接听/挂断页面不等于已经接听；activity 生命周期不能作为通话计时起点。
2. 微信会同时或先后发布多条通话相关通知，包含短暂的头像/名称通知；这些通知的可见生命周期并不等于通话状态生命周期。
3. `id=40`、`id=41` 或某个 channel 在已有样本里有参考价值，但尚未证明跨语音/视频、来电/去电、语言和重复来电都稳定。
4. 来电邀请文案与通话进行中文案必须分开；替换通知进入 Live Update 后不能继续显示 `invites you to voice call`。
5. 待接听阶段不应开始 elapsed time；只有确认微信已经进入通话进行中后才允许创建或升级计时 Live Update。
6. 通话通知不得进入消息分组或 bubble host 管线，否则会放大通知数量。
7. 在替换通知确定成功前隐藏、取消或长时间 snooze 微信原通知，会把分类失败放大为“完全没有通知”。未知状态必须优先保留原通知。
8. Android 标准通知布局是明确要求；头像和联系人名称只能通过标准通知字段表达。

## 当前采集与改写控制

设置界面的“调试”区域提供两个独立开关：

| 采集与日志 | 改写通知 | 行为 |
| --- | --- | --- |
| 开 | 关 | 当前默认。微信通知保持原样；记录原始 `ACTIVE_SCAN`、`POSTED`、`REMOVED` 到 logcat 和 JSONL。 |
| 开 | 开 | 先记录原始事件，再运行 `v1.6.1` 的解析、隐藏和替换行为。 |
| 关 | 开 | 不写原始采集记录；运行 `v1.6.1` 改写。 |
| 关 | 关 | 完全透传微信通知，不采集、不改写。 |

关闭改写时不会启动 `NotificationCancelLogWatcher`，不会运行消息/VoIP 解析，不会取消或 snooze 微信通知，也不会创建替换通知、Live Update 或 bubble host；切换为关闭时还会清理残留的 WeModern 替换通知与 replacement 映射。两个开关通过 `SharedPreferences` 监听即时生效，不需要重新构建 APK 或重新授予通知使用权。

## 采集记录内容

每条 JSONL 包含：

- `StatusBarNotification` 的 package、key、id、tag、group、post time、ongoing 与 clearable。
- `Notification` 的 `when`、flags、priority、category、channel、group、shortcut、sound、vibration 和可见性。
- 标准 extras 的键、类型和值，包括 title、text、subtext、people 和模板信息。
- small/large icon 描述，`RemoteViews` 包名与 layout ID。
- actions、remote inputs，以及 content/delete/full-screen `PendingIntent` 的创建方。
- 当前 ranking、importance、conversation/bubble 能力及 notification channel 信息。

记录输出到 logcat tag `WeModern.Capture`，同时追加到应用私有文件：

```text
files/wechat_notification_capture.jsonl
```

文件达到 16 MiB 时轮换为：

```text
files/wechat_notification_capture.previous.jsonl
```

导出命令：

```bash
adb logcat -v threadtime -s WeModern.Capture
adb exec-out run-as me.jaspr.wemodern cat files/wechat_notification_capture.jsonl > wechat_notification_capture.jsonl
```

## 构建与设备验证记录

- 回滚后的 capture-only 属性版本分别以采集开/普通模式完成过 `:app:testDebugUnitTest`、`:app:assembleDebug` 和 `:app:lintDebug`。
- capture-only APK 曾安装到 Pixel 9 Pro；服务日志确认 capture-only 已启用、rewrite 已禁用，`cmd notification list` 当时没有 WeModern 自有通知。
- 当时没有活动微信通知，因此尚未生成首条有效 JSONL 样本；这不能算通话序列验证成功。
- 构建属性随后被运行时开关取代，`BuildConfig.WECHAT_CAPTURE_ONLY` 和 `-PwechatCaptureOnly=true` 已删除。
- 当前运行时开关版本已通过 `:app:testDebugUnitTest`、`:app:assembleDebug` 与 `:app:lintDebug`，并已安装到 Pixel 9 Pro。
- 结构化界面检查确认“采集与日志”为开启、“改写通知”为关闭；顶部状态显示“正在采集微信原始通知；通知改写已关闭”。
- 服务日志确认 `captureLogging=true, rewrite=false` 和 `listener connected, rewrites disabled`；最终通知列表没有 WeModern 自有通知。
- 音频模式快速确认版本新增 `CallAudioModePolicyTest` 的正反例，并再次通过 `:app:testDebugUnitTest`、`:app:assembleDebug`、`:app:lintDebug` 和 `git diff --check`；Debug APK 已安装到同一 Pixel 9 Pro / API 37。当时结构化界面确认“Incoming call pop-up”为 On，说明该设备的 `canUseFullScreenIntent()` 返回允许；该入口和权限后来因锁屏只保留 CallStyle 的产品决定而删除。安装时设备仍为 capture on / rewrite off，因此尚未把基础检查误记为真实通话验收。
- 随后的语音 rewrite 真机测试确认：实际接通触发音频模式切换后，Live Update 立即升级，旧版约 5 秒体感延迟不再出现；用户验收结论为“延迟问题已经完美解决”。
- 锁屏 direct full-screen handoff 版本通过 `:app:testDebugUnitTest`、`:app:lintDebug`、`:app:assembleDebug` 与 `git diff --check`，并已安装到同一 Pixel 9 Pro / API 37；随后真实锁屏来电确认它会立即启动微信 Activity。关闭特殊访问后，同一来电改为系统 CallStyle HUN并获用户确认；direct handoff 因此不作为最终行为。
- 第一版无权限声明候选删除了设置入口并成功安装，APK 合并 manifest 与设备 `dumpsys package` 均确认 requested permissions 不含 `USE_FULL_SCREEN_INTENT`；但随后 AOSP 源码审查发现它的无权限 FSI 字段会在 CallStyle 校验前被清除，因此未作为最终实现。替代的 `shortService` FGS 版本已通过 `:app:testDebugUnitTest`、`:app:lintDebug`、`:app:assembleDebug` 与 `git diff --check`，仍需安装并完成一通真实锁屏来电验收。

## 后续采集场景

每个场景都应从通知栏没有微信通话通知开始，并在 JSONL 之外记录明确的人工动作时间：

1. 语音来电保持至少 30 秒，不进入微信。
2. 语音来电进入接听/挂断页面，但暂不接听。
3. 真正接听语音通话，保持前台，再切换到其他应用并返回。
4. 分别采集主动挂断、对方挂断、拒接和无人接听。
5. 完整重复视频通话的来电、接听、切后台和结束流程。
6. 挂断后短时间内再次来电，确认 notification key、ID、tag 和 channel 是否复用。
7. 分别采集微信中文和英文界面，避免把单一语言文案误当成状态协议。

## 后续扩展与验收条件

- 至少取得多个完整语音和视频序列，而不是只看某一时刻的 `dumpsys` 或 logcat 片段。
- 为每条通知建立 `POSTED → 更新 → REMOVED(reason)` 时间线，并与人工动作时间对齐。
- 找到能稳定区分待接听、仅进入通话页、真正已连接和结束的字段组合；单个 ID、activity 或文案不得独立决定连接态。
- 新分类器先只读运行并输出判定，不隐藏原通知；验证无误后才允许定点改写。
- 未知或冲突状态必须保留微信原通知。
- 先证明一条源状态只生成一条目标通知，再引入 promotion；通话路径必须显式排除消息分组与 bubble host。
- 不再用长时间 snooze 覆盖可能复用的通知 key。
