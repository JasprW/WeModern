# 未实现功能探索

此目录只记录尚未交付能力的证据、可行性、外部依赖、风险和下一步。结论为“可行”不代表已经实现；实现完成后应把最终行为迁入 `../current-implementation.md` 并在 `../changelog.md` 记录版本变更。

| 主题 | 状态 | 结论 |
| --- | --- | --- |
| [微信 Direct Share](../2026-07-15-wechat-direct-share-feasibility.md) | 暂不实现 | Android 标准 Direct Share 可接入，但无法稳定取得微信指定联系人的 internal username / OpenSDK 映射，因此不能产品化地直达联系人。 |
| [微信标记已读](../2026-07-16-wechat-mark-read-feasibility.md) | 暂不实现 | 需要微信支持的稳定接口或可授权的会话标识；应先确认可维护的官方能力。 |
| [微信语音/视频通话通知实验与采集记录](2026-07-18-wechat-call-notification-capture.md) | 已实现 / 待完整回归 | 保留全部已撤回尝试；来电只使用标准 CallStyle，不申请全屏通知权限，Answer/Decline 都只在点击后打开微信通话页；接通由微信 `0x62` 候选门控音频模式快速确认，并保留通知更新与超时兜底。 |
| [设置页滚动性能检查](2026-07-18-settings-scroll-performance.md) | 已优化 | 确认轻微卡顿主要来自超高 lazy item 的 UI/布局尖峰；记录 item 拆分、cache window、真机指标以及已撤回的字体和 ART 对照实验。 |
| [Bubble channel 初始化调查](2026-07-20-bubble-channel-initialization.md) | 已修复 / Pixel 已验证 | API 37 上高重要性 Bubble 只显示 flyout、不显示普通 pinned HUN，但仍发声和振动；消息已合并到单一 alerting channel，旧 quiet/host channel 迁移删除。 |
| [Trampoline PendingIntent bridge](2026-07-20-trampoline-pending-intent-bridge.md) | 已实现 / 平台限制 | trampoline 默认由 per-conversation bridge 转发最新微信 immutable 会话 token；精确桥接已在 Pixel 验收，其他系统仍可能复用全屏 task。 |
| [多会话 Trampoline Bubble](2026-07-21-multi-conversation-trampoline-bubbles.md) | 已交付 | 默认由每个会话的替换通知作为独立 Bridge Bubble host；另提供一个只打开微信 Home、改善 IME 适配的共享 Bubble 模式。会话级模式的跨会话并存、Back 收起和新消息重定向已通过 Pixel 双会话验收。 |

新探索文档建议采用 `YYYY-MM-DD-<topic>-feasibility.md` 命名，并写明：目标、已验证环境、证据、可行与不可行边界、依赖/风险、推荐结论和重新评估条件。
