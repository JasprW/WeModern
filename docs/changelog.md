# WeModern 版本变更记录

本记录汇总已合入的实现变化；未发布或仍在验证的方案应记录在 `explorations/` 或 `plans/`，不应写成已交付功能。

## Unreleased

## 1.7.1 — 2026-07-20

- 修复来电期间微信 `id=40/0x02` 再次并列显示：最新 Pixel 9 Pro / API 37 capture 证明 Android 会忽略 notification listener 对该 ongoing 通知的 cancel，导致它从来电起一直保留。现在只在 WeModern CallStyle 已成功建立后对 `0x02` 使用 1 秒可续短 snooze；用户点击 CallStyle 或观察到微信恢复前台时，会通过公开 listener API 把该 key 的 snooze 租期缩到 1ms，并开启 2.5 秒状态切换窗口，避免 Android 丢弃接听页触发的 `0x62`。首个及同批 `0x62` 在连接候选登记后立即切入原有 2 秒短压制。替换发布失败时继续保留微信原通知。
- 重新设计 WeModern 通话 channel：全新“微信来电提醒”使用高重要性、系统默认来电铃声与振动，并通过 `FLAG_INSISTENT` 持续提醒到用户处理或来电状态结束；“微信通话中”保持静音并承载 ongoing CallStyle / Live Update。接通时以同一通知 ID 从来电 channel 原地更新到通话中 channel，移除持续提醒标记，避免重复通知和二次提醒；旧 `wechat_calls_live_quiet` 仅作为升级清理项，不再用于发布通知。Voice / Video 测试同步覆盖这套两阶段声音行为；Pixel 9 Pro / API 37 全新安装后的系统 channel 配置已通过 `dumpsys notification` 验证。
- 将接通后的主通话替换从 `ProgressStyle` 改为可 promotion 的 `CallStyle.forOngoingCall`：继续显示联系人、头像和通话计时，并以同一固定通知 ID 从来电卡片更新为通话中卡片。系统 Hang up 操作使用独立 fake PendingIntent 打开微信通话页，不直接挂断；这是当前无法取得微信内部挂断 Intent 时接受的 tradeoff。API 37 的来电与通话中阶段统一由已声明用途的 `specialUse` FGS 承载，以满足 CallStyle 校验，同时仍不声明或使用全屏通知权限。
- 修复 ongoing CallStyle 改造引入的 API 37 来电改写回归：真机日志显示原微信 `id=41` 从来电起持续存在且没有 snooze，系统也没有接受来电阶段的 `shortService` FGS；同一通电话接通后的 `specialUse` FGS 与 promoted ongoing CallStyle 则正常。现在来电也复用已验证的 `specialUse` 路径，避免 foreground type 切换差异，并继续只在替换发布成功后隐藏微信原通知；后续语音来电复测确认恢复正常。
- 将 Voice / Video 测试通知改为与真实通话共用标准 CallStyle 构造：首次点击发布不计时的 incoming CallStyle，Answer 以同一 ID 更新为计时且可 promotion 的 ongoing CallStyle，Decline 与 ongoing Hang up 都直接停止专用测试 FGS 并移除通知。每次测试携带独立 session token，旧卡片的延迟操作不能关闭刚启动的新测试。测试与旧固定 VoIP 兼容路径一并迁移后，删除 `Notification.ProgressStyle` 构造类及全部生产引用；Pixel 9 Pro / API 37 已完成语音和视频两条完整操作链验收。
- Voice / Video 测试 CallStyle 现在与 Message 测试共用 mock 联系人 `Mia` 和 `ic_test_message_avatar_48`：incoming 与 Answer 后的 ongoing 阶段都通过 `Person` 和 large icon 显示同一名称、头像，语音/视频标题资源不再单独伪装成联系人名称。

## 1.7.0 — 2026-07-19

- 新增根目录 `DESIGN.md` 与机器可读的 `DESIGN.json`，以 Material You 动态颜色和 Material 3 Expressive 的灵动、活泼设计语言统一后续界面设计。
- 优化 Bubbles 与 Tests 设置体验：Android 气泡设置和 Chat bubbles 开关统一使用 Material Symbol `bubble`，移除 Tests 标题尾部提示与 Bubble trampoline 的 Experimental 标记，并明确 trampoline 可能带来通知栏重复通知、host channel 优化用于弱化其存在感。关闭 Chat bubbles 时不再清空 trampoline 偏好，重新开启后自动恢复；依赖选项使用无回弹的原生展开/淡入动画，会话排序使用稳定 key 的原生位置动画。
- 锁屏来电统一为 CallStyle 通知，不再直接启动微信：删除 `USE_FULL_SCREEN_INTENT` 权限声明、“来电持续弹出”设置入口和授权状态；API 37 改由最长约三分钟的 `shortService` foreground type 承载不含 FSI 的纯 CallStyle，来电结束、用户点击或接通时立即退出前台状态，API 26–36 继续使用普通 CallStyle。Pixel 9 Pro / API 37 已确认关闭全屏特殊访问时锁屏显示带操作按钮的 CallStyle。
- 优化通话阶段切换：首个微信 `id=40/0x62` 仍只登记候选，但候选期间新增由微信会话门控的 `AudioManager` 模式检测；`MODE_IN_COMMUNICATION` / `MODE_IN_CALL` 可立即升级 Live Update，保留后续通知更新与 10 秒兜底，避免旧逻辑在实际接通后仍等待数秒。Pixel 9 Pro / API 37 语音真机复测确认原接通延迟已解决。
- 先撤回 `v1.6.1` 之后所有缺少完整证据的微信语音/视频通话识别、通知合并、snooze、头像和分阶段 Live Update 实验，并恢复发布基线；后续新状态机仅依据 capture-only 完整样本重新实现。
- 新增应用内 Debug 开关：“采集与日志”总开关控制微信通知 posted/removed/active-scan 的 `WeModern.Capture` 与 JSONL 记录，“改写通知”开关独立控制解析、取消或 snooze、替换、Live Update 与气泡 host；切换即时生效，开发阶段默认只采集、不改写。
- 补充完整通话实验记录，保留双重/丢失/残留通知、错误 Live Update promotion 与计时、头像通知、VoIP Activity、`id=40` / `id=41`、snooze 和回滚等全部尝试及失败结论，防止未发布探索被误当成当前能力。
- 修正设置页顶部运行状态：改写关闭时明确显示只采集或原样透传，不再错误显示“微信通知重写正在运行”。
- 简化设置页区块标题，移除 Debug 标题说明和 Bubbles 标题旁的状态文字，避免与区块内开关重复。
- 为微信原通知隐藏入口增加运行时 rewrite 二次保护，确保只采集模式即使发生错误调用也不会取消或 snooze 微信通知。
- 优化设置页滚动：将 Setup 与 Bubbles 的超高单一 `LazyColumn` item 拆分为带稳定 key/content type 的独立行和卡片，并增加前向预取与后向保留窗口，降低首次滚入区块及快速折返时的组合与布局尖峰。
- 主状态卡新增明确的“关闭 / Debug only / All set”运行标识，分别使用电源、调试和勾选图标及不同色调，避免只采集模式仍显示 All set；关闭态采用高对比度深色中性图标底板，避免与卡片背景融为一体。
- 基于 capture-only JSONL 重新实现待验证的微信语音来电状态机：`id=41` 使用标准 CallStyle，Answer/Decline 都打开微信原 PendingIntent；`id=40/0x02` 不再误入消息或提前计时，只有 `0x02 → 0x62` 才以单一固定 ID 创建计时 Live Update。联系人头像复用已有会话缓存，接通源通知采用 30 分钟短 snooze，并保留旧固定 channel 兼容路径。
- 修复来电 `id=41` 未被改写：activity 日志维护的微信前台状态可能滞后，旧逻辑因此跳过 CallStyle 发布并保留原通知；现在以命中的来电通知为权威信号，成功发布后立即隐藏 `id=41`，进入微信时再移除替换卡片。Answer/Decline 改用身份独立但都转发同一微信目标的 PendingIntent，避免系统合并 action。
- 再次修复 API 37 上来电与快速重拨均无替换：系统会拒绝既非 FGS/UIJ、又没有 `fullScreenIntent` 字段的 CallStyle；早期真机探针通过 FSI 验证标准 CallStyle 可发布，最终实现改用临时 `shortService` FGS 满足同一平台条件，不声明全屏通知权限，也不在通知中携带 FSI。连接态不再将复用 key 的微信 `id=40` snooze 30 分钟，避免后续通话的 `0x02/0x62` 被系统直接丢弃而无法创建 Live Update。
- 修复 CallStyle 出现后微信 `id=41` 仍并列显示，以及进入微信接听页即过早启动 Live Update：ongoing `id=41` 改用 2 秒可续短 snooze 隐藏，避免普通 listener cancel 被系统忽略又不长期压住下一通来电；首个 `id=40/0x62` 改为连接候选，等待至少 1 秒后的新 `0x62` 更新再计时，并提供 10 秒无更新兜底。
- 修复微信 `id=40` 在来电与 Live Update 阶段始终并列显示：待接听的普通 `0x02` 状态尝试 listener cancel，Live Update 成功后对系统保护的 `0x62` 前台服务通知使用 2 秒滚动 snooze；隐藏标记会自动过期，避免吞掉后续真实 APP_CANCEL。保留当前约 5 秒的安全接通延迟，并记录为待优化项。
- 记录 Pixel 9 Pro / API 37 的语音通话可用基线：微信 `id=41` 动态来电 channel 与 `id=40` 的 `reminder_channel_id` 都保持开启并手动设为 Silent + Minimize，再由 WeModern 提供 CallStyle 和 Live Update；明确动态 channel 重建、`reminder_channel_id` 可能影响其他提醒、约 5 秒 promotion 延迟以及视频通话待专项优化等边界。
- 对比最新 capture 后确认视频通话与语音使用相同的 `id=41/0x96 → id=40/0x02 → id=40/0x62 → reason 8` 生命周期、channel、RemoteViews 和 PendingIntent；视频现正式复用语音的 CallStyle、阶段判定、短周期源通知压制和 Live Update 清理，仅使用视频文案、图标及 `setIsVideo(true)`，并加入真实英文视频样本回归测试。

## 1.6.1 — 2026-07-17

- 修复 WeChat bubble 生命周期不稳定：新增 bubble task / 微信前台状态跟踪，使用代理启动路径，并且只在嵌入 bubble task 真正移除时清理 trampoline host。
- 修复普通微信取消、前台切换或 host 更新可能错误关闭气泡的问题；同步移除仍只处理普通替换通知。
- 增加上述启动、前台、清理和日志解析路径的 JVM 测试。

## 1.6.0 — 2026-07-17

- 加入私聊/群聊独立默认策略、单会话 Always allow / Never allow 覆盖、排序与已知会话管理。
- 气泡按会话策略切换 quiet 与 alerting 通道；关闭会话气泡不会关闭正常提醒。
- 升级 Android 构建工具链与 Material 3，并发布 v1.6.0。

## 1.5.1 — 2026-07-16

- 修复群聊中发送者头像被复用于其他会话的问题。
- 明确 bubble host 通知 channel 的设置入口和行为。

## 1.5.0 — 2026-07-16

- 稳定 trampoline host：一个固定、独立、最新会话的 host bubble，普通 replacement 重新支持同步移除。
- host 使用专用静默最小化通道，降低额外图标、声音和 heads-up 干扰。
- 发布 v1.5.0。

## 1.4.0 — 2026-07-15

- 修复会话气泡与 launcher shortcut 的启动、删除和图标稳定性问题。
- 发布 v1.4.0。

## 1.0–1.3 — 2026-06-23 至 2026-07-14

- 将应用更名为 WeModern，重写微信消息与 VoIP 通知，提供简体/繁体中文和设置 UI。
- 增加通知同步移除、Material You 设置流程、主题会话快捷方式、可配置应用图标行为和内建测试通知。
- 为 Android 17 加入原生会话 bubble、嵌入式可恢复的消息界面与 experimental Bubble trampoline。
- 将微信通话测试与实际通知统一为 Live Update / `ProgressStyle` 路径。

## 维护格式

发布版本时新增 `## <version> — YYYY-MM-DD` 小节。未发布修复可先用 `## Unreleased`；描述应包含用户可见结果，修复还要包含“问题 → 方案”。
