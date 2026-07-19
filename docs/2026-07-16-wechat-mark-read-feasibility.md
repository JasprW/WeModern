# WeModern 通知内“标为已读”可行性分析

## 结论

在当前普通手机模式、无 root、未修改微信的前提下，WeModern **不能仅靠自己增加一个通知按钮，真实清除微信会话里的未读状态**。

Android 支持 `SEMANTIC_ACTION_MARK_AS_READ`，但这个值只是动作语义。真正修改消息状态的能力必须来自消息应用提供的 `PendingIntent`。通知监听器调用 `cancelNotification()` 也只代表用户从系统通知界面移除了通知，不会获得修改另一个应用数据库的权限。

微信 8.0.69 本身存在一个真正的后台已读实现，但只为 Android Auto 生成：

- 微信为车机通知创建 `on_read` `PendingIntent`；
- 执行后进入微信自己的非导出 `BroadcastReceiver`；
- 接收器调用微信会话存储的 `updateUnreadByTalker()`，将该会话的 `unReadCount`、`unReadMuteCount`、`UnReadInvite` 和 `atCount` 等字段清零；
- 全程不需要打开聊天 Activity。

然而，微信只在 Android Auto 开关开启、手机处于车机 UI mode、检测到 USB audio/AOAP 设备且 Android Auto 包签名匹配时，才把这个 `PendingIntent` 放进通知。接收器执行时还会重新检查这些条件。当前普通通知没有这个能力令牌，WeModern 也不能自己构造等价调用。

因此：

| 场景 | 不打开微信 UI | 真实清除微信未读 | 当前可用性 |
|---|---:|---:|---|
| 只取消原通知或 WeModern 通知 | 是 | 否 | 可用，但只是表面清理 |
| WeModern 自己新增“已读”按钮 | 是 | 否 | 按钮可做，缺少微信侧能力 |
| 转发微信普通通知的 action | 是 | 否 | 微信普通消息通知没有 action |
| 转发 Android Auto `on_read` | 是 | 是 | 仅真实车机连接期间有条件可用 |
| 打开原 `contentIntent` | 否 | 是 | 可用，但会进入微信聊天 |
| Accessibility 自动进入会话再返回 | 否 | 是 | 不满足“不跳转”，且脆弱 |
| root / LSPosed 在微信进程内注入 | 是 | 是 | 实验可行，不适合普通发布 |

## 实机与样本

- 设备：Pixel 9 Pro
- Android：17 / API 37
- 微信：8.0.69，`versionCode=3022`，`targetSdk=35`
- 微信 base APK SHA-256：`4714286fc02cec090078b71d00c2d4dacf801c0d41d2bca60fe6d8333654364b`
- 验证日期：2026-07-16
- 验证输入：同一设备上收到两条用户发送的测试消息，并保持微信会话未读

系统通知历史中的两条微信原通知均为：

```text
Notification(channel=message_channel_new_id ... flags=SHOW_LIGHTS|AUTO_CANCEL ... category=msg)
```

如果 `Notification.actions` 非空，Android 的通知 dump 会显示 `actions=N` 及动作列表；这两条微信通知都没有该字段，即可见 action 数量为 0。当前 WeModern Bubble host 同样只有启动 Activity 的 `contentIntent`，`deleteIntent=null`，没有 actions。

当前设备状态也不满足微信的 Android Auto 分支：

```text
mCarModeEnabled=false
audio_accessory_connected=false
```

本次没有取消用户的测试通知、点击聊天或修改未读状态。

## Android 能力边界

### `SEMANTIC_ACTION_MARK_AS_READ` 不会自动产生已读行为

`Notification.Action` 由图标、标题和一个待执行的 `PendingIntent` 组成。`SEMANTIC_ACTION_MARK_AS_READ` 只是告诉系统这个 `PendingIntent` 的含义；系统不会因此代替原应用修改消息状态。

参考：

- [Android `Notification.Action`](https://developer.android.com/reference/android/app/Notification.Action)
- [Android Auto 消息通知与 mark-as-read](https://developer.android.com/training/cars/communication/notification-messaging)

### 通知监听器只能移除系统通知

Android 对 `NotificationListenerService.cancelNotification(key)` 的定义是通知监听器报告一次用户 dismissal。它会从通知管理器中移除通知并产生 `onNotificationRemoved()` 回调，不会调用微信的内部“读消息”逻辑。

参考：[Android `NotificationListenerService`](https://developer.android.com/reference/android/service/notification/NotificationListenerService)

在当前代码中，WeModern 收到微信通知后只复制 `contentIntent` 和可能存在的 `deleteIntent`，随后调用通知监听器的 `cancelNotification()` 隐藏原通知：

- `WeChatNotificationService.java:303-306`
- `WeChatNotificationService.java:686-694`

这解释了为什么 WeModern 能让原通知从 SystemUI 消失，却不能据此证明微信未读状态已经改变。

## 微信 8.0.69 静态分析

以下类名和行为来自上述实机 APK 的反编译结果；混淆符号可能随版本变化。

### 1. 普通通知构建路径

`com.tencent.mm.booter.notification.e0.b(...)` 构建消息通知。它会触发 `AutoNewMessageEvent`，只有事件返回 Android Auto conversation 数据时，才写入：

```text
android.car.EXTENSIONS
  car_conversation
    remote_input
    on_reply
    on_read
    participants
    timestamp
```

同一个方法在 Android 10+ 还显式关闭微信原通知的 system-generated contextual actions。因此，系统自动建议动作也不能补出微信的已读入口。

### 2. Android Auto 能力生成条件

`uk1.a.b(talker, displayName)` 依次检查：

1. `clicfg_android_auto` 配置开启；
2. `UiModeManager.getCurrentModeType() == UI_MODE_TYPE_CAR`；
3. USB host 设备的第一个 interface class 为 audio；
4. `com.google.android.projection.gearhead` 的签名 MD5 与微信内置值匹配。

任一条件失败就返回 `null`，普通通知不会包含 `on_read` 或 `on_reply`。

条件满足时，微信用内部 talker ID 创建两个由微信持有的广播 `PendingIntent`：

```text
com.tencent.mm.permission.MM_AUTO_HEARD_MESSAGE
com.tencent.mm.permission.MM_AUTO_REPLY_MESSAGE
extra: key_username=<微信内部 talker ID>
```

### 3. 为什么普通应用不能直接发相同广播

微信 manifest 中两个接收器都是：

```xml
<receiver
    android:name="com.tencent.mm.plugin.auto.service.MMAutoMessageHeardReceiver"
    android:exported="false" />

<receiver
    android:name="com.tencent.mm.plugin.auto.service.MMAutoMessageReplyReceiver"
    android:exported="false" />
```

所以 WeModern 不能通过显式或隐式 broadcast 直接调用它们。只有微信自己创建并交出的 `PendingIntent` 能把这项权限委托给通知消费者。

此外，当前通知只把精确会话 ID 放在微信自己的 opaque `contentIntent` 或内部构建参数中。WeModern 可调用这个 `PendingIntent`，但不能读取其底层 Intent 和 `key_username`，也不能仅靠显示名可靠推断内部 talker ID。

### 4. `on_read` 确实修改微信未读状态

`MMAutoMessageHeardReceiver.onReceive()` 在再次通过 Android Auto 条件检查后调用：

```text
ConversationStorage.d0(key_username)
```

反编译后的方法日志名为 `updateUnreadByTalker`，会更新微信自己的 `rconversation` 记录，包括：

```text
unReadCount = 0
unReadMuteCount = 0
UnReadInvite = 0
atCount = 0
```

并发送会话存储变更通知。这属于微信内部的真实会话已读状态，不是简单移除系统通知。

需要保留一个边界：静态代码证明了本机微信数据库与 UI 未读状态会被更新，但本次没有连接车机执行该分支，尚未验证它是否同步到微信的其他登录设备。微信也没有面向聊天对方显示“已读回执”的常规产品语义。

## Nevolution 原实现现状

Nevolution 的微信 decorator 没有自行实现微信“已读”协议，它只是转发原通知 `CarExtender.UnreadConversation` 中的能力令牌：

1. 从微信通知提取 `replyPendingIntent`、`readPendingIntent` 和 `RemoteInput`；
2. 将非空的 `readPendingIntent` 按通知 key 缓存在内存中；
3. 用户滑动移除通知、收到 `REASON_CANCEL` 时，直接执行该 `readPendingIntent`；
4. 快速回复则先经过 Nevolution 自己的广播代理，再附加 RemoteInput 结果并执行微信的 `replyPendingIntent`。

源码：

- [提取 `on_reply` / `on_read`](https://github.com/Nevolution/decorator-wechat/blob/14c807aa87eba2afef3697cd95ae7b89476e1bf2/src/main/java/com/oasisfeng/nevo/decorators/wechat/MessagingBuilder.kt#L114-L141)
- [回复代理与 `markRead()`](https://github.com/Nevolution/decorator-wechat/blob/14c807aa87eba2afef3697cd95ae7b89476e1bf2/src/main/java/com/oasisfeng/nevo/decorators/wechat/MessagingBuilder.kt#L148-L210)
- [滑动通知时调用 `markRead()`](https://github.com/Nevolution/decorator-wechat/blob/14c807aa87eba2afef3697cd95ae7b89476e1bf2/src/main/java/com/oasisfeng/nevo/decorators/wechat/WeChatDecorator.kt#L211-L220)

### 回复失效不等于已读执行逻辑也失效

Nevolution 的回复代理 `PendingIntent` 使用 `FLAG_IMMUTABLE`。现代 Android 的 RemoteInput 流程要求用于接收输入结果的 `PendingIntent` 可变，否则系统无法把回复文本加入 fill-in Intent。这是快速回复可以单独失效的一个明确兼容性问题。标记已读不携带 RemoteInput，并且 Nevolution 直接发送微信原始 `readPendingIntent`，不受这个具体问题影响。

但这只说明两个失败点不同，并不代表普通手机上的标记已读仍然可用。

### 当前普通模式下，Nevolution 的标记已读也实际上不可用

微信 8.0.69 在创建 `readPendingIntent` 之前增加了前述真实车机条件；执行接收器时又检查一次。当前普通手机通知根本不带 `CarExtender.UnreadConversation.on_read`，所以 Nevolution 的 `mMarkReadPendingIntents` 中没有可发送的动作，滑动通知最终只会清除 SystemUI 通知。

Nevolution 早期的扩展包也无法补足当前条件。对仓库历史中的 `dummy-auto.apk` 检查结果为：

```text
package = com.google.android.projection.gearhead
versionName = Stub
signer MD5 = 27b1c70e1f7bdf1fafcf160f27b80f7f
```

而微信 8.0.69 的 `AutoLogic.isInstallAutoApp()` 要求同包名签名摘要等于微信内置的另一个值；即使包检查通过，当前版本仍要求车机 UI mode 和 USB audio/AOAP 设备。因此，“仅安装 Android Auto”或安装 Nevolution 扩展包的旧方法不再足够。

更准确的结论是：

- **普通手机 + Nevolution/扩展包：标记已读不可用。**
- **真实 Android Auto 连接期间：微信仍保留 `on_read` 实现，静态代码看应可用；本次尚未做车机实测。**
- **快速回复失效不能作为车机 `on_read` 也失效的直接证据，因为两者后半段执行链不同。**

## 可考虑的实现路线

### 路线 A：能力检测式 Android Auto 转发

这是唯一不修改微信、且使用微信自己授权能力的路线。

WeModern 在收到原通知时可以检查：

```text
extras["android.car.EXTENSIONS"]
    ["car_conversation"]
    ["on_read"]
```

仅当 `on_read` 是由 `com.tencent.mm` 创建的有效 `PendingIntent` 时，在重写通知上增加“标为已读”按钮，并直接使用该 `PendingIntent`：

```java
new Notification.Action.Builder(icon, "标为已读", onRead)
        .setSemanticAction(Notification.Action.SEMANTIC_ACTION_MARK_AS_READ)
        .setShowsUserInterface(false)
        .build();
```

限制：

- 普通手机模式下不会显示按钮；
- 点击时必须仍处于真实 Android Auto 条件，否则微信接收器静默 no-op；
- `PendingIntent.send()` 成功只代表系统完成投递，没有微信侧成功回执；
- 必须只响应明确的用户点击，不能在收到消息后自动标为已读。

它适合作为窄场景实验，不解决当前普通使用场景。

### 路线 B：root / LSPosed 研究模块

在受控设备中，可在微信进程内：

- 捕获通知构建时的真实 talker ID；
- 注入一个由微信进程处理的 mark-read action；
- 或调用与 `ConversationStorage.d0(talker)` 等价的内部路径。

这可以满足“不打开微信 UI + 真已读”，但依赖高权限和微信私有实现，存在版本兼容、账号安全与分发合规风险，不应放入普通 WeModern APK。

### 路线 C：Accessibility UI 自动化

辅助功能可打开精确聊天、等待微信将会话置为已读，再返回原界面。它会实际启动微信 Activity，存在可见跳转、锁屏失败、UI 文本变化和误操作风险，不满足本需求的核心约束。

## 建议

1. 普通发布版不要提供一个实际上只清通知的“标为已读”按钮，避免产生错误语义。
2. 如果要继续实验，先实现只记录布尔元数据的诊断：`actions.length`、是否存在 `car_conversation`、`on_read`/`on_reply` 是否非空、`PendingIntent` creator package；不要记录消息正文或 talker ID。
3. 有真实 Android Auto/USB audio 环境时，再验证 `on_read` 转发是否同时清除微信会话红点、桌面角标和 WeModern 重写通知。
4. 若产品要求是普通手机模式也必须无跳转真已读，则需要微信官方配合或单独的 root/LSPosed 研究版本；当前 Android 公共 API 无法满足。
