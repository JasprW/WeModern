# 服务号聚合会话

## 目标

把微信服务号容器建模为一个 Android conversation，各服务号作为这个会话中的参与者，而不是让显示语言或服务号名称生成多个 Bubble。服务号 Bubble 需要能独立于私聊和群聊开启或关闭。

## 已验证环境与来源证据

- 设备：Pixel 9 Pro，Android 17 / API 37。
- 当前微信：8.0.72；采集文件包含升级前后的历史通知样本。
- WeModern：1.8.0 capture-only JSONL，读取 `wechat_notification_capture.jsonl` 与轮换文件，全程未修改设备设置。

样本中 `Service Accounts`、`服务号` 使用消息 channel，正文格式为 `服务号名称: 内容`。不同服务号拥有不同且跨多次通知保持稳定的 source notification ID；相同服务号在标题语言改变后仍使用原 ID。source `contentIntent` 由 `com.tencent.mm` 创建且 immutable。头像来自 source `largeIcon`，部分通知暂时不携带头像，因此不能只保存一个 conversation 级头像。

## 已实现模型

- 固定 conversation ID：`wechat:service_accounts`。
- 支持容器标题：`Service Accounts`、`服务号`、`服務號`；旧的本地化 conversation preference 读取时合并到固定 ID，并保留最近的非默认覆盖。升级时同时移除旧本地化 dynamic / long-lived shortcuts，后续只发布固定 ID。
- participant key：`wechat-service:<source notification id>`。
- 每条消息使用独立 `Person(name, key, icon, bot=true)`，`MessagingStyle` 明确标记为 group conversation。
- 参与者名称最多记录最近 25 个；通知和 shortcut 使用的头像为 96px，shortcut 最多附带最近 8 个参与者，降低 Bitmap Binder 负载。
- 最新服务号头像更新聚合 conversation 的 notification large icon、shortcut icon 与 Bubble icon。头像 fallback 顺序为：当前 source `largeIcon`、同 participant 缓存、按当前 sender 精确匹配的旧 `wechat:<sender>` 私聊头像。旧私聊头像迁移到 participant 缓存后继续复用；不读取无法确定参与者归属的旧 Service Accounts 聚合头像，微信后续提供的新头像仍会覆盖缓存。
- 整体 notification、shortcut 和 Bubble 使用最新 source `contentIntent`。Android 标准 `MessagingStyle.Message` 没有逐消息 PendingIntent，因此历史行不分别跳转。
- 点击成功后按固定 conversation ID 清空整个服务号 `MessagingStyle` 历史与 Bubble 快照。微信 source 移除也继续按聚合会话清理，符合微信服务号本身同时已读和清除的语义。
- Bubble 默认策略新增第三类“服务号”，默认开启，与私聊、群聊互不影响；已知会话页仍可用 Always allow / Never allow 覆盖。

## 验证状态

已完成服务号标题识别、固定 identity、participant key、精确旧 sender fallback、独立 Bubble 默认策略和普通覆盖优先级的 JVM 测试，并通过 debug 编译与 lint。Debug APK 已在上述 Pixel 保留数据覆盖安装：通知监听器保持 enabled 且重新连接，Bubbles 页面显示独立 Service Accounts 行，关闭后 UI 与 preference 均变为 off，再次开启后恢复 true；升级迁移标记已写入，`dumpsys shortcut` 不再包含旧本地化服务号 shortcut。2026-08-31 的跃捷 source 通知没有 `largeIcon`，但设备仍保留旧 `wechat:跃捷国际物流集运转运` conversation 头像；安装新版后日志记录迁移 1 个精确旧头像，生成 `wechat-service:-1898834351` participant 文件和 canonical conversation 文件，`dumpsys shortcut` 确认 `wechat:service_accounts` 使用 bitmap icon 并附带 3 个 `Person`。安装后未观察到 WeModern crash、头像缓存或 shortcut 发布错误。

仍需真实通知验收：

1. 连续收到两个不同服务号消息，展开通知确认每条消息使用对应头像，主头像跟随最新服务号。
2. 在简体中文、繁体中文和英文微信标题之间切换，确认 shortcut ID 始终为 `wechat:service_accounts`，设置中只有一个服务号会话。
3. 分别关闭和开启“服务号”Bubble，确认普通通知始终保留，而 BubbleMetadata 按设置移除或恢复。
4. 在 conversation trampoline 模式打开 Bubble，确认进入最新服务号；返回后收到新消息时旧历史已整体清空。
5. 检查 `dumpsys shortcut` 与 `dumpsys notification`，确认 shortcut 为 long-lived conversation、参与者和 Bubble icon 未触发大小或发布回退。
