# WeModern Privacy Policy

Last updated: 2026-09-14

WeModern exists to redraw an app's notifications on modern Android. To do that
it has to be able to read notifications, which is the most sensitive permission
on Android. This document states exactly what it reads, what it keeps, and
where that data goes.

## Summary

- **Nothing leaves your device.** WeModern declares no `INTERNET` permission,
  so it has no network capability at all. You can verify this on your own copy:
  `adb shell dumpsys package me.jaspr.wemodern | grep -i permission` reports no
  `android.permission.INTERNET`.
- **No analytics, no ads, no accounts, no telemetry, no crash reporting.**
- **A release build stores no notification content by default.** Notifications
  are read in memory, rewritten, and posted back. The optional capture log
  (below) is off unless you turn it on yourself.
- **No data is ever sold, shared, or transferred to a third party.**

## What WeModern accesses

| Access | Why it is needed | When |
| --- | --- | --- |
| `NotificationListenerService` (notification access) | To read an incoming notification, rebuild it with conversation, bubble, and call styling, then post the rewritten notification and remove the original. | Always, while the service is enabled |
| `RECEIVE_SENSITIVE_NOTIFICATIONS` | To read notifications on lock screen / from apps flagged sensitive, which Android otherwise redacts. | Only if you grant it in system settings |
| `READ_LOGS` (debug build or your explicit `adb` grant) | To notice when the source app removes a notification, so the rewritten one can be removed in sync. Optional; the app works without it. | Only if granted |
| `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE*`, `POST_PROMOTED_NOTIFICATIONS` | To post the rewritten notifications and calls, and to keep the listener alive. | Always |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Optional, so the listener is not killed in the background. | Only if you accept the system prompt |

Notification content is processed in memory on the device. Message text is never
written anywhere unless you enable capture logging.

## What is stored on the device

All of it is inside the app's private storage (`/data/data/me.jaspr.wemodern`),
unreadable by other apps, and deleted when you uninstall WeModern.

- **Conversation metadata** — conversation name, notification counts, bubble
  preferences, and cached sender avatars. Needed so shortcuts and bubbles can
  show the right conversation before the next notification arrives.
- **Settings** — your switches and theme choice.
- **Capture log (optional, off by default)** — when *Debug → Capture and
  logging* is on, WeModern writes every intercepted notification to a private
  JSONL file (`files/wechat_notification_capture.jsonl`, rotating at 16 MiB).
  This is a troubleshooting aid: it exists so notification-shape changes can be
  diagnosed with evidence. Turning the switch off stops writing. You can delete
  the file at any time:
  `adb shell run-as me.jaspr.wemodern rm files/wechat_notification_capture.jsonl`

## Permissions WeModern deliberately does not ask for

- No `INTERNET` — no network capability.
- No `READ_SMS`, contacts, storage, camera, microphone, or location access.
- No root, no Xposed/LSPosed modules, no memory scanning, no modification of
  the source app's data, files, or database.

## Children, jurisdiction, and change

WeModern is a local utility that collects no personal data, so there is nothing
to export, rectify, or erase on a server — there is no server. If this policy
ever changes, the change will be committed to this repository with the date
above.

## Contact

Open an issue at <https://github.com/JasprW/WeModern/issues>.

---

# WeModern 隐私政策

最后更新：2026-09-14

WeModern 的存在意义是在新版本 Android 上重新绘制某个应用的通知。要做到这一点，
它必须能够读取通知 —— 这是 Android 上最敏感的权限。本文说明它到底读什么、留什么、
数据去了哪里。

## 结论

- **数据不会离开你的设备。** WeModern 没有声明 `INTERNET` 权限，因此完全不具备
  联网能力。你可以自行核验：
  `adb shell dumpsys package me.jaspr.wemodern | grep -i permission`
  输出中没有 `android.permission.INTERNET`。
- **没有统计、没有广告、没有账号、没有遥测、没有崩溃上报。**
- **正式版默认不保存任何通知内容。** 通知在内存中读取、重绘、重新发布。
  可选的采集日志（见下）默认关闭，只有你自己打开才会写盘。
- **任何数据都不会被出售、共享或转移给第三方。**

## WeModern 会接触什么

| 权限 / 能力 | 为什么需要 | 何时使用 |
| --- | --- | --- |
| `NotificationListenerService`（通知使用权） | 读取新通知，用会话、气泡、通话样式重绘，再发布重写后的通知并移除原通知 | 服务启用期间始终 |
| `RECEIVE_SENSITIVE_NOTIFICATIONS` | 读取锁屏上或被标记为敏感的通知（系统默认会遮挡这些内容） | 仅在你于系统设置中授权后 |
| `READ_LOGS`（调试版或你手动 `adb` 授权） | 感知源应用撤回了哪条通知，以便同步移除重写后的那条。可选，不给也能用 | 仅在你授权后 |
| `POST_NOTIFICATIONS`、`FOREGROUND_SERVICE*`、`POST_PROMOTED_NOTIFICATIONS` | 发布重写后的通知与通话，并保持监听服务存活 | 始终 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 可选，避免监听服务被后台清理 | 仅在你同意系统提示后 |

通知内容全程在设备内存中处理。除非你打开采集日志，消息正文不会被写入任何位置。

## 设备上会保存什么

全部位于应用私有目录（`/data/data/me.jaspr.wemodern`），其他应用无法读取，
卸载 WeModern 即被删除。

- **会话元数据** —— 会话名称、通知计数、气泡偏好、缓存的发件人头像。用于让快捷方式
  与气泡在下一条通知到来之前就能显示正确的会话。
- **设置项** —— 你的开关状态与主题选择。
- **采集日志（可选，默认关闭）** —— 打开 *Debug → 采集并记录* 后，WeModern 会把
  拦截到的每条通知写入私有 JSONL 文件
  （`files/wechat_notification_capture.jsonl`，写满 16 MiB 轮转）。它是排障工具：
  为了让"通知结构变化"这类问题能凭证据定位。关掉开关即停止写入，文件也可随时删除：
  `adb shell run-as me.jaspr.wemodern rm files/wechat_notification_capture.jsonl`

## WeModern 刻意不申请的权限

- 没有 `INTERNET` —— 不具备联网能力。
- 不读取短信、通讯录、存储、相机、麦克风、位置。
- 不要求 root，不使用 Xposed/LSPosed，不扫描进程内存，不修改源应用的数据、文件或数据库。

## 变更与联系

WeModern 是本地工具，不收集个人数据，因此没有服务器端数据可供导出、更正或删除 ——
它没有服务器。如果本政策发生变化，改动会连同日期一起提交到本仓库。

有问题请提 issue：<https://github.com/JasprW/WeModern/issues>

---

# WeModern 隱私政策

最後更新：2026-09-14

WeModern 的存在意義是在新版本 Android 上重新繪製某個應用的通知。要做到這一點，
它必須能讀取通知 —— 這是 Android 上最敏感的權限。本文說明它實際讀取什麼、保留什麼、
資料去了哪裡。

## 結論

- **資料不會離開你的裝置。** WeModern 未宣告 `INTERNET` 權限，因此完全不具備
  連網能力。你可以自行核驗：
  `adb shell dumpsys package me.jaspr.wemodern | grep -i permission`
  輸出中沒有 `android.permission.INTERNET`。
- **沒有統計、沒有廣告、沒有帳號、沒有遙測、沒有當機回報。**
- **正式版預設不保存任何通知內容。** 通知在記憶體中讀取、重繪、重新發佈。
  可選的擷取日誌（見下）預設關閉，只有你自己開啟才會寫入。
- **任何資料都不會被出售、共享或轉移給第三方。**

## WeModern 會接觸什麼

| 權限 / 能力 | 為什麼需要 | 何時使用 |
| --- | --- | --- |
| `NotificationListenerService`（通知使用權） | 讀取新通知，以會話、泡泡、通話樣式重繪，再發佈重寫後的通知並移除原通知 | 服務啟用期間始終 |
| `RECEIVE_SENSITIVE_NOTIFICATIONS` | 讀取鎖定畫面上或被標記為敏感的通知（系統預設會遮蔽這些內容） | 僅在你於系統設定中授權後 |
| `READ_LOGS`（除錯版或你手動 `adb` 授權） | 感知來源應用撤回了哪條通知，以便同步移除重寫後的那條。可選，不給也能用 | 僅在你授權後 |
| `POST_NOTIFICATIONS`、`FOREGROUND_SERVICE*`、`POST_PROMOTED_NOTIFICATIONS` | 發佈重寫後的通知與通話，並讓監聽服務存活 | 始終 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 可選，避免監聽服務被背景清理 | 僅在你同意系統提示後 |

通知內容全程在裝置記憶體中處理。除非你開啟擷取日誌，訊息內容不會被寫入任何位置。

## 裝置上會保存什麼

全部位於應用私有目錄（`/data/data/me.jaspr.wemodern`），其他應用無法讀取，
解除安裝 WeModern 即被刪除。

- **會話中繼資料** —— 會話名稱、通知計數、泡泡偏好、快取的傳送者頭像。用於讓捷徑
  與泡泡在下一則通知到來之前就能顯示正確的會話。
- **設定項目** —— 你的開關狀態與主題選擇。
- **擷取日誌（可選，預設關閉）** —— 開啟 *Debug → 擷取並記錄* 後，WeModern 會把
  攔截到的每則通知寫入私有 JSONL 檔案
  （`files/wechat_notification_capture.jsonl`，寫滿 16 MiB 輪替）。它是排障工具：
  為了讓「通知結構變更」這類問題能憑證據定位。關掉開關即停止寫入，檔案也可隨時刪除：
  `adb shell run-as me.jaspr.wemodern rm files/wechat_notification_capture.jsonl`

## WeModern 刻意不申請的權限

- 沒有 `INTERNET` —— 不具備連網能力。
- 不讀取簡訊、通訊錄、儲存空間、相機、麥克風、位置。
- 不要求 root，不使用 Xposed/LSPosed，不掃描行程記憶體，不修改來源應用的資料、檔案或資料庫。

## 變更與聯絡

WeModern 是本地工具，不收集個人資料，因此沒有伺服器端資料可供匯出、更正或刪除 ——
它沒有伺服器。如果本政策有所變更，改動會連同日期一起提交到本倉庫。

有問題請開 issue：<https://github.com/JasprW/WeModern/issues>
