# WeModern 接入微信 Direct Share 可行性分析

- 日期：2026-07-15
- 测试设备：Pixel 9 Pro，Android 17（API 37）
- 微信版本：8.0.69
- 测试场景：Google Photos 分享单张 JPEG 图片，经微信发送给联系人 JASPR

## 结论摘要

WeModern 可以接入 Android 标准 Direct Share：在系统分享面板展示自己的会话分享目标、接收图片，并将内容转交给微信。这个部分在 Android 平台层面可实现。

但是，把某个 WeModern 会话目标稳定映射为“微信的指定联系人”，目前缺少关键数据：微信 internal username（例如 `wxid_...` 一类内部会话标识）或 OpenSDK 的 `userOpenId`。本次实机检查确认：当前微信通知、系统 activity/intent dump 和普通 logcat 都没有暴露该标识；WeModern 现有代码也只能保存并触发微信通知携带的不可解析 `PendingIntent`，不能从中读取目标联系人 ID。

因此当前能力边界是：

| 能力 | 当前状态 | 说明 |
| --- | --- | --- |
| 在 Android Sharesheet 展示 WeModern Direct Share 目标 | 可实现 | 使用 Sharing Shortcuts 与 `<share-target>` 声明 |
| WeModern 接收 Google Photos 分享的图片 | 可实现 | 接收 `ACTION_SEND`、`image/*` 和临时 URI 权限 |
| 打开某条微信通知对应的精确聊天 | 已具备 | 保存并调用通知中的 `contentIntent` |
| 从通知获取微信 internal username | 当前不可行 | 通知只提供显示名、正文等；`PendingIntent` 不可解析 |
| 从当前 logcat 获取 internal username | 当前不可行 | 已有 `READ_LOGS`，但日志只显示组件和 `(has extras)` |
| 通过微信私有 Intent 直接指定联系人 | 条件可行、不可产品化 | 需要先取得 internal username，并受微信版本变化影响 |
| 通过微信 OpenSDK 指定联系人 | 条件可行 | 需要获批 AppID，并解决目标 `userOpenId` 的合法来源和映射 |

## 实机分享链路

操作路径为：

```text
Google Photos 当前照片
  -> Share
  -> Android Sharesheet
  -> WeChat
  -> 微信联系人选择页
  -> JASPR
  -> 微信发送确认页
  -> Send
  -> JASPR 聊天页
```

系统 activity 日志观察到以下关键跳转：

```text
ACTION_SEND image/jpeg
  -> com.tencent.mm/.ui.tools.ShareImgUI

ACTION_SEND content://com.google.android.apps.photos.contentprovider/...
  -> com.tencent.mm/.ui.transmit.MsgRetransmitUI

  -> com.tencent.mm/.ui.mvvm.MvvmContactListUI
  -> com.tencent.mm/.ui.halfscreen.HalfScreenTransparentActivity
  -> com.tencent.mm/.ui.chatting.ChattingUI
```

Google Photos 交给微信的 Intent 带有 `content://` 图片 URI、`image/jpeg` MIME type 和临时 URI grant。日志只能显示 `(has extras)`，不会打印 extras 的键值。

本机 Sharesheet 的 Direct Share 行显示了其他应用的联系人，但没有微信联系人；微信只出现在普通应用目标行。微信当前发布的系统快捷方式只有扫码、收付款和我的二维码，没有发现 Sharing Shortcut 或 conversation share target。

## Android 标准 Direct Share 接入

Android 11 及以上应使用 Sharing Shortcuts 提供 Direct Share 目标。目标应用需要：

1. 声明能接收目标 MIME type 的分享 Activity。
2. 在 XML 中通过 `<share-target>` 将 MIME type 与自定义 shortcut category 关联。
3. 为最近会话发布带相同 category 的动态快捷方式。
4. 收到分享时读取 `Intent.EXTRA_SHORTCUT_ID`，确定用户点选了哪个会话目标。
5. 接收并继续传递 `ClipData`、`EXTRA_STREAM` 和 URI 读取权限。

WeModern 目前发布的是启动器/会话快捷方式，category 仅为 `android.shortcut.conversation`；项目中没有 `<share-target>` 配置。因此现有会话快捷方式不会自动进入 Sharesheet 的 Direct Share 行。

标准接入可以解决“用户在 Sharesheet 中选择 WeModern 的 JASPR 目标”和“图片进入 WeModern”两个问题，但不能自动解决“WeModern 如何告诉微信目标是 JASPR”。后一步仍然需要微信认可的接收方标识。

## internal username 获取能力

### 1. 从微信通知获取

当前微信消息通知可观察到的内容包括：

- 通知 ID、tag、key、channel、flags；
- 标题和正文；
- 大小图标；
- 可执行的 `contentIntent`；
- 本次捕获的通知没有可用 `shortcutId` 或联系人内部 ID。

WeModern 当前使用通知标题构造本地会话键：

```text
conversationKey = "wechat:" + title
```

这个键只是 WeModern 自己的显示名映射，不是微信 internal username。显示名还可能重复、被修改或因群聊/语言环境变化，不能作为微信私有接口的稳定 ID。

微信通知的 `contentIntent` 能打开精确聊天，但它是微信创建、由系统托管的能力令牌。WeModern 可以调用 `PendingIntent.send()`，却没有受支持的 API 读取其底层 Intent extras。本机观察到该 `PendingIntent` 为 immutable；`dumpsys activity intents` 只展示目标组件为微信聊天 Activity 以及 `(has extras)`，没有展示 `Chat_User` 或具体值。proto dump 同样未发现 `Chat_User`、`Select_Conv_User` 或 `wxid_`。

结论：**当前不能通过通知直接获得 internal username。** 能打开目标聊天不代表能提取目标 ID。

### 2. 从系统 logcat 获取

设备已为 WeModern 授予 `android.permission.READ_LOGS`，并允许敏感通知访问。现有 `READ_LOGS` 用途是监听 Android `NotificationService` 输出，以同步通知移除事件；它不会扩大 `PendingIntent` 的可见性，也不会授予微信进程或私有文件访问权。

本次在完整 logcat、`ActivityTaskManager` 和 WeModern 自身日志中均未找到目标 internal username。系统启动 Activity 的日志只记录包名、组件、action、type、flags 和 `(has extras)`。微信 release 构建也没有把所需联系人标识以明文写入普通 logcat。

结论：**即使当前具有 `READ_LOGS`，也不具备从现有 logcat 获得 internal username 的能力。** 若未来某个微信版本偶然打印该值，这只会是版本相关的信息泄漏，不能作为稳定产品接口。

### 3. 从微信 xlog 获取

设备外部存储中可以通过 ADB shell 看到微信的 `MicroMsg/xlog` 文件，但存在三层限制：

- xlog 是微信私有的二进制日志格式，本次检查不是可直接搜索的明文；
- Android scoped storage 下，普通 WeModern 进程不能读取另一个应用的 `Android/data` 目录；
- 即使使用 ADB 拉取并成功解码，也尚未证明当前分享链路一定记录了联系人 internal username。

因此 xlog 只能被视作后续 ADB 取证研究方向，不是 WeModern 当前的运行时能力，也不应成为 Direct Share 的产品依赖。

### 4. 当前能力的准确表述

WeModern 当前具有：

- 读取允许暴露给通知监听器的微信通知字段；
- 将显示名映射为自己的 `wechat:<title>` 会话键；
- 在进程内缓存微信通知的 opaque `PendingIntent`；
- 调用该 `PendingIntent` 打开原通知对应的聊天。

WeModern 当前不具有：

- 从通知或 `PendingIntent` 反解微信 internal username；
- 从 logcat 稳定读取微信联系人 ID；
- 读取微信私有数据库、进程内存或应用私有日志；
- 将显示名可靠转换为 `wxid`/internal username 或 `userOpenId`。

## 微信侧可选路径

### 路径 A：普通微信分享

WeModern 接收 Direct Share 后，用公开的 `ACTION_SEND` 将图片交给微信。微信继续显示联系人选择页和发送确认页。

- 优点：最稳定，符合系统与微信公开能力边界。
- 缺点：用户仍需在微信内选择 JASPR，不能实现真正的一键定向分享。
- 结论：可产品化，推荐作为第一阶段。

### 路径 B：微信 OpenSDK 指定联系人

微信 OpenSDK 的 `SendMessageToWX.Req` 定义了 `WXSceneSpecifiedContact`，同时有 `userOpenId` 和应用 `openId` 字段。这表明 SDK 在协议层面存在指定联系人的能力，但真正接入需要确认：

- WeModern 是否能申请并通过对应能力审核；
- 目标联系人 `userOpenId` 通过何种官方流程获得；
- `userOpenId` 是否只对特定开放平台 AppID 有效；
- 图片、确认页和隐私要求是否满足微信当前政策。

在官方身份和 ID 获取链路验证以前，这条路径只能标记为“条件可行”，不能根据类字段存在就判断产品可用。

### 路径 C：微信私有 Intent

对微信 8.0.69 的静态检查发现，内部转发页识别 `Select_Conv_User`、`scene_from`、`Retr_MsgQuickShare` 等私有 extras。若已知 internal username，当前版本技术上可能预选目标，甚至触发更短的转发流程。

这条路径不适合作为正式能力：

- internal username 当前没有可靠来源；
- Activity、extra 名称和行为随微信版本变化；
- 可能绕过或改变微信预期的用户确认流程；
- 私有组件可随时改为不导出或增加签名/来源校验；
- 错误映射存在把内容发给错误联系人的严重隐私风险。

即使做实验，也应保留微信确认页、校验版本、默认失败关闭，并禁止仅凭显示名猜测联系人 ID。

### 路径 D：root、hook 或辅助功能

root/Xposed/Frida 等方式理论上可观察微信进程内的 Intent extras 或数据库映射；辅助功能可能通过界面文本自动选择联系人。但它们分别依赖高权限、破坏平台安全边界或产生脆弱的 UI 自动化，不属于普通商店应用的可部署方案，只适合受控研究设备。

## 建议实施顺序

1. 先实现标准 Sharing Shortcuts 和分享接收 Activity，让 WeModern 会话出现在 Sharesheet，并能安全接收/转发图片。
2. 第一版仍走微信普通 `ACTION_SEND`，明确接受微信联系人选择与发送确认步骤。
3. 单独做 OpenSDK spike，以“能否通过官方方式获得目标 `userOpenId`”作为 go/no-go 条件。
4. 私有 Intent 只保留为实验性验证，不与展示名绑定，不默认开启，也不作为正式架构前提。
5. 如果继续研究 internal username，优先做一次受控的 ADB/xlog 取证实验；结果必须区分“某版本调试时能看到”和“普通 WeModern 运行时可稳定获取”。

## 代码与证据位置

- `app/src/main/java/me/jaspr/wemodern/WeChatNotificationService.java`
  - 新消息处理会把原通知 `contentIntent` 交给 `ConversationShortcuts.publish()` 缓存。
  - `restoreShortcutContentIntent()`：仅在替换通知有 `shortcutId` 时恢复对应的 `contentIntent`。
  - `logWeChatPosted()`：当前只记录通知元数据、标题和正文。
  - `ParsedNotification.parse()`：使用通知标题生成 `wechat:<title>`。
- `app/src/main/java/me/jaspr/wemodern/ConversationShortcuts.java`
  - `CONTENT_INTENTS`：只在内存缓存 opaque `PendingIntent`。
  - `openConversation()`：只调用 `PendingIntent.send()`。
  - 快捷方式 category 当前仅为 `SHORTCUT_CATEGORY_CONVERSATION`。
- `app/src/main/AndroidManifest.xml`
  - 已声明通知监听、`READ_LOGS` 和敏感通知权限。
- `README.md`
  - 已记录通过 ADB 授予 `READ_LOGS` 的设备配置步骤。

## 参考资料

- [Android PendingIntent 安全说明](https://developer.android.com/privacy-and-security/risks/pending-intent)
- [Android 日志信息泄漏说明](https://developer.android.com/privacy-and-security/risks/log-info-disclosure)
- [Android Sharing Shortcuts / Direct Share](https://developer.android.com/training/sharing/receive#sharing-shortcuts)
- [微信 OpenSDK `SendMessageToWX.Req` API](https://javadoc.io/static/com.tencent.mm.opensdk/wechat-sdk-android-without-mta/6.7.0/com/tencent/mm/opensdk/modelmsg/SendMessageToWX.Req.html)

## 最终判断

**当前 WeModern 不具备通过通知或日志获取微信 internal username 的能力。** 已具备的是对微信提供的精确会话 `PendingIntent` 的调用能力，而不是读取或转换联系人身份的能力。

Direct Share 的 Android 入口可以实现；真正的“定向到微信 JASPR”只有在通过官方 OpenSDK 获得有效的目标标识后才适合产品化。私有 Intent 加 internal username 在当前微信版本上存在实验可能，但 ID 来源、兼容性、安全性和合规性均未达到正式接入条件。
