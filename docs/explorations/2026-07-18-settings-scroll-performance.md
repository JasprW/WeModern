# WeModern 设置页滚动性能检查

- 日期：2026-07-18
- 状态：已完成针对性优化与真机对比
- 设备：Pixel 9 Pro，Android API 37，Debug APK
- 工具：Android CLI 结构化布局、ADB 合成滚动、`dumpsys gfxinfo`

## 用户现象

打开 WeModern 设置页后滚动时会感觉到轻微卡顿。检查目标是确认是否存在持续性的 GPU/绘制瓶颈、滚动期间状态重读，或 Compose lazy item 结构导致的首次组合尖峰，并在不改变页面内容和交互的前提下优化。

## 优化前基线

在启动后先完整预热滚动，再重置 `gfxinfo`，执行 5 次向下和 5 次向上滚动：

- 533 个渲染帧。
- 6 个 FrameTimeline deadline miss，jank 比例 1.13%。
- UI 帧耗时 P50 6 ms、P90 9 ms、P95 11 ms、P99 34 ms。
- 出现 1 个约 300 ms 的极端帧。
- GPU P99 仅 3 ms，没有慢 bitmap upload；主要压力在 UI/布局线程，不在 GPU。

ADB 合成手势会产生大量 high-input-latency 标记，Pixel 也会动态切换渲染刷新率，因此这些数字用于同设备相对比较，不等同于人工触摸或 Release APK 的绝对帧率。

## 代码检查结论

1. 页面使用 `LazyColumn`，但 Setup 和 Bubbles 原先各自只占一个 lazy item；每个 item 内再用普通 `Column` 包含多张卡片。超高 item 进入预取或可见区域时，其全部子项会在同一批次组合和布局，削弱了 lazy 容器分摊工作的能力。
2. 滚动状态没有触发 `readSetupState()`，也没有在 item 中同步读文件、解析 JSON 或加载头像；没有发现每帧状态读取问题。
3. GPU 帧耗时稳定，Surface、图标和颜色绘制不是主要瓶颈。
4. 页面内容是有限的静态设置项，适合为单行/单卡片提供稳定 key 和准确 content type，以便 Compose 保存状态并复用相同结构的 slot。

## 保留的优化

- 将 Setup header、每个 setup row、Bubbles header、每个 bubble switch、host action 和 defaults card 拆成独立 lazy item。
- 为每个 item 添加稳定 key；只让结构相同的设置行或 switch card 共享 content type，Advanced、Debug 和 Tests 使用各自类型。
- 通过 `LazyLayoutCacheWindow` 配置 1.5 个 viewport 的前向预取和 0.5 个 viewport 的后向保留，降低快速折返时的重新组合。
- 保持优化前的页面顺序、行间距、圆角、字体、开关状态和点击行为；结构化布局检查的关键坐标与优化前一致。

## 对比结果

相同预热滚动脚本下，优化后多轮结果为：

| 轮次 | 帧数 | Deadline miss | Jank | P50 | P90 | P95 | P99 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 526 | 7 | 1.33% | 6 ms | 10 ms | 12 ms | 21 ms |
| 2 | 560 | 7 | 1.25% | 5 ms | 9 ms | 11 ms | 20 ms |
| 3 | 554 | 1 | 0.18% | 5 ms | 8 ms | 10 ms | 15 ms |

小幅 jank 比例会受合成手势与设备动态刷新影响，但 P99 从基线 34 ms 降至 15–21 ms，且没有再次出现 300 ms 极端帧，说明首次进入大型区块的尾部尖峰得到改善。

## 尝试但未保留

### 改用系统默认正文/标题字体

曾保留 section display font、把其余 Material typography 从 bundled Google Sans Flex 改为系统默认字体，以验证变量字体是否是首屏滚动瓶颈。冷滚动对照中 P50 反而升至 7 ms、P99 为 34 ms，没有改善，因此已完整撤回；当前字体与优化前一致。

### 强制 ART `speed` 编译

曾在设备上执行一次 `cmd package compile -m speed` 排除 Debug JIT 影响，但单次结果受安装、缓存和动态刷新率影响较大，不能证明稳定收益。后续重新安装 APK 已替换该临时设备状态；它不是产品修复的一部分。

### 仅依赖预热后的数据

拆分 item 后预热数据明显改善，但冷路径仍有较大测量波动。因此最终又增加明确 cache window，并保留本文的冷/热限制说明，没有把某一轮最好结果当作帧率保证。

## 当前边界

- 当前验证对象是 Debug APK；Compose 调试检查、JIT 和 ADB 输入都会放大波动。
- 页面没有持续 GPU 过载，剩余偶发帧更接近首次文字绘制、运行时编译和系统刷新率切换。
- 若人工触摸仍能稳定复现明显卡顿，下一步应使用 Macrobenchmark/Baseline Profile 和 Perfetto composition tracing，而不是继续删除视觉样式或盲目扩大缓存。
