---
name: WeModern
description: A lively, native Android utility for understanding and controlling modernized WeChat notifications.
colors:
  primary: "dynamic(MaterialTheme.colorScheme.primary)"
  on-primary: "dynamic(MaterialTheme.colorScheme.onPrimary)"
  primary-container: "dynamic(MaterialTheme.colorScheme.primaryContainer)"
  on-primary-container: "dynamic(MaterialTheme.colorScheme.onPrimaryContainer)"
  secondary-container: "dynamic(MaterialTheme.colorScheme.secondaryContainer)"
  on-secondary-container: "dynamic(MaterialTheme.colorScheme.onSecondaryContainer)"
  tertiary: "dynamic(MaterialTheme.colorScheme.tertiary)"
  on-tertiary: "dynamic(MaterialTheme.colorScheme.onTertiary)"
  tertiary-container: "dynamic(MaterialTheme.colorScheme.tertiaryContainer)"
  on-tertiary-container: "dynamic(MaterialTheme.colorScheme.onTertiaryContainer)"
  surface: "dynamic(MaterialTheme.colorScheme.surface)"
  on-surface: "dynamic(MaterialTheme.colorScheme.onSurface)"
  on-surface-variant: "dynamic(MaterialTheme.colorScheme.onSurfaceVariant)"
  surface-container-low: "dynamic(MaterialTheme.colorScheme.surfaceContainerLow)"
  surface-container: "dynamic(MaterialTheme.colorScheme.surfaceContainer)"
  surface-container-highest: "dynamic(MaterialTheme.colorScheme.surfaceContainerHighest)"
  outline-variant: "dynamic(MaterialTheme.colorScheme.outlineVariant)"
  error: "dynamic(MaterialTheme.colorScheme.error)"
typography:
  display:
    fontFamily: "Google Sans Flex"
    fontSize: "28sp"
    fontWeight: 760
    lineHeight: "36sp"
    letterSpacing: "0sp"
    fontVariation: "wght 760, wdth 112, opsz 32, ROND 100"
  headline:
    fontFamily: "Google Sans Flex"
    fontSize: "24sp"
    fontWeight: 700
    lineHeight: "32sp"
    letterSpacing: "0sp"
    fontVariation: "ROND 100"
  title:
    fontFamily: "Google Sans Flex"
    fontSize: "16sp"
    fontWeight: 600
    lineHeight: "24sp"
    letterSpacing: "0.15sp"
    fontVariation: "ROND 100"
  body:
    fontFamily: "Google Sans Flex"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: "24sp"
    letterSpacing: "0.5sp"
    fontVariation: "ROND 100"
  supporting:
    fontFamily: "Google Sans Flex"
    fontSize: "12sp"
    fontWeight: 400
    lineHeight: "16sp"
    letterSpacing: "0.4sp"
    fontVariation: "ROND 100"
  label:
    fontFamily: "Google Sans Flex"
    fontSize: "14sp"
    fontWeight: 500
    lineHeight: "20sp"
    letterSpacing: "0.1sp"
    fontVariation: "ROND 100"
rounded:
  grouped-inner: "12dp"
  compact: "18dp"
  icon-container: "17dp"
  conversation: "20dp"
  card: "24dp"
  grouped-outer: "28dp"
  hero: "32dp"
  full: "999dp"
spacing:
  text-stack: "3dp"
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  xl: "20dp"
  xxl: "24dp"
  section: "28dp"
  sheet: "32dp"
components:
  setup-hero:
    typography: "{typography.display}"
    rounded: "{rounded.hero}"
    padding: "24dp"
  setup-hero-all-set:
    backgroundColor: "{colors.primary-container}"
    textColor: "{colors.on-primary-container}"
    typography: "{typography.display}"
    rounded: "{rounded.hero}"
    padding: "24dp"
  setup-hero-attention:
    backgroundColor: "{colors.tertiary-container}"
    textColor: "{colors.on-tertiary-container}"
    typography: "{typography.display}"
    rounded: "{rounded.hero}"
    padding: "24dp"
  setup-hero-off:
    backgroundColor: "{colors.surface-container-highest}"
    textColor: "{colors.on-surface}"
    typography: "{typography.display}"
    rounded: "{rounded.hero}"
    padding: "24dp"
  grouped-setting-row-first:
    backgroundColor: "{colors.surface-container-low}"
    textColor: "{colors.on-surface}"
    typography: "{typography.title}"
    rounded: "28dp 28dp 12dp 12dp"
    padding: "18dp 20dp"
  grouped-setting-row-middle:
    backgroundColor: "{colors.surface-container-low}"
    textColor: "{colors.on-surface}"
    typography: "{typography.title}"
    rounded: "{rounded.grouped-inner}"
    padding: "18dp 20dp"
  grouped-setting-row-last:
    backgroundColor: "{colors.surface-container-low}"
    textColor: "{colors.on-surface}"
    typography: "{typography.title}"
    rounded: "12dp 12dp 28dp 28dp"
    padding: "18dp 20dp"
  switch-card:
    backgroundColor: "{colors.surface-container-low}"
    textColor: "{colors.on-surface}"
    typography: "{typography.title}"
    rounded: "{rounded.card}"
    padding: "20dp"
  icon-container:
    backgroundColor: "{colors.surface-container-highest}"
    textColor: "{colors.on-surface-variant}"
    rounded: "{rounded.icon-container}"
    size: "48dp"
  primary-action:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.label}"
    rounded: "{rounded.full}"
    padding: "14dp 20dp"
    height: "56dp"
  test-action:
    backgroundColor: "{colors.surface-container-highest}"
    textColor: "{colors.on-surface}"
    typography: "{typography.label}"
    rounded: "{rounded.full}"
    padding: "12dp 8dp"
    height: "56dp"
  test-button-group:
    rounded: "{rounded.full}"
    height: "56dp"
    width: "100%"
---

# Design System: WeModern

## Overview

**Creative North Star: "Lively System Companion / 灵动的系统伙伴"**

WeModern 面向这样一个具体场景：用户在日常室内或户外光线下短暂打开应用，只想快速确认通知改写是否正常、还缺哪项权限，以及下一步该做什么。界面必须跟随 Android 的系统主题和 Material You 动态色，在几秒内给出可靠答案，不要求用户理解实现细节。

设计气质是 reliable at the core、lively on the surface、precise in every state。Material 3 Expressive 是默认设计语言，不是完成基础界面后附加的装饰层。圆润的 Google Sans Flex、鲜明的 shape scale、会响应触摸的组件分组、动态 container role 和有方向感的 motion 共同让工具显得灵动、活泼，同时保持技术可信度。

Expressive 必须服务理解：重要状态可以通过形状、尺度、字体轴、容器层级和空间转换获得更鲜明的反馈；高频设置仍保持熟悉和高效。界面不能静止得像系统参数表，也不能热闹得像营销页面。

**Key Characteristics:**

- 先展示整体 readiness 和唯一的下一步动作，再展示设置细节。
- 充分使用 Material 3 Expressive 的 typography、shape、component 和 motion vocabulary，而不是只使用基础 Material 控件。
- 使用单列、最大宽度 600dp 的扫描路径，手机和大屏都保持清晰阅读顺序。
- 通过大小、圆角关系、形状变化和自适应组件分组建立活泼的视觉节奏，避免等大卡片堆叠。
- 通过语义色、图标和状态文字共同表达结果，绝不只依赖颜色。
- 区分 required、recommended 和 advanced，ADB 能力永远保持次级。
- 已完成状态保持紧凑，未完成状态才获得行动入口。
- 动效只解释状态变化，优先使用 Material expressive motion scheme，并服从系统动画时长设置。

**The Expressive by Default Rule.** 每个关键状态转换都必须获得一个有意义的 Expressive 响应，例如形状、尺度、字体轴、容器或空间运动；不能只瞬间替换文字和颜色。

**The One Fact Rule.** 每个状态只解释一次。标题、说明、尾标和主卡片不得重复同一句结论。

**The Native First Rule.** 优先使用 Android 与 Material 3 已有的设置、开关、底部面板、通知和气泡隐喻，不为风格重新发明标准 affordance。

## Colors

WeModern 不拥有固定品牌色系。色彩由 Material You 根据用户壁纸、系统浅色或深色主题动态生成，设计系统只规定语义角色和使用比例。

### Primary

- **Dynamic Primary:** 用于已就绪、成功、主要行动和当前启用状态。主状态卡在 All set 模式使用 `primaryContainer` 与 `onPrimaryContainer`，行动图标使用 `primary` 与 `onPrimary`。

### Secondary

- **Dynamic Secondary:** 用于辅助身份、头像 fallback、轻量提示容器和不应与主要行动竞争的强调。不得把 secondary 当作第二品牌色大面积铺陈。

### Tertiary

- **Dynamic Tertiary:** 用于需要注意、建议完成、Debug only 和 required setup 等非错误状态。使用 `tertiaryContainer` 与 `onTertiaryContainer` 承载说明，使用 `tertiary` 标记行动性。

### Neutral

- **Dynamic Surface:** 页面根背景使用 `surface`。普通设置卡使用 `surfaceContainerLow`，图标底板与测试按钮使用 `surfaceContainerHighest`，会话列表使用 `surfaceContainer`。
- **Dynamic On Surface:** 主文本使用 `onSurface`，说明、非活动图标和中性状态使用 `onSurfaceVariant`。
- **Dynamic Outline Variant:** 只用于同一分组内的细分隔线，不用于给每张卡增加边框。
- **Dynamic Error:** 只用于真实错误、不可恢复或明确拒绝状态。关闭、未配置和推荐项不是错误。

**The Wallpaper Belongs Rule.** Android 12 及以上必须使用系统 `dynamicLightColorScheme` 或 `dynamicDarkColorScheme`。不要固定绿色、蓝色、紫色或任何 hue，也不要用 WeChat green 取代 Material You。

**The Semantic Fallback Rule.** 旧版 Android 可以提供静态 fallback，但只能复现同一组 Material 语义角色、对比度和层级。fallback 不是品牌色板，不得驱动新组件设计。

**The Restrained Color Rule.** 强调色只服务行动、选择和状态。普通内容由 surface 层级组织，非活动状态禁止使用高饱和颜色。

## Typography

**Display Font:** Google Sans Flex，使用 `wght 760`、`wdth 112`、`opsz 32`、`ROND 100` 的 display 变体。

**Body Font:** Google Sans Flex，所有 Material 3 typography role 使用 `ROND 100`。

**Label/Mono Font:** 控件标签继续使用 Google Sans Flex；ADB 命令等技术内容使用平台等宽字体。

**Character:** 圆润、鲜活、有弹性观感，但不幼稚。Display 变体只用于主状态标题、应用标题和真正的区块层级；设置名称、按钮、状态和正文使用标准 rounded 变体。变量字体轴可以参与 prominent state feedback，让 weight、width 或 optical size 的变化和容器运动同步。

### Hierarchy

- **Display** (760, 28sp, 36sp): 主状态卡的关键结论。每个页面最多一个。
- **Headline** (700, 24sp, 32sp): bottom sheet 标题和需要独立阅读的局部标题。
- **Title** (600, 16sp, 24sp): 设置项、卡片标题和主要组件名称。
- **Body** (400, 16sp, 24sp): 状态解释、引导和 sheet 正文。长段落限制在约 65 至 75 个字符的可读宽度内。
- **Supporting** (400, 12sp, 16sp): 设置结果、限制与补充说明。设置行默认最多两行。
- **Label** (500, 14sp, 20sp): 按钮、状态尾标和 capability pill。保持正常大小写，不全大写。

所有字号使用 `sp` 并允许系统字体缩放。中英文资源必须共享同一层级，不能通过缩小中文或强制单行来掩盖布局问题。

**The Rounded Utility Rule.** 圆体传达亲和力，字重和间距传达可靠性。不要用装饰字体、渐变文字或超大数字制造层级。

**The Display Restriction Rule.** Display 只属于真正的标题。开关、按钮、标签、状态和数据禁止使用 display 变体。

**The Variable Axis Feedback Rule.** 变量字体轴只用于 Hero、区块标题或显著的按压与完成反馈；变化必须短促、可逆并与状态同步，正文和长列表禁止持续变形。

## Elevation

WeModern 以 tonal layering、shape contrast 和 responsive scale 建立深度，默认不依赖自定义阴影。页面使用 `surface`，内容根据重要性进入 `surfaceContainerLow`、`surfaceContainer` 或 `surfaceContainerHighest`。主状态卡使用语义 container 形成清晰的最高视觉层级；重要组件可以通过更饱满的圆角、较大尺度和短暂 shape morphing 获得 Expressive 能量。Modal bottom sheet 的 elevation 交给 Material 3 与系统处理。

同一逻辑组可以使用 1dp 的 `outlineVariant` 分隔线。独立卡片不加装饰边框；相邻分组通过 4dp 或 8dp 间距和不同圆角关系表达结构。卡片内部禁止再嵌套一张同等级卡片，代码或诊断块除外。

**The Tonal Depth Rule.** 先改变 Material surface role，再考虑阴影。静止内容禁止使用自定义 drop shadow。

**The Flat at Rest Rule.** 阴影不是装饰。只有系统 sheet、按压或平台定义的 elevation 状态可以产生抬升感。

**The Shape Carries Energy Rule.** 灵动感优先来自容器形状、尺度和相邻组件关系。不要用阴影、描边或额外颜色弥补平淡的 shape hierarchy。

## Components

### App Shell and Navigation

- 使用原生 `Scaffold` 与 `TopAppBar`。应用标题使用 titleLarge 的 display 变体，右侧只保留系统设置入口。
- 主内容为单列 `LazyColumn`，最大宽度 600dp，水平边距 20dp，顶部 12dp，底部 8dp。
- 页面区块按 Setup、Bubbles、Advanced、Debug、Tests 排列。区块之间保留 28dp，标题与内容之间保留 12dp。
- 大屏只增加两侧留白，不把设置拆成重复卡片网格。
- 滚动页保持稳定骨架，但区块出现、依赖项展开和状态完成必须有连贯的 spatial response，避免整页像静态表单。

### Setup Hero

- **Shape:** 连续柔和的大圆角 (32dp)。
- **Layout:** 内边距 24dp，内容间距 18dp。状态图标底板为 56dp，圆角 20dp，图标 28dp。
- **State:** Required、Off、Debug only、All set 必须同时改变图标、文字和语义 container。颜色不能成为唯一差异。
- **Action:** 只在必要设置未完成时展示一个全宽、最小高度 56dp 的主要按钮。完成后用 capability pills 表达当前能力。
- **Expressive response:** mode 改变时协调 icon container 的 scale/shape、标题字体轴、container tone 和 capability 内容转换。Hero 是页面最明显的 Expressive moment，但转换不能延迟用户操作。

### Grouped Setting Rows

- **Shape:** 第一行外侧圆角 28dp、内侧 12dp；中间行 12dp；最后一行内侧 12dp、外侧 28dp。
- **Spacing:** 行间 4dp；行内水平 20dp、垂直 18dp；图标、文字和状态间距 16dp。
- **Icon:** 48dp tonal container，圆角 17dp，内部图标 24dp。图标必须来自同一 Rounded Material vocabulary；气泡入口使用 Material Symbol `bubble`。
- **Copy:** 标题使用 title，说明使用 supporting，默认最多两行。状态尾标由文字、图标和 chevron 共同表达。
- 已完成且不可操作的行是 compact status row，不是假装可点击的 disabled button。
- 分组外侧使用更饱满的 28dp，内部连接处收紧到 12dp，以形状关系表达同组归属。完成或高亮时可以在这两个 shape role 之间平滑过渡。

### Switch Cards

- **Shape:** 圆角 24dp，内边距 20dp。
- 整行是最小 48dp 的开关点击目标，并声明 `Role.Switch`；Switch 本体不重复注册点击。
- 总开关关闭或前置条件未完成时，依赖项使用渐隐与纵向收起隐藏，不保留无意义的 disabled controls。
- 关闭 Chat bubbles 只停止功能并隐藏依赖项，不清除用户已选择的 trampoline 偏好。
- 开关触发的依赖项 reveal 必须同时具备 effects transition 与 spatial transition，内容淡入、容器展开和下方列表位移保持同一方向。

### Status, Pills, and Icons

- Success 使用 check 图标和状态文字；attention 使用明确文案与可行动 chevron；neutral 使用说明文字。
- Capability pills 是轻量状态摘要，不可替代主要操作，也不可换行成大面积 badge wall。
- 标准图标为 24dp；小型按钮图标可以为 18 至 20dp；所有可交互图标必须有语义标签。
- 状态变化时图标可使用一次短促的 scale 或 shape response，pill 可以从相关触发点展开；禁止循环 pulse。

### Buttons and Tests

- Primary action 使用 full pill shape，最小高度 56dp，水平内边距 20dp、垂直 14dp。
- Test actions 使用 Material 3 Expressive `ButtonGroup`、`FilledTonalButton`、full pill shape 和 56dp 高度，保持视觉次级但具备鲜明触摸反馈。
- 按压时当前按钮增长、相邻按钮收缩并保持整组宽度稳定。尺度变化必须快速、可中断且无反复弹跳，让组件看起来有生命而不是松散抖动。
- Disabled 状态仅用于动作确实不可执行时，不能承担健康状态展示。

### Bottom Sheets and Conversation Settings

- 复杂的补充设置使用原生 `ModalBottomSheet`，水平内边距 24dp、底部 32dp。能在主页面 progressive disclosure 完成的内容不要先做 modal。
- 会话列表使用稳定 conversation key。排序改变时用原生 placement animation 保持对象连续性。
- 对话头像为 40dp 圆形；缺少头像时使用 secondary container 与首字符 fallback。
- 单会话覆盖保持 Android 熟悉的 radio vocabulary，不发明三态开关。
- 排序、筛选和单会话覆盖变化使用连续的 item placement、shape 与内容反馈，不能整块闪烁刷新。

### Motion

- Material theme 应优先采用 `MotionScheme.expressive()`。Prominent UI、Hero、ButtonGroup 和关键完成反馈使用 expressive spatial/effects spec；高频、小尺度反馈选择相应的 fast spec。
- 形状、位置、大小和 bounds 变化使用 spatial spec；颜色、alpha 和不改变 bounds 的视觉变化使用 effects spec。不要用同一个 tween 处理所有属性。
- 当前依赖暂未暴露 theme-level expressive API 时，使用 Compose 原生 spring/tween 匹配同一语义；升级后应迁移到 theme motion tokens，避免散落硬编码时长。
- `AnimatedVisibility`、`AnimatedContent`、`animateItem`、`animate*AsState` 和 transition APIs 根据状态结构选择。多个属性属于同一状态时使用统一 `Transition` 协调。
- 动效必须可中断并延续当前速度，不能因为连续切换而跳回起点。禁止循环动画、decorative choreography、bounce 和 elastic。
- 所有动画必须服从 Android animator duration scale；系统关闭或减少动画时，以即时状态更新和轻量 effects feedback 替代空间运动。

## Do's and Don'ts

### Do:

- **Do** 先展示 readiness 和一个下一步动作，再展开权限与技术细节。
- **Do** 把 Material 3 Expressive 作为默认设计语言，充分使用 expressive shapes、variable font axes、ButtonGroup 和 motion scheme。
- **Do** 让 Hero、总开关、排序和 Tests 等关键交互通过尺度、形状或空间连续性表现灵动与活泼。
- **Do** 使用 Material You 动态色和语义 color roles，浅色与深色均跟随系统。
- **Do** 保持 48dp 最小触控目标、可缩放 `sp` 文本、明确 accessibility labels 和非纯颜色状态提示。
- **Do** 用 4dp、8dp、12dp、20dp、24dp、28dp 的节奏区分紧密关系、组件内部和页面区块。
- **Do** 将 required、recommended 和 advanced 明确分层，optional ADB features 永远是次级能力。
- **Do** 使用 progressive disclosure 隐藏暂时无关的 Bubbles 与 Advanced 细节。
- **Do** 同时检查英文、简体中文和繁体中文，允许说明换行，不用压缩字号解决本地化长度。
- **Do** 在通知、气泡、Live Update 和设置入口中复用 Android 用户已经认识的图标与术语。

### Don't:

- **Don't** 把界面做成 dense developer dashboards。
- **Don't** 只把标准 Material 控件装进一组静态等大卡片就称为 Expressive；没有 shape hierarchy、responsive grouping 或 motion continuity 就不完整。
- **Don't** 使用 giant repeated cards 或 identical card grids；卡片必须对应真实分组或交互边界。
- **Don't** 使用 disabled controls as status indicators；状态行和动作控件必须在语义上分离。
- **Don't** 展示 long walls of system terminology；先解释结果，再按需显示 Android 名称或 ADB 命令。
- **Don't** 使用 excessive WeChat-green branding，也不要在 DESIGN.md 固定任何替代色系。
- **Don't** 让 setup flows 把 optional ADB features 表现成 mandatory。
- **Don't** 使用大于 1dp 的左右彩色 side-stripe border、gradient text、decorative glassmorphism 或 hero-metric template。
- **Don't** 在 inactive state 使用高饱和色，不要把推荐项画成错误，也不要只靠红绿表达状态。
- **Don't** 在标准设置可以 inline 或渐进展开时首先使用 modal。
- **Don't** 把灵动误解为 decorative motion、bounce、elastic 或编排式页面入场；活泼必须来自可解释的状态反馈。
- **Don't** 在系统允许动画时让关键状态瞬间跳变，也不要让所有属性使用同一种时长和 easing。
- **Don't** 在按钮、开关、状态标签或数据中使用 display font。
- **Don't** 在同一屏幕混用不同 icon style、卡片圆角 vocabulary 或开关 affordance。
