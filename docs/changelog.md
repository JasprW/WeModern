# WeModern 版本变更记录

本记录汇总已合入的实现变化；未发布或仍在验证的方案应记录在 `explorations/` 或 `plans/`，不应写成已交付功能。

## Unreleased

- 优化通话阶段切换：首个微信 `id=40/0x62` 仍只登记候选，但候选期间新增由微信会话门控的 `AudioManager` 模式检测；`MODE_IN_COMMUNICATION` / `MODE_IN_CALL` 可立即升级 Live Update，保留后续通知更新与 10 秒兜底，避免旧逻辑在实际接通后仍等待数秒。Pixel 9 Pro / API 37 语音真机复测确认原接通延迟已解决。Android 14+ 设置页同时新增“来电持续弹出”特殊访问状态和系统授权入口，CallStyle 发布日志记录实际 full-screen intent 可用性。
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
- 再次修复 API 37 上来电与快速重拨均无替换：系统会拒绝既非 FGS/UIJ、又没有 `fullScreenIntent` 的 CallStyle，现已声明对应权限并让替换复用微信目标作为 full-screen intent；真机探针确认系统接受标准 CallStyle。连接态不再将复用 key 的微信 `id=40` snooze 30 分钟，避免后续通话的 `0x02/0x62` 被系统直接丢弃而无法创建 Live Update。
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
