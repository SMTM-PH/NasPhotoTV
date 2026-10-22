# NAS Photo TV · 设计规范（DESIGN）

> 面向：设计师 / 前端实现 / 硬件厂商 UI 团队 / 产品。
> **本文的每一条数值都来自代码**（`ui/theme/Tokens.kt`、各页面实现），不是"建议稿"。
> 画布：**1920 × 1080 dp**（1080p 电视 1dp ≈ 1px，16:9）。所有尺寸单位 dp，字号 sp。

---

## 目录

1. [设计原则](#1-设计原则)
2. [设计 Token 完整表](#2-设计-token-完整表)
3. [页面结构图](#3-页面结构图1920×1080)
4. [焦点规范](#4-焦点规范)
5. [屏保设计（对标 iOS StandBy）](#5-屏保设计对标-ios-standby)
6. [动效规范](#6-动效规范)
7. [可访问性](#7-可访问性)
8. [附录：实现索引](#8-附录实现索引)

---

## 1. 设计原则

### P1 · 10-foot UI —— 三米外、坐着、只用手感
- **最小可读字号 16sp**（`TvType.Caption`），辅助信息不少于 13sp（`TvType.Tiny`）；
- 主标题 22~30sp，页面标题 56sp，屏保时钟 120~220sp；
- **一键一动作**：方向键移动、ENTER 确认、BACK 返回，不支持长按/双击/手势；
- 焦点必须"不用看就知道在哪"：**放大 + 白描边 + 青光晕**，绝不只靠颜色变化（色弱 + 电视色偏）。

### P2 · 遥控器优先 —— 焦点是一等公民
- 不依赖 Compose 默认焦点顺序做复杂网格，页面显式接管上下跨行；
- 焦点放大必须预留空间（滚动容器四周 padding ≥ 缩放溢出量），不能被父容器裁边；
- 列表滚动目标是"**聚焦项 + 一点上下文**"，不是"把聚焦项顶到边"。

### P3 · 安静不刺眼 —— 客厅里的东西，不该抢注意力
- 全站深空黑底（`#08090D`），**没有纯白大面积**，最亮文字 `#F5F7FA`；
- 屏保文字用白色 **90%** 不透明度（防烧屏 + 更柔和）；
- 动效时长 1000ms 起、缓动用 `FastOutSlowInEasing`，慢而不顿；
- 只有"用户正在操作"的区域才亮：卡片聚焦浮出标题，未聚焦只给图。

### P4 · 照片是主角 —— UI 让路给像素
- 看图页：图片区占满除底部控制栏以外的整屏，控制栏 3 秒自动隐藏；
- 视频舞台：控制层隐藏时只剩画面 + 极淡的"正在播放"标识；
- 屏保：背景照片永远铺满，前景元素只占四角；
- 一切叠加层（渐变遮罩、信息条）都半透明且从边缘渐隐。

### P5 · 一致性靠 token
- 禁止页面里写魔法色值/字号/圆角/间距（契约 §6）；新增视觉必须先进 `Tokens.kt`；
- 组件按"角色"选 token：`Surface`=卡片底，`SurfaceHigh`=按下/选中，`Accent`=可操作/成功，`AccentAlt`=视频与智能相册，`Warn`=收藏，`Danger`=关闭/离线。

---

## 2. 设计 Token 完整表

> 来源：`app/src/main/java/com/nasphoto/tv/ui/theme/Tokens.kt`（`TvColor` / `TvDimen` / `TvType`）与 `Theme.kt`（`TvShapes` / M3 映射）。

### 2.1 颜色 `TvColor`

| Token | 值 | ARGB | 用途 |
|---|---|---|---|
| `Bg` | `#08090D` | `0xFF08090D` | 全局底色（深空黑，微冷调）；页面背景、看图/播放底 |
| `BgElevated` | `#12141B` | `0xFF12141B` | 抬升面：M3 `surface`、行内空状态底、`OpenFolderCard` 底 |
| `Surface` | `#1A1D26` | `0xFF1A1D26` | 卡片底、顶栏图标按钮底、NAS 状态胶囊底、返回按钮底 |
| `SurfaceHigh` | `#252935` | `0xFF252935` | 设置行聚焦底色、工具按钮未聚焦底、开关轨道 |
| `SurfaceGlass` | 白 40% | `0x66FFFFFF` | 玻璃胶囊底（Hero 主按钮未高亮时） |
| `Divider` | 白 12% | `0x1FFFFFFF` | 1dp 描边、分隔线、进度条轨道 |
| `TextPrimary` | `#F5F7FA` | `0xFFF5F7FA` | 主文字（标题、正文） |
| `TextSecondary` | `#A9B0BE` | `0xFFA9B0BE` | 次要文字（日期、元信息、未聚焦标签） |
| `TextTertiary` | `#6F7787` | `0xFF6F7787` | 三级文字（计数、提示、空状态副文案） |
| `Accent` | `#3DDC97` | `0xFF3DDC97` | **品牌主色「极光青」**：可操作/选中/成功/焦点辉光 |
| `AccentDim` | 青 20% | `0x333DDC97` | 扩展名角标底 |
| `AccentAlt` | `#5B8CFF` | `0xFF5B8CFF` | **次强调「星蓝」**：视频、智能相册标签、极光背景 |
| `AccentAltDim` | 蓝 20% | `0x335B8CFF` | 同上的淡底 |
| `Danger` | `#FF6B6B` | `0xFFFF6B6B` | 关闭按钮、NAS 离线点 |
| `Warn` | `#FFC857` | `0xFFFFC857` | 收藏星标、太阳图标 |
| `FocusRing` | `#FFFFFF` | `0xFFFFFFFF` | **焦点环描边（3dp）** |
| `FocusRingGlow` | 青 50% | `0x803DDC97` | **焦点辉光**（shadow ambient/spot） |
| `ScrimTop` | 黑 70% | `0xB3000000` | 播放页/看图页顶部压暗（控制层可见时） |
| `ScrimBottom` | 黑 85% | `0xD9000000` | 底部压暗（信息条渐变终点） |

**派生色（不在 token 里，但在代码中固定使用，视为规范的一部分）**

| 位置 | 值 | 用途 |
|---|---|---|
| 按钮「深青字」 | `#00210F` | Accent 底上的文字/图标（选中 Tab、聚焦工具按钮、CTA、M3 `onPrimary`） |
| 视频时长角标底 | `#CC000000` | 卡片右下角 `▶ 12:34` |
| 字幕条底 | 黑 62% | 视频字幕 |
| 屏保时钟白 | `TextPrimary` @ 90% | `TimeWhite`，防烧屏不用纯白 |
| 屏保玻璃卡 | 白 5.5% + 竖向渐变(白10%→白3%→黑10%) + 1.6px 白 13% 描边 | `GlassCard` |
| 屏保遮罩 | 照片 34% / 极光 30% / 纯黑 0%，底部再加至 ≤62% | `ScreensaverScrim` |
| 背景辉光（媒体库） | `AccentAlt` 10% 径向，左上角 | `LibraryBackdrop` |

### 2.2 尺寸与间距 `TvDimen`

| Token | 值 | 用途 / 出现位置 |
|---|---|---|
| `ScreenPadding` | **64 dp** | 屏幕左右安全边距（顶栏、区块标题、网格 contentPadding、屏保前景四角） |
| `SafeInset` | 48 dp | 右侧额外安全区（预留） |
| `RadiusS` | 10 dp | 小圆角（扩展名角标、视频角标、字幕条、看图图片容器） |
| `RadiusM` | 16 dp | 中圆角（最近添加卡片、设置行、胶片条缩略图、提示胶囊） |
| `RadiusL` | 24 dp | 大圆角（Hero、顶栏 Tab、返回按钮） |
| `RadiusXL` | 32 dp | 特大圆角（主 CTA 胶囊、Hero 主按钮） |
| `SpaceXs` | 8 dp | 图标与文字之间、密排列表项之间 |
| `SpaceS` | 12 dp | 按钮组间距、元信息间距 |
| `SpaceM` | 20 dp | 卡片之间的横排间距、区块上下留白 |
| `SpaceL` | 32 dp | 区块标题上方留白、面板内边距 |
| `SpaceXl` | 48 dp | Hero 内边距、看图图片四周内边距 |
| `SpaceXxl` | 72 dp | 大区块分隔（屏保 DATE_FOCUS 时钟与月历卡之间） |
| `FocusScale` | **1.08 f** | 标准焦点放大（卡片、缩略图） |
| `FocusScaleLarge` | **1.12 f** | 大焦点放大（看图页工具按钮、主 CTA） |
| `FocusRingWidth` | **3 dp** | 焦点描边宽度 |
| `FocusElevation` | 24 dp | 焦点阴影高度（`shadow`） |

**页面内固定尺寸（不属于 token，但已是事实规范；改动需全组同步）**

| 元素 | 尺寸 | 出处 |
|---|---|---|
| 顶栏 | 高约 62dp（内容 30dp 图标 + 上下 `SpaceS` 12dp 内边距） | `LibraryTopBar` |
| 顶栏 Tab 胶囊 | 文本 16sp + `padding(h=20dp, v=10dp)`，圆角 32dp | `TabPill` |
| 顶栏图标按钮 | 图标 22dp + 文字 13sp，`padding(h=12dp, v=10dp)`，圆角 14dp | `TopIconButton` |
| Hero | 高度 = 屏高 × **0.42**（1080p ≈ **454dp**），左右 `ScreenPadding`；内层文字 `padding(start/end=48dp, bottom=32dp)` | `HomeTab` / `LibraryHero` |
| 最近添加卡片 | **300 × 180 dp**，圆角 16dp | `MediaCard` |
| 相册卡片 | **380 × 220 dp**，圆角 20dp | `AlbumCard` |
| 相册网格单元格 | **408 × 200 dp**，4 列 | `ALBUM_CELL_W/H`, `ALBUM_GRID_COLUMNS` |
| 媒体网格单元格 | **244 × 158 dp**，6 列 | `MEDIA_CELL_W/H`, `MEDIA_GRID_COLUMNS` |
| 网格间距 | 横/竖 12dp（相册网格横竖 20dp） | `GridContentPadding` / `Arrangement.spacedBy` |
| 网格 contentPadding | start/end **64dp**，top 16dp，bottom 32dp | `GridContentPadding` |
| 横排 contentPadding | start/end 64dp，top/bottom 14dp | `RowPadding` |
| 工具按钮（看图/播放） | 高 **56dp**，胶囊圆角 28dp，`padding(h=20dp)`，图标 22dp | `ToolChip` |
| 播放器控件 | 画面窗内：返回键 **52dp** 圆（1.12 放大）；中央播放键 **112dp** 圆（1.10 放大）；底栏按钮高 **52dp**、圆角 26dp；进度条高 12dp（聚焦 20dp）；信息面板宽 **520dp** | `VideoControls.kt` / `VideoOverlays.kt` |
| 键位提示胶囊 | 30 × 22 dp，圆角 10dp | `KeyHint` |
| 胶片条 | 高 104dp；缩略图 **132 × 78 dp**（`VisibleThumbCount = 9`）；左右 contentPadding 各 560dp（保证首尾能居中） | `FilmStrip` |
| 看图图片区 | 除控制栏外整屏，图片四周 `padding(h=48dp, v=32dp)` | `PhotoStage` |
| 设置行 | 高 **62dp**，圆角 16dp；左栏宽 **420dp**（窗口 <1200dp 时取 42%），右面板自适应（`weight(1f)`，1920 下约 900dp），圆角 32dp | `SettingsFocusRow` / `SettingsScreen` |
| 设置行内部 | 左：6dp 状态点 + 12dp + 标题（Body 20sp）+ 聚焦时才出现的副标题（Tiny 13sp，140ms 展开）；右：值胶囊 ／ 开关 ／ 动作图标 | `SettingsRow` |
| 值胶囊 | 高 42dp，圆角全圆，最小宽 **132dp**（共享目录 188dp）；聚焦时两侧出现 `‹ ›`（26×42dp 字形） | `ValueChip` / `ArrowGlyph` |
| 开关 `TvSwitch` | **64 × 34 dp** 圆角轨道，滑块 28dp，行程 **3dp → 33dp**（160ms），轨道内左"开"/右"关"13sp | `TvSwitch` |
| 设置进度条 | 高 6dp 全圆 | `SettingsProgressBar` |
| 设置右栏 · 屏保预览框 | **16:9**（约 900×506），圆角 24dp，1.5dp `Divider` 描边 | `SettingsScreensaverPanel` |
| 设置右栏 · NAS 拓扑节点 | 每个胶囊高 **84dp**，图标圆 46dp；连线区高 34dp；右指标列宽 **280dp**；底部连接串条圆角 16dp | `SettingsNasPanel` |
| 设置右栏 · 播放封面卡 | **400 × 225 dp**，圆角 24dp，中央播放钮 64dp | `SettingsPlaybackPanel` |
| 设置右栏 · 关于 | Logo 88dp（24dp 圆角，青→星蓝线性渐变），设计原则块圆角 16dp | `SettingsAboutPanel` |
| 屏保天气卡 | 宽 **300dp**（`WeatherCard`），距左上角 `ScreenPadding` | `ScreensaverForeground` |
| 屏保月历卡 | 宽 **368dp**，单元格 42 × 38 dp | `CalendarPanel` |
| 屏保退出提示 | 底部居中，`padding(bottom=40dp)`，进入后 8s 淡出 | `ExitHint` |
| 屏保像素位移 | **±8dp**，9 个固定位置，每 **2 分钟**切换 | `PIXEL_SHIFT_POSITIONS` |

### 2.3 字号 `TvType`

| Token | 值 | 用途 |
|---|---|---|
| `Display` | **56 sp** | 页面标题 / Hero 标题 / 相册详情标题（字重 Bold） |
| `ClockHuge` | **220 sp** | 屏保 FLIP 大数字（ExtraLight，字距 −4sp，行高 ×1.05） |
| `ClockLarge` | **120 sp** | 屏保 THIN 细数字（Thin） |
| `Section` | **30 sp** | 区块标题（SemiBold）、看图信息条标题、THIN 日期行、屏保 FLIP 顶/底日期 |
| `CardTitle` | **22 sp** | 卡片标题、顶栏 Logo「NAS 相册」、相册名、视频字幕 |
| `Body` | **20 sp** | 正文、Hero 日期、设置行主文字、工具按钮文字、屏保日期行 |
| `Caption` | **16 sp** | 辅助文字、Tab 标签、元信息、卡片聚焦时标题、空状态副文案 |
| `Tiny` | **13 sp** | 最小字号：NAS 状态、视频角标、计数、键位提示、退出提示 |

**屏保专用字号（代码内固定，向外视为规范）**

| 样式 | 元素 | 字号 | 字重 |
|---|---|---|---|
| FLIP | 大数字 | 220sp | ExtraLight |
| FLIP | 顶部日期行（星期 · 农历） | 30sp | ExtraLight，字距 6sp，alpha 0.62 |
| THIN | 数字 | 120sp | Thin |
| THIN | 日期行 | 30sp | Light，字距 4sp |
| MINIMAL | 右上角时间 | **44sp** | Light |
| MINIMAL | 中央大星期 | **96sp** | Light，字距 10sp |
| MINIMAL | 月日 | **48sp** | ExtraLight |
| DATE_FOCUS | 大星期 | **168sp** | Light，行高 176sp |
| DATE_FOCUS | 月日 | **54sp** | ExtraLight |
| DATE_FOCUS | 时间（青色） | **46sp** | Light |
| 天气卡 | 温度 | **84sp** | Light（`°` 52sp ExtraLight） |
| 天气 pill | 温度 | 22sp | Light |

### 2.4 圆角 `TvShapes`（`Theme.kt`）

| Token | 值 |
|---|---|
| `TvShapes.S` | `RoundedCornerShape(10.dp)` |
| `TvShapes.M` | `RoundedCornerShape(16.dp)` |
| `TvShapes.L` | `RoundedCornerShape(24.dp)` |
| `TvShapes.Xl` | `RoundedCornerShape(32.dp)` |

### 2.5 M3 映射（组件直接用 `MaterialTheme.colorScheme` 时的行为）

`NasPhotoTvTheme` 用 `darkColorScheme`：`primary=Accent` / `onPrimary=#00210F` / `secondary=AccentAlt` /
`background=Bg` / `onBackground=TextPrimary` / `surface=BgElevated` / `surfaceVariant=Surface` /
`onSurfaceVariant=TextSecondary` / `error=Danger`。
Typography：`displayLarge=Display(SemiBold)` / `headlineMedium=Section(SemiBold)` / `titleLarge=CardTitle(Medium)` /
`bodyLarge=Body` / `bodyMedium=Caption` / `labelSmall=Tiny(Medium, 字距 0.6sp)`。

---

## 3. 页面结构图（1920×1080）

> 坐标以左上角为原点。"←64→"表示 `ScreenPadding=64dp`。各图按代码实现绘制，非精确到 px。

### 3.1 媒体库 · 首页（Tab「全部照片」）

```
┌───────────────────────────────────────────────────────────────────────────────────┐ 1920 × 1080
│ 64  [📺] NAS 相册  (● Home-NAS 在线)   [全部照片] 年·月·日 相册 最近添加 收藏·人物·地点   [🔍搜索] [▶屏保] [⚙设置]  64 │ 顶栏 h≈62
├───────────────────────────────────────────────────────────────────────────────────┤
│                                                                                   │
│   ┌───────────────────────────────────────────────────────────────────────────┐   │
│   │                                                                           │   │
│   │                    HERO 454dp（屏高 ×0.42）                                │   │
│   │            媒体缩略图 Crop 铺满 · 下方渐变到 Bg · 左侧压暗 78%→0             │   │
│   │                                                                           │   │
│   │    ▶ 3:32   (视频才有)                                                     │   │
│   │    大理洱海 · 日出                        ← 56sp Bold，左下角 (48dp, 32dp)   │   │
│   │    2026 年 10 月 19 日                    ← 20sp TextSecondary              │   │
│   │    4032x3024  ·  6.2 MB                   ← 16sp TextTertiary              │   │
│   │    [  查看 / 播放  ]                      ← 32dp 圆角胶囊，聚焦时变青底深青字  │   │
│   └───────────────────────────────────────────────────────────────────────────┘   │
│  64                                                                            64 │
│   最近添加                                        24 项                            │ ← Section 30sp SemiBold + 计数 16sp
│   ┌300×180┐ 20 ┌───────┐ 20 ┌───────┐ 20 ┌───────┐ 20 ┌───────┐ →            │ ← LazyRow，聚焦卡片放大1.08 + 白描边3dp
│   │  图     │    │  图    │    │       │    │       │    │       │              │    聚焦时底部浮出标题(16sp)+日期(13sp)
│   └────────┘    └───────┘    └───────┘    └───────┘    └───────┘              │
│                                                                                   │
│   相册                                            共 5 个                          │
│   ┌380×220────┐ 20 ┌───────────┐ 20 ┌───────────┐ 20 ┌── 查看全部相册 ──┐ →   │
│   │ 封面 + 底部渐变(35%→92%黑) │  │           │    │  ➕ 图标 + 文字   │          │
│   │ 2024 大理之旅             │  │           │    │                 │          │
│   │ 24 项  (聚焦时换成 subtitle)│  │           │    │                 │          │
│   └─────────────┘   ↑ 聚焦：1.08 + 白描边 3dp + 青色辉光 24dp            │
│                                                                                   │
│                                                                                   │
└───────────────────────────────────────────────────────────────────────────────────┘
   ↑ 焦点落点：进入页面 → Hero；Hero↓ → 最近添加同列；↑ → 回 Hero；
     相册行 ↓ 无目标则不吞键（交给 Compose）；相册行末尾「查看全部相册」→ Tab「相册」
```

### 3.2 媒体库 · 相册网格（Tab「相册」）

```
┌───────────────────────────────────────────────────────────────────────────────────┐
│ 顶栏（同上）                                                                          │
├───────────────────────────────────────────────────────────────────────────────────┤
│  64    相册                                                              5 个文件夹  64 │ ← SectionHeader，上下 20dp
│                                                                                     │
│   ┌─408──────┐ 20 ┌──────────┐ 20 ┌──────────┐ 20 ┌──────────┐                     │
│   │  h=200   │    │          │    │          │    │          │                     │ ← LazyVerticalGrid 4 列
│   │ 封面/名/数 │    │          │    │          │    │          │                     │   contentPadding: 64/64/16/32
│   └──────────┘    └──────────┘    └──────────┘    └──────────┘                     │   横竖间距 20dp
│   20                                                                                │
│   ┌──────────┐    ┌──────────┐    ┌──────────┐                                     │ ← 上下 = 同列滚动（Compose 默认）
│   │          │    │          │    │          │                                     │
│   └──────────┘    └──────────┘    └──────────┘                                     │
│                                                                                     │
└───────────────────────────────────────────────────────────────────────────────────┘

相册详情（进入相册后，同一 route 内的页内状态）：
┌───────────────────────────────────────────────────────────────────────────────────┐
│ 64 [← 返回]  2024 大理之旅                                   ← Display 56sp Bold      │  ← 返回按钮可聚焦，高≈46dp
│               24 张照片 · 2 个视频                                                   │
├───────────────────────────────────────────────────────────────────────────────────┤
│  ┌─244─┐ ┌─244─┐ ┌─244─┐ ┌─244─┐ ┌─244─┐ ┌─244─┐   每行 6 个，间距 12dp            │
│  │ 158 │ │     │ │     │ │     │ │     │ │     │                                  │ ← 第一行 ↑ → 返回按钮
│  └─────┘ └─────┘ └─────┘ └─────┘ └─────┘ └─────┘                                  │
│  ┌─────┐ ┌─────┐ …                                                                 │
└───────────────────────────────────────────────────────────────────────────────────┘
   BACK / 返回按钮 → 回列表；空相册显示「这个相册还是空的」+ 副文案（焦点落返回按钮）
```

### 3.3 全屏看图（`PhotoViewerScreen`）

**图片优先**：舞台 `fillMaxSize()` + `ContentScale.Fit`，整屏就是画框，没有常驻侧栏也没有 16:9 副框；
标题 / 来源 / 拍摄 / 序号都在随控制栏显隐的浮层里，控制栏隐藏后屏幕上只剩图片本身。

```
┌───────────────────────────────────────────────────────────────────────────────────┐
│ ▓ 左上浮层（chromeVisible 才出现，圆角 24 + 竖向黑渐变，宽 ≤760dp）                  │
│ ▓  ● 正在浏览这组照片。 / 你喜欢这张照片。              [⏸ 幻灯片播放中 ▬▬▬▭] ← 右上 │
│ ▓  来源：Home-NAS · 2026 年 10 月 5 日                                              │
│ ▓  拍摄：2026年10月5日                                                              │
│ ▓  尺寸 / 文件 / ID      ← 仅「信息」展开或方向键 UP 时                              │
│ ▓  IMG_20261005_10                                                                 │
│ ▓  01 / 24                                                                         │
│ ▓  方向键切换 · 上键信息 · 下键工具 · 返回关闭                                        │
│                                                                                   │
│      图片 ContentScale.Fit 铺满整屏（实况：静图与片段共用同一画框，LIVE 徽标常驻左上）  │
│ ← 左缘返回（随控制栏显隐）   放大态：2× + 方向键平移，顶部 [2× 放大 · 偏移 -0.5 / 0.0]  │
│                                                                                   │
│ ░░░ 底部浮层：Transparent → ScrimBottom 渐变（只在控制栏可见时出现）░░░░░░░░░░░░░░░░ │
│ ░ [◀上一张][▶下一张][ⓘ信息][★收藏][⏵幻灯片][⛶放大/适应屏幕][✕关闭]      按钮 h=56   │
│ ░ [ 132×78 ][132×78][132×78] … 胶片条 104dp，当前项居中（左右各留 560dp 空白）…      │
└───────────────────────────────────────────────────────────────────────────────────┘
   控制栏 3s 无操作自动隐藏；焦点在按钮行 / 信息展开 / 幻灯片播放时不隐藏。
   任意按键 → 显示控制栏 且 继续执行原动作（不吃键）。
   下键 / TAB / MENU → 焦点进工具行首钮：`toolbarEnter` 计数器 + 等两帧，
   因为 AnimatedVisibility 要先组合出按钮节点，同一帧 requestFocus 必然失败。
```

### 3.4 视频播放（`VideoPlayerScreen`，模拟解码）

画面是**一块 16:9 的"电视窗"**（`aspectRatio(16/9)` + `fillMaxHeight(0.78f)`），1920×1080 上约 **1478 × 831 dp**，
居中、圆角 24dp、上下自然留黑边 —— 像真播放器而不是"贴满屏的图"。所有控制元素都在窗内。

```
┌───────────────────────────────────────────────────────────────────────────────────┐
│                                    ↑ 上下黑边（约 124dp）                            │
│      ┌──────────────────────────── 16:9 画面窗 1478 × 831 ──────────────────────┐  │
│      │ ░ [←]  宝宝第一次走路                    来自 最近添加      [NAS 直连·原片] ░ │  │ ← 顶部条（ScrimTop）
│      │ ░    52dp圆     卡标题22sp                Caption16sp    Tiny13sp 胶囊    ░ │  │   内边距 32/20dp
│      │ ░                                                                        ░ │  │
│      │ ░                           ┌───────────┐                               ░ │  │
│      │ ░                           │   ▶ 112dp │ ← 中央播放键（1.10 放大）        ░ │  │
│      │ ░                           └───────────┘                               ░ │  │
│      │ ░                        [ ⏩ 快进 10 秒 ] ← 居中偏下 OSD（1.1s）           ░ │  │
│      │ ░                                                                        ░ │  │
│      │ ░               这一天的风很轻，我们沿着海边一直走到了日落。                  ░ │  │ ← 字幕条（黑62%，22sp）
│      │ ░ ────────────────────────────────────────────────────────────────────── ░ │  │
│      │ ░ 00:42  ●━━━━━━━━━━━━━━━━━━━━━━░░░░░░░░░░░░░░░░░░  03:32   ◀▶ 微调10s   ░ │  │ ← 进度条（聚焦加高+白描边）
│      │ ░ [快退10s][暂停][快进10s] │ [上一个][下一个] │ [字幕 开][循环 关]      [信息] ░ │  │ ← 底栏按钮 52dp
│      └──────────────────────────────────────────────────────────────────────────┘  │
│                                    ↓ 上下黑边                                        │
│                              [● 正在播放] ← 控制层隐藏时右下角常驻                      │
└───────────────────────────────────────────────────────────────────────────────────┘

叠加层（不改变画面窗布局，按需出现）：
  右侧信息面板（520dp 宽，圆角 24dp，slide 240ms）：缩略图 148×84 + 标题 + 来自 xxx
      · 文件信息：分辨率 / 时长 / 大小 / 格式 / 拍摄日期 / 已播放
      · 存储位置：smb://Home-NAS/photo/<id>（青色）+ Home-NAS · 192.168.1.20 · SMB 已连接
  自动连播角标（右下，ScreenPadding）：44dp 圆形倒计时 + 「即将播放：xxx」+「N 秒后自动连播 · 回车取消」+ [取消连播]
  结束面板（居中，BgElevated 96%）：标题「播放结束」+ 片名 + [重新播放][下一个][返回媒体库] + 键位提示
```


### 3.5 屏保待机（`ScreensaverScreen`）— 布局随 4 种时钟变化

```
① FLIP 翻页数字（招牌组合：FLIP + 照片轮播）
┌───────────────────────────────────────────────────────────────────────────────────┐
│ ┌ 天气卡 300dp ┐                                                                   │
│ │ ● 杭州  多云  │                        星期五 · 九月初五 · 丙午年   30sp α0.62     │
│ │  21°  [图标]  │                                                                   │
│ │ 最高24° 最低16°│             [ 0 9 ] ⋅⋅ [ 4 1 ]      ← 220sp ExtraLight，冒号每秒呼吸 │
│ └──────────────┘                                                                   │
│                                                                                   │
│                                                                  第 292 天 · 星期五 │ ← 20sp α0.56
│                                                                                   │
│                    ┌─ 按任意键返回 ─┐  ← 底部居中，8s 后淡出                          │
└───────────────────────────────────────────────────────────────────────────────────┘

② THIN 纤细极简（THIN + 照片/极光）
             10月19日 · 星期日         ← 30sp Light，字距 4sp
                  ──────              ← 88×1dp 渐变短线
               09 : 41                ← 120sp Thin，冒号 50%
             上午 · 56 秒              ← 16sp α0.52

③ MINIMAL 极简小钟（MINIMAL + 纯黑/极光）
                                             ● 09:41   ← 右上角 44sp
                                             2026年10月19日  星期日  16sp α0.60
          ← 天气 pill（顶部左，不抢戏）
   ┌─────────────────────────────┐
   │ 星期日     96sp Light 字距10sp │  ← 中央偏左（CenterStart + 8dp）
   │ 10月19日  九月初五 · 丙午年    │     48sp ExtraLight + 16sp
   └─────────────────────────────┘

④ DATE_FOCUS 日期日历（DATE_FOCUS + 极光）
                        ┌─ 月历卡 368dp（GlassCard）──┐
   星期五               │ 2026年          10月       │
   168sp Light          │ 一 二 三 四 五 六 日         │
   10月19日  九月初五‧丙午年│ …  ⃝19  …   ← 今日青色圈点 │
   09:41  46sp Accent   └────────────────────────────┘
        ↕ 72dp（SpaceXxl）
```

**分层结构（自下而上）**

| 层 | 内容 |
|---|---|
| 1 · 背景 | `PHOTO`：照片 `ContentScale.Crop` + 2s 交叉淡入 + Ken Burns（scale 1.0→1.12，单程 26s）；`AURORA`：纯 Canvas 三团径向渐变（极光青/星蓝/淡紫）34s / 47s / 61s 异速漂移 + 46 颗固定星点 + 细噪点；`BLACK`：径向 `#0A0D12 → #05060A → 黑`（避免"死黑"像关机） |
| 2 · 遮罩 | 竖向渐变：照片 34% / 极光 30% / 纯黑 0%，底部加深 +22%（上限 62%）；照片额外叠径向压暗 18% |
| 3 · 防烧屏 | `PixelShiftBox`：9 个固定位置（中心 + 八方向 **±8dp**），每 **2 分钟** 用 900ms 平移到下一格；`nightDim` 时整体 alpha **0.90 ↔ 1.00**（15s 单程，30s 往返） |
| 4 · 前景 | 只有时钟（按样式定位）与退出提示（底部，8s 后淡出）。**不显示任何图片信息**：标题、拍摄日期、分辨率、序号、从文件名猜的"地点"都不上屏 |

### 3.6 设置（`SettingsScreen`，两栏）

```
┌───────────────────────────────────────────────────────────────────────────────────┐
│ 64 ● 设置  (● Home-NAS)                    [↑↓ 选择][←→ 调值][ENTER 执行][BACK 返回] │ ← 顶栏：Display 56sp + 键位提示胶囊
├───────────────────────────────────────────────────────────────────────────────────┤
│  ← 左列表 420dp →  32 ←──────────── 右面板（自适应，1920 下约 900dp）──────────────→ │
│                                                                                     │
│  NAS 连接              ← 16sp Medium，聚焦组变青 + 右侧 28dp 强调线                    │
│  ┌──────────────────────────────────┐   ┌───────────────────────────────────────┐  │
│  │● 设备名称            ‹ Home-NAS ›│   │ 连接拓扑           电视 ↔ 路由器 ↔ NAS │  │ ← PanelTitle 30sp + 13sp 副标题
│  │  局域网内的 NAS 主机名  ← 仅聚焦显示│   │ ┌─────────────────────────────────┐   │  │
│  ├──────────────────────────────────┤   │ │ [📺] 这台电视   Android TV·1080p │   │  │ ← 节点胶囊 84dp
│  │● 协议                     ‹ SMB ›│   │ └─────────────────────────────────┘   │  │
│  │● 地址              192.168.1.20  │   │  ┈┈●┈┈●┈┈ Wi-Fi 5GHz · 内网  ← 流动光点  │  │
│  │● 端口                      445   │   │ ┌─────────────────────────────────┐   │  │
│  │● 账号                    media   │   │ │ [🌐] 路由器     192.168.1.1·千兆 │   │  │
│  │● 共享目录                /photo  │   │ └─────────────────────────────────┘   │  │
│  │● 测试连接      ✓/⟳/✕/›        │   │  ┈┈●┈┈●┈┈ 有线 · 1.0 Gbps            │  │
│  │● 重新扫描媒体库   上次：2 分钟前 › │   │ ┌─────────────────────────────────┐   │  │
│  └──────────────────────────────────┘   │ │ [💾] Home-NAS  SMB·192.168.1.20 │   │  │
│  屏保与待机                             │ └─────────────────────────────────┘   │  │
│  ┌──────────────────────────────────┐   │          状态 已连接 / 延迟 12ms / 抖动 6ms│  │ ← 指标列 280dp
│  │● 屏保                    ●──[开] │   │          带宽 86 MB/s / 路径 /photo       │  │
│  │● 静止后进入            ‹ 1 分钟 › │   │ ┌─────────────────────────────────────┐ │  │
│  │● 照片停留              ‹ 10 秒 › │   │ │ smb://media@192.168.1.20:445/photo   │ │  │ ← 连接串 + 上次扫描
│  │● 时钟样式           ‹ 翻页数字 › │   │ │ ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬░ ← 重扫时出现         │ │  │
│  │● 背景             ‹ 照片轮播 ›   │   │ └─────────────────────────────────────┘ │  │
│  │● 显示日期                      │   └───────────────────────────────────────┘  │
│  │● 夜间自动调暗 / Ken Burns（开关） │                                                  │
│  │● 预览屏保                    ▶ 全屏 ›│  ← 聚焦「屏保」/「预览屏保」行时右栏出现提示：  │
│  └──────────────────────────────────┘  │   「ENTER 全屏预览 · 屏保里任意键退出」         │
│  视频播放 / 关于（自动连播、循环、字幕、│  聚焦「时钟样式 / 背景」时右栏底部出现可选胶囊条： │
│  转码 / 版本 / 引擎 / 设备 / 设计说明 / │   ‹ 翻页数字 › ‹ 纤细极简 › ‹ 极简小钟 › ‹ 日期日历 ›│
│  恢复默认设置）                         │                                                  │
├───────────────────────────────────────────────────────────────────────────────────┤
│ 64 局域网内的 NAS 主机名（聚焦行提示）              屏保与待机 · 第 12 / 30 项        │ ← 底部状态条 13sp
└───────────────────────────────────────────────────────────────────────────────────┘

屏保组聚焦时右栏切换为「屏保实时预览」：
   ┌─ 16:9 预览框（约 900×506，圆角 24dp）──────────────────────────┐
   │ (背景：照片/极光/纯黑)  (夜间调暗叠加暖色 0x33FF8A3D)           │
   │                                     [☁ 21° 杭州·多云] ← 右上 │
   │        09:41 ← 四种时钟小样，各自保留识别度                    │
   │ [照片标题 / 日期 · 分辨率] ← 左下（仅 PHOTO）                  │
   │ 屏保已关闭时盖一层 45% 黑 + 「屏保已关闭」                      │
   └────────────────────────────────────────────────────────────────┘
   时钟 / 背景 / 停留 / 进入 / 叠加 ← InfoLine 摘要（标签 72dp + 值）
播放组聚焦时右栏切换为「播放摘要」：400×225 封面卡 + [自动连播][循环播放][显示字幕][NAS 转码] 四条参数
关于组聚焦时右栏切换为「关于」：88dp 渐变 Logo + 版本 + 统计 + 可展开的 5 条设计原则

   ↑↓ 换行（自动滚动保证聚焦行可见）；←→ 行内调值（聚焦行变 SurfaceHigh + 白 3dp 描边 + 1.02）
   开关行/值行左右键被吃掉，不会跑到右面板；ENTER 执行；BACK 关闭（设计说明展开时先收回）
```

> 设置页的分组/行/键位以 `ui/settings/SettingsModel.kt`（`SettingsGroup` / `SettingsKey` 30 行 / `SettingsActions`）为准；
> 每行的可选值与默认值**口径以 `data/model/Models.kt` + `data/SettingsStore.kt` 为准**：
>
> | 设置项（字段） | 可选值 / 范围 | 默认 |
> |---|---|---|
> | `screensaver.enabled` 屏保开关 | 开 / 关 | `true` |
> | `screensaver.idleTimeoutSec` 静止多久进入 | 30s → 1 → 2 → 5 → 10 → 30 分钟循环 | `60`（1 分钟） |
> | `screensaver.photoIntervalSec` 照片停留 | 5 → 10 → 15 → 30 → 60 秒循环 | `10` |
> | `screensaver.clockStyle` 时钟样式 | FLIP 翻页数字 / THIN 纤细极简 / MINIMAL 极简小钟 / DATE_FOCUS 日期日历 | `FLIP` |
> | `screensaver.background` 背景 | PHOTO 照片轮播 / AURORA 极光动态 / BLACK 纯黑极简 | `PHOTO` |
> | `screensaver.showDate` 显示日期（农历） | 开 / 关 | `true` |
> | `screensaver.nightDim` 夜间自动调暗 | 开 / 关 | `true` |
> | `screensaver.kenBurns` Ken Burns | 开 / 关 | `true` |
> | `playback.autoPlayNext` 自动连播 | 开 / 关 | `true` |
> | `playback.loopMode` 循环播放 | 开 / 关 | `false` |
> | `playback.showSubtitles` 默认字幕 | 开 / 关 | `false` |
> | `playback.transcode` NAS 端转码 | 开 / 关 | `false` |
> | `nas.*` 连接 | 协议 SMB / WebDAV / NFS / Jellyfin；host / port / username / sharePath | `SMB · media@192.168.1.20:445/photo` |

---

## 4. 焦点规范

### 4.1 焦点态的三件套（缺一不可）

| # | 视觉 | 实现 | 数值 |
|---|---|---|---|
| 1 | **放大** | `graphicsLayer.scale` + `animateFloatAsState` | 卡片 `1.08`（`TvDimen.FocusScale`）/ 大按钮 `1.12` / 设置行 `1.02` / Hero `1.015` / 顶栏 Tab `1.06` |
| 2 | **白色描边** | `Modifier.border(BorderStroke(…), shape)` | **3dp** `TvColor.FocusRing`（白），alpha 随焦点 0→1 |
| 3 | **青色辉光** | `Modifier.shadow(elevation, shape, ambientColor = spotColor = FocusRingGlow)` | **24dp** 高度，光色 `#803DDC97` |

> 规则来源：契约 §5「任何可聚焦元素都必须有肉眼可见的焦点态……不能只靠颜色变化」。
> 库页的 `TvFocusBox` 与公共 `FocusCard` 都实现了这三件套；设置行为了不抖动改用「底色 SurfaceHigh + 白描边 + 1.02」的组合，**也是三件套的变体**。

### 4.2 不同元素的焦点参数

| 元素 | 放大 | 描边 | 其它焦点反馈 | 来源 |
|---|---|---|---|---|
| 媒体卡片（300×180 / 244×158） | 1.08 | 3dp 白 | 底部 45%→92% 黑渐变浮出标题（16sp）+日期（13sp）；视频角标始终可见 | `MediaCard` / `TvFocusBox` |
| 相册卡片（380×220 / 408×200） | 1.08 | 3dp 白 | 副标题从「24 项」换成 `subtitle`，颜色变 `Accent` | `AlbumCard` |
| Hero | 1.015 | 3dp 白 | 左下主按钮从 `SurfaceGlass` 变 `Accent` 底 + 深青字 | `LibraryHero` |
| 顶栏 Tab | 1.06 | **始终 3dp 白**（`FocusRingWidth`） | 选中态本身是 `Accent` 底 + 深青字，未选中描边 1dp `Divider` | `TabPill` |
| 顶栏图标按钮 | 1.08 | 3dp 白 | 图标/文字由 `TextSecondary` → `Accent` / `TextPrimary` | `TopIconButton` |
| 看图工具按钮 | **1.12** | 3dp 白 | 整块变 `Accent` 底 + `Bg` 色图标文字（反向高亮，一眼可辨） | `ToolChip` |
| 播放器返回键 / 底栏按钮 / 结束面板按钮 | **1.12**（`FocusScaleLarge`） | 3dp 白 | 变 `Accent` 底 + `Bg` 色图标文字；"开"状态的按钮在未聚焦时是淡强调底 + 青字 + ✓ | `TvFocusSurface` / `VideoControlButton` |
| 播放器中央播放键 | **1.10** | 3dp 白 | 未聚焦黑 46% 圆底，聚焦青 94% 圆底 + 深青图标 | `CenterPlayButton` |
| 播放器进度条 | 不变（高度 12→20dp） | 3dp 白 | 轨道变 `SurfaceHigh` 70% + 白描边；右侧提示从「◀ ▶ 切换」变青色的「◀ ▶ 微调 10s」 | `VideoProgressBar` |
| 胶片条缩略图 | 1.08 | 3dp 白 | 未聚焦 alpha 0.62 → 聚焦 1.0 | `ThumbCell` |
| 设置行 | 1.02 | 3dp 白 | 底 `SurfaceHigh`；左侧 6dp 状态点变青；副标题（提示文案）180ms 展开；值胶囊两侧出现 `‹ ›` | `SettingsFocusRow` |
| 设置组标题 | 不变 | 无 | 聚焦组标题变 `Accent` + 右侧出现 28dp 高亮短线（180ms 变色） | `SettingsGroupHeader` |

### 4.3 焦点移动规则

| 场景 | 规则 | 实现 |
|---|---|---|
| 行内左右 | `LazyRow` 默认顺序 | Compose 焦点系统 |
| 跨行上下 | **同列落点**：记住上一行聚焦的 index，`DOWN/UP` 时精确聚焦目标行的同 index（越界则取末项） | `PendingFocus.request(requesterAt(list, index))` |
| 网格上下 | 同列垂直（Compose 默认）+ 首行 `UP` 可显式回到页头 | `scrollGridTo` / `verticalNav(onUp = …)` |
| 顶栏 → 内容 | Tab 按 `DOWN` 时 `requestBlock(BLOCK_CONTENT)`，由当前 Tab 页决定第一落点（Hero → 最近添加 → 无） | `ConsumeBlockRequest` |
| 进入页面 | 首次焦点：首页 → Hero；相册详情 → 第一张（空相册 → 返回按钮）；看图页 → 图片区；播放页 → 中央播放键（`VideoFocusSlot.PLAY`）；设置页 → 「屏保」开关（延迟 120ms 等首帧布局） | `LaunchedEffect(Unit) { requestFocus() }` |
| 播放器分区记忆 | 播放页按 **4 个分区**（返回 / 进度条 / 中央播放键 / 底栏按钮行）上下穿梭，每个分区记住上次停留的控件，回来时回到原位 | `VideoKeyHandler.kt`（`ZoneMemory`） |
| 播放器不参与 Compose 焦点链 | 播放页所有控件 `focusProperties { canFocus = false }`，按键统一走页面根 `onPreviewKeyEvent` → 焦点外观由 `state.focusSlot` 驱动。**原因**：避免同一次 ENTER 既触发按钮的 `onClick` 又触发页面动作（双触发）；也避免遥控器十字键走焦点搜索时跳错。 | `VideoKeyHandler.kt` / `TvFocusSurface.kt` |
| 焦点不被吞 | 方向键没有目标时**不拦截**（返回 `false`），交给 Compose 搜索最近可聚焦项 | `verticalNav(onUp = null)` |
| 屏保 | 单独规则：外层 `Box` 抢焦点并吞掉**所有**按键 → 只 `onExit()`，不触发任何其它动作（含 Activity 级 BACK 兜底） | `onKeyEvent { … true }` + `OnBackPressedDispatcher` |

### 4.4 滚动跟随（"聚焦项必须可见"）

| 容器 | 策略 | 代码位置 |
|---|---|---|
| 横排 `LazyRow` | 滚到 **index − 1**（左对齐、右侧露出一张，做"还有更多"的预告）；已在该位置则不动 | `scrollRowTo` |
| 网格 `LazyVerticalGrid` | 只在聚焦格**完全不可见**时才滚；滚到 **上一行** 做参照（避免上下移动时画面跳） | `scrollGridTo(focusedIndex / columns − 1)` |
| 胶片条 | 滚到 `index − 9/2`，把当前项放画面中间；左右各留 560dp padding 使首尾也能居中 | `FilmStrip` |
| 区块（Tab 内容） | 首行卡片聚焦时，页头仍可见（contentPadding 与 Hero 高度已配平） | `HomeTab` LazyColumn |

> 关键约束：**放大溢出必须预留**。1.08 倍意味着每边多出 4%（244 宽约 5dp、158 高约 3.2dp）；1.12 倍每边 6%。
> 所以所有滚动容器四周至少留 14~16dp（`RowPadding` / `GridContentPadding`），屏幕水平方向留 64dp。

### 4.5 焦点可见性反例（禁止）

- ❌ 只改背景色不放大、不描边；
- ❌ 放大但父容器 `clipToBounds` 或 padding 不足导致切边；
- ❌ 焦点落在列表外（滚动后不可见）；
- ❌ 靠颜色区分"选中/聚焦"（两者必须有不同的形状/尺寸线索：选中=底色，聚焦=放大+描边）；
- ❌ 空列表不处理（`LazyRow(items = emptyList())` + 强行 `requestFocus()` 会崩或卡住焦点）。

---

## 5. 屏保设计（对标 iOS StandBy）

### 5.1 StandBy → NAS Photo TV 的对应关系

| iOS StandBy | 我们的实现 | 说明 |
|---|---|---|
| 横屏待机全屏 | 全屏 16:9 + 沉浸式（隐藏状态栏） | 电视天然横屏 |
| 4 种表盘（模拟/数字/世界时钟/太阳能） | **4 种时钟**：`FLIP` 翻页数字 / `THIN` 纤细极简 / `MINIMAL` 极简小钟 / `DATE_FOCUS` 日期日历 | 全部是 Compose 绘制，无图片依赖 |
| 3 种背景（照片/色彩/单一色） | **3 种背景**：`PHOTO` 照片轮播 / `AURORA` 极光动态 / `BLACK` 纯黑极简 | + Ken Burns 慢推拉（开关） |
| 小组件（天气、日历、电池…） | 农历干支小字 + 日期日历表盘（DATE_FOCUS 的月历卡）；不放照片信息 | 待机时前景越少越不容易烧屏 |
| 夜间自动变暗 | `nightDim`：整体 alpha 0.90~1.00 呼吸 + 白色 90% | 目的是**防烧屏**，不只是"暗" |
| 轻点唤醒 | 任意键退出（只退出，不触发动作） | 遥控器语义 |
| 常亮显示 | 屏幕常亮（`FLAG_KEEP_SCREEN_ON`） | 量产需接 DreamService，见 README §7 |

### 5.2 4 种时钟规格（`ScreensaverSettings.ClockStyle`）

| 样式 | label | 主视觉 | 尺寸/字重 | 附属信息 | 锚点 | 气质 |
|---|---|---|---|---|---|---|
| `FLIP` | **翻页数字** | `时:分` 超大数字 | **220sp** ExtraLight，字距 −4sp，行高 ×1.05，`tnum` | 顶部：`星期五 · 九月初五 · 丙午年`（30sp ExtraLight，字距 6sp，α0.62）；底部：`第 292 天 · 星期五`（20sp α0.56，中间 4dp 圆点） | 画面中央 | 机械感、戏剧性；适合家庭大屏"招牌" |
| `THIN` | **纤细极简** | `时:分` 细体数字 | **120sp** Thin；冒号 50% α | 上方：`10月19日 · 星期日`（30sp Light，字距 4sp）；分隔：88×1dp 渐变短线；下方：`上午 · 56 秒`（16sp α0.52） | 画面中央 | 安静、克制 |
| `MINIMAL` | **极简小钟** | 中央大星期 + 月日 | **96sp** Light（字距 10sp）+ **48sp** ExtraLight + 16sp 农历 | 右上角：`● 09:41`（**44sp** Light + 9dp 青点呼吸）+ `2026年10月19日 星期日`（16sp α0.60）；左上：**天气 pill**（不是大卡） | 右上 + 中央偏左（CenterStart + 8dp） | 最有设计感、最耐看 |
| `DATE_FOCUS` | **日期日历** | 大号星期 + 月日 + **月历卡** | **168sp** Light（行高 176sp）+ **54sp** ExtraLight + 16sp 农历 | 小号时间 **46sp** `Accent`（92% α）；月历卡 368dp：年/月头 + 周一开始的 7 列 + 今日青色圈点（填充 18% + 2dp 描边 60%） | 中央偏下（Center + top 48dp） | 信息量最大，"看日期"为主 |

**共同约定**
- 时间颜色统一 `TimeWhite = TextPrimary @ 90%`（**不用纯白**，防烧屏）；
- 所有数字用 `fontFeatureSettings = "tnum"`（等宽），跳字不左右抖；
- 大数字带柔和投影 `Shadow(black 45%, dy=6, blur=26)`，任何背景上都清晰；
- **重组边界**：秒变化只让"冒号/秒点/秒数"这一个 Text 重组（`ClockParts` 分为 `hmKey`/`second`/`dayKey`），日期/农历只在跨天时重算；
- 切换样式时时间无缝衔接（四种样式共用同一份 `ClockParts`）。

### 5.3 3 种背景规格（`ScreensaverSettings.BackgroundStyle`）

| 背景 | label | 实现 | 动效 | 遮罩 |
|---|---|---|---|---|
| `PHOTO` | **照片轮播** | `MediaImage(Crop, targetWidth=1280)` + `Crossfade` | 交叉淡入 = `min(2000ms, 停留时长/2)`；Ken Burns：scale **1.0→1.12**，单程 **26s** 线性往返，每张照片按 url hash 决定推拉方向与 origin（0.35/0.5/0.65） | 34% 起，底部 +22%（≤62%），另叠 18% 径向压暗 |
| `AURORA` | **极光动态** | 纯 `Canvas`：竖向底渐变 `#070B12→#0A0F18→#04060A` + 3 团大径向渐变 + 顶部冷光带 + 46 颗固定星点 + 3px 点阵噪点 | 三团分别 **34s / 47s / 61s** Linear 往返漂移；星点**不闪**（固定种子）；噪点用 `drawWithCache` 预生成 | 30% |
| `BLACK` | **纯黑极简** | 径向 `#0A0D12 → #05060A → #000000`（半径 1500） | 无 | 0%（不加遮罩） |

**降级规则**：`PHOTO` 但照片列表为空 → 自动用 `AURORA`（不崩、不黑屏）。

### 5.4 天气小组件

- 两种形态：**玻璃卡**（FLIP/THIN/DATE_FOCUS，宽 300dp，温度 84sp + 76dp 图标 + 最高/最低/湿度）与 **pill**（MINIMAL，一行，22sp）；
- 卡片 = `GlassCard`：白 5.5% 底 + 竖向白 10%→3%→黑 10% 渐变 + 1.6px 白 13% 描边，圆角 30dp。**刻意不用真实背景模糊**（电视 SoC 代价高）；
- 图标 **Canvas 手绘**（晴 / 多云 / 阴 / 雨 / 雪），不依赖 emoji 字体（避免电视缺字体出现豆腐块），云以 5.2s 周期漂移 ±1.6dp。

### 5.5 防烧屏策略（5 条，全部已实现）

| # | 策略 | 参数 | 原理 |
|---|---|---|---|
| 1 | **像素位移** | 9 个固定位置（中心 + 八方向 ±8dp），每 **2 分钟** 切一次，900ms 平滑过渡 | 打破"静止亮像素"，OLED/等离子都受益；固定位置而非随机，避免"跳" |
| 2 | **夜间呼吸** | 整体 alpha **0.90 ↔ 1.00**，15s 单程（30s 往返） | 亮度过冲降到人眼不可察觉，但足以"移动"OLED 的发光曲线 |
| 3 | **不用纯白** | 时钟白 90%，星点 alpha 0.10~0.42；前景只有时钟与退出提示 | 纯白是烧屏最快来源 |
| 4 | **不闪不闪** | 冒号/秒点用缓动呼吸（420ms / 1400ms）而非硬切；极光星点固定种子不闪烁；噪点预生成 | 避免高频闪烁（视觉疲劳 + OLED 损耗） |
| 5 | **遮罩压暗** | 背景统一 30~45% 黑 + 底部加深；`nightDim` 关闭时呼吸也关闭（省电优先） | 降低整体亮度即降低烧屏速度，同时保证 3m 可读 |

> 量产可加（README §7 P1）：>2h 后整体亮度再降 10~15%、每 30 分钟做 3~5 分钟"文字淡出只留背景"、按 1/2/3 套布局轮换位置。

---

## 6. 动效规范

**总原则**：屏保/背景"慢"（秒级），交互"快"（100~300ms 级）；缓动统一 `FastOutSlowInEasing`（除线性往返的推拉/漂移）；
所有无限动画都用 `rememberInfiniteTransition`（跟随 Choreographer，页面不可见时自动冻结，不耗电）。

### 6.1 交互类（≤ 400ms）

| 动效 | 时长 | 缓动 | 出处 |
|---|---|---|---|
| 焦点放大（`FocusCard`） | **160ms** | 默认（`tween`） | `FocusCard` |
| 焦点描边淡入 | **140ms** | 默认 | `FocusCard` |
| 焦点放大（`TvFocusBox`） | **160ms** | 默认 | `LibraryFocus` |
| 焦点辉光 | **180ms** | 默认 | `LibraryFocus` |
| 设置行焦点 | **140ms** | 默认 | `SettingsFocusRow` |
| 顶栏 Tab 焦点 | 160ms（`TvFocusBox` 默认） | 默认 | `TabPill` |
| 播放器焦点放大 / 描边（`TvFocusSurface`） | **160ms / 140ms** | 默认 | `TvFocusSurface` |
| 开关滑块位移 | **160ms** | 默认 | `TvSwitch` |
| 开关「开/关」文字切换 | 120ms fade | 默认 | `TvSwitch` |
| 设置值切换（旧值左出 / 新值右入） | 180ms in / 140ms out，fade 160/120 | 默认 | `AnimatedValue` |
| 设置行副标题展开 / 收起 | **140ms / 100ms**（fade + expand） | 默认 | `SettingsRow` |
| 设置组标题变色 | **180ms**（`animateColorAsState`） | 默认 | `SettingsGroupHeader` |
| 设置右栏面板切换（按分组） | **fadeIn 200ms / fadeOut 140ms** | `tween` | `SettingsScreen` |
| 设置右栏内边距微调（设计说明展开时） | **200ms**（`animateDpAsState`，32→20dp） | `tween` | `SettingsScreen` |
| 胶片条当前项描边 | **180ms** | 默认 | `ThumbCell` |
| 播放器控制层显隐（`ChromeFade`） | **180ms in / 220ms out** | `tween` | `VideoStage.kt` |
| 播放器上下压暗层（`VideoScrim`） | **200ms in / 220ms out** | `tween` | `VideoScrim` |
| 播放器字幕条显隐 | **180ms / 180ms** | `tween` | `SubtitleBar` |
| 快进/快退 OSD | **110ms in / 220ms out**（提示本身 1.1 秒后由页面清空） | `tween` | `SeekOsd` |
| 播放器右侧信息面板滑入 | **slide 240ms / 200ms + fade 160ms** | `tween` | `InfoPanelSlide` |
| 页面切换（外壳 `AnimatedContent`） | **fadeIn 220ms / fadeOut 160ms** | `tween` | `NasPhotoTvApp` |
| 看图页控制栏显隐 | fade 200/180 + slide 220/200（1/4 高度） | 默认 | `AnimatedVisibilityCompat` |
| 屏保预览里时钟样式切换 | **fadeIn 220ms / fadeOut 160ms** | `tween` | `SettingsScreensaverPanel` |

### 6.2 内容切换类（300~600ms）

| 动效 | 时长 | 缓动 | 出处 |
|---|---|---|---|
| 看图左右切换（按方向滑动 1/3 宽 + 淡入） | slide **320ms**，fade 220/160 | `FastOutSlowInEasing` | `PhotoViewerScreen` |
| 图片出现（scale 0.95→1.0 + 淡入） | **280ms** | `FastOutSlowInEasing` | `PhotoViewerScreen` |
| **屏保翻页数字**（新数字自上滑入 −55% 高 + 淡入；旧数字下滑 30% + 淡出） | slide in **520ms** / fade 340ms；slide out 420ms / fade 260ms | `FastOutSlowInEasing` | `FlipDigitGroup` |
| 屏保冒号呼吸（1s 一周期明暗 0.95/0.20） | **420ms** | `FastOutSlowInEasing` | `FlipClock` |
| 屏保 MINIMAL 秒点呼吸（0.25↔0.95） | **1400ms** 单程（Reverse） | `FastOutSlowInEasing` | `BlinkDot` |
| 屏保像素位移 | **900ms** | `FastOutSlowInEasing` | `PixelShiftBox` |
| 屏保夜间呼吸（alpha 0.90↔1.00） | **15000ms** 单程（30s 往返） | `FastOutSlowInEasing` | `rememberBreathingAlpha` |
| **屏保进入落位**（0.97→1.0 + 淡入） | **1000ms** | `FastOutSlowInEasing` | `EntranceFade` |
| 屏保退出提示淡出 | **600ms** | `FastOutSlowInEasing` | `ExitHint` |

### 6.3 环境类（秒~分钟级，无限循环）

| 动效 | 参数 | 出处 |
|---|---|---|
| 屏保照片交叉淡入 | `min(2000ms, 停留时长/2)`（停留 10s → 2000ms） | `ScreensaverPhotoBackground` |
| Ken Burns 推拉（scale 1.0→1.12，平移 ±14dp / ±10dp） | **26s** 单程，Linear，Reverse | `KenBurnsFrame` |
| 极光三团漂移 | **34s / 47s / 61s**，Linear，Reverse | `AuroraBackground` |
| 天气图标云漂移 | **5200ms** 单程（±1.6dp） | `WeatherIcon` |
| 播放器画面氛围（暗角 + 冷光晕，暂停时冻结） | 控制层 `ChromeFade` 180/220ms；画面暗角为静态 radial（半径 1200），播放中光晕 alpha 0.10 / 暂停 0.05 | `VideoAmbience` |
| 设置右栏 · 屏保预览 Ken Burns | **9000ms** 单程（1.0→1.08，Reverse） | `SettingsScreensaverPanel` |
| 设置右栏 · 极光预览漂移 | **12000ms** 单程（Restart） | `AuroraPreview` |
| 设置背景双层辉光漂移 | **24000ms** 单程（Reverse） | `SettingsBackdrop` |
| 设置右栏 · NAS 拓扑光点流动 | **2600ms** 一轮；测试连接中加速到 **900ms** | `SettingsNasPanel` |
| 看图幻灯片 | 每 **5000ms** 下一张（右下 96×4dp 进度条线性填充） | `SlideshowProgressBar` |
| 视频模拟进度步进 | **100ms**/帧（不是 16ms，避免整树重组耗电） | `VideoTickMs` |
| 屏保照片停留 | `photoIntervalSec`，**限幅 3~600s** | `intervalMs()` |

### 6.4 时长选择的理由（写给人看）

- **160ms** 是"焦点跟得上手指但又看得出"的甜点；再短像卡顿，再长跟不上连续按键；
- **300~520ms** 用于"页面内的内容切换"，让观众理解"东西从哪来、到哪去"（方向性滑动），但不超过一次呼吸；
- **1000ms 以上**只给屏保，因为待机时用户不在操作，慢=高级；
- **所有无限动画必须可冻结**：用 `rememberInfiniteTransition` 或 `graphicsLayer`，暂停/不可见时停止；**不要**用 `while(true){ delay(16) }`。

---

## 7. 可访问性

### 7.1 对比度（WCAG 2.1 AA：正文 ≥ 4.5:1，大字号 ≥ 3:1）

| 组合 | 计算值 | 判定 |
|---|---|---|
| `TextPrimary #F5F7FA` on `Bg #08090D` | ≈ **18.0 : 1** | ✅ AAA |
| `TextSecondary #A9B0BE` on `Bg` | ≈ **9.2 : 1** | ✅ AAA |
| `TextTertiary #6F7787` on `Bg` | ≈ **4.2 : 1** | ⚠️ 仅用于 ≥16sp 且非关键（计数/提示），符合"大字号 3:1"，但接近下限 |
| `Accent #3DDC97` on `Bg` | ≈ **11.0 : 1** | ✅ AAA |
| `AccentAlt #5B8CFF` on `Bg` | ≈ **5.6 : 1** | ✅ AA |
| `Warn #FFC857` on `Bg` | ≈ **12.4 : 1** | ✅ AAA |
| `Danger #FF6B6B` on `Bg` | ≈ **6.0 : 1** | ✅ AA |
| **深青字 `#00210F` on `Accent`** | ≈ **10.7 : 1** | ✅ AAA（所以 Accent 底一定配深青字，不配白字） |
| 屏保时钟 `TimeWhite`(90%) 叠照片 | 最坏情况靠 `Shadow`(黑 45%, blur 26) + 遮罩 34% 兜底 | 规范要求：任何背景下大数字都必须可读 |

> 规范约束：**任何文字叠加在照片上，必须先加 ≥30% 黑遮罩**；小字（13~16sp）不允许直接压在亮部照片上。

### 7.2 字重与字号

- 屏保大数字刻意用 **ExtraLight / Thin**（220sp / 120sp）—— 大字号下细体在 3m 外反而更"锐利"，但要依靠投影保证对比；
- 卡片标题、按钮使用 **Medium / SemiBold**（21sp 以下不要用 Light）；
- 最小字号 **13sp**（`Tiny`）只用于"不必读也能用"的信息（计数、键位提示）；
- 中文字距：大标题 `0`，全大写/数字类 `tnum`，屏保大数字 `−4sp`（视觉收紧）。

### 7.3 色弱与色盲

- **不单靠颜色传达状态**：
  - 焦点 = 放大 + 描边 + 阴影（3 条线索）；
  - 选中 Tab = `Accent` 底 **+** 深青字 **+** 无描边（形状差异）；
  - 收藏 = 星形图标（不是"变黄"）；视频 = `▶` 图标 + 时长（不是"变蓝"）；
  - 在线/离线 = 圆点 **+** 文案「在线/离线」。
- **红绿色弱友好**：主强调青（#3DDC97）与危险红（#FF6B6B）在 deuteranopia 下明度差仍大（L 约 71% vs 60%）且形状不同（圆形进度/关闭 ✕ 图标）；
- **蓝色弱（tritan）**：星蓝只用于次级标识，从不承担唯一语义。

### 7.4 动画与视觉疲劳

- 客厅场景默认开启"安静模式"：无弹窗、无闪烁、无自动跳转（除用户主动开启的幻灯片/自动连播）；
- 屏保的 `nightDim` 关闭时，呼吸与位移都停止（夜间看片的人不想被"呼吸"打扰）；
- 所有动画时长 ≥ 120ms，不提供"瞬时硬切"（硬切在电视屏上会在残影里看出轮廓）。

### 7.5 遥控器可达性（"可访问性"的另一半）

- 每个可操作元素都必须**能被方向键走到**，不存在只能"按快捷键"的功能；
- 键位提示直接写在按钮上（`KeyHint` 胶囊：`◀ ▶ ⓘ ★ ⏵ ⛶ ESC`），新手不用记；
- 每个页面都有可见的**返回路径**（返回按钮 / BACK / 关闭按钮三者至少其一，实际上都有）；
- 屏保退出不需要"找到按钮"：任意键即退出，且第一次按键不触发误操作。

### 7.6 待补齐（量产前）

- [ ] `contentDescription` 完备：目前卡片/按钮已带标题或标签，但 `EmptyState` 图标、装饰性 Box 未标注（应 `null` 或合并语义）；
- [ ] TalkBack 焦点顺序与视觉顺序一致（网格是 6 列，TalkBack 会按组合顺序走）；
- [ ] 关键文字的**动态字号**支持（系统"字体大小"设置）；
- [ ] 高对比模式（色弱/低视力用户）：可提供 `TvColor` 的高对比变体（描边 4dp、文字纯白）；
- [ ] 字幕样式可调（字号/底框/位置），对标电视厂商的字幕设置。

---

## 8. 附录：实现索引

| 规范条目 | 代码位置 |
|---|---|
| 颜色 / 尺寸 / 字号 / 焦点数值 | `ui/theme/Tokens.kt` |
| 圆角 Shape、M3 映射 | `ui/theme/Theme.kt` |
| 焦点三件套（公共） | `ui/common/FocusCard.kt`（1.08/1.12 + 3dp 白 + 24dp 青光） |
| 焦点三件套（库页变体）+ 滚动跟随 | `ui/library/LibraryFocus.kt`（`TvFocusBox` / `scrollRowTo` / `scrollGridTo`） |
| Hero / 卡片 / 顶栏 / 空状态尺寸 | `ui/library/LibraryComponents.kt` |
| 网格列数与单元格尺寸 | `ui/library/LibraryScreen.kt`（`ALBUM_GRID_COLUMNS=4`、`MEDIA_GRID_COLUMNS=6`、244×158、408×200） |
| 看图页布局与按键 | `ui/viewer/PhotoViewerScreen.kt`（`ChromeAutoHideMs=3000`、`SlideshowIntervalMs=5000`、`PanStep=0.5`） |
| 信息条 / 胶片条 | `ui/viewer/ViewerInfoBar.kt`（132×78、560dp padding、104dp 高） |
| 工具按钮 / 键位胶囊 | `ui/viewer/ViewerToolBar.kt`（56dp、1.12、30×22） |
| 播放页布局与按键映射 | `ui/video/VideoPlayerScreen.kt`、`ui/video/VideoKeyHandler.kt`（`VideoSeekStepMs=10000`、倒计时 5s、`VideoTickMs=100`、分区焦点） |
| 播放器控件尺寸 | `ui/video/VideoControls.kt`（返回 52dp、播放键 112dp、底栏 52dp、进度条 12/20dp） |
| 播放器叠加层 | `ui/video/VideoOverlays.kt`（信息面板 520dp、连播角标、结束面板）、`ui/video/VideoStage.kt`（压暗/字幕/OSD） |
| 播放器焦点外框 | `ui/video/TvFocusSurface.kt`（页面状态驱动，160/140ms） |
| **4 种时钟** | `ui/screensaver/ScreensaverClock.kt` |
| **3 种背景 + 遮罩 + Ken Burns** | `ui/screensaver/ScreensaverBackground.kt` |
| **防烧屏（位移/呼吸）** | `ui/screensaver/ScreensaverScreen.kt`（`PIXEL_SHIFT_POSITIONS`、`PixelShiftBox`、`ExitHint`） |
| 时钟时间/农历/tabular 样式 | `ui/screensaver/ScreensaverState.kt` |
| 天气卡 / pill / Canvas 图标 | `ui/screensaver/ScreensaverWeather.kt` |
| 设置行 / 值胶囊 / 开关 / 进度条 | `ui/settings/SettingsFocusRow.kt`、`ui/settings/SettingsChips.kt`、`ui/settings/SettingsRow.kt` |
| 设置页分组与 30 行的地址 | `ui/settings/SettingsModel.kt`（`SettingsGroup` / `SettingsKey` / `SettingsActions` / `SettingsFeedback`） |
| 设置右栏四面板 | `SettingsNasPanel.kt` / `SettingsScreensaverPanel.kt` / `SettingsPlaybackPanel.kt` / `SettingsAboutPanel.kt` |
| 屏保 4 时钟 × 3 背景的开关语义 | `data/model/Models.kt`（`ScreensaverSettings`）、`data/SettingsStore.kt`（`cycleClockStyle` / `cycleScreensaverBackground` / `cyclePhotoInterval` / `cycleIdleTimeout`） |

### 快捷验证：把设计稿和实现对上

1. Android Studio 打开工程 → 打开任一 Screen 文件的右侧 Preview（全部是 `@Preview(widthDp = 1920, heightDp = 1080)`）；
2. 屏保 4 个 Preview 分别覆盖 FLIP+PHOTO、DATE_FOCUS+AURORA、MINIMAL+BLACK、THIN+PHOTO —— 正好是 4×3 矩阵的 4 个代表格；
3. 想验证另外 8 种组合：改 `ScreensaverSettings(clockStyle = …, background = …)` 的参数即可实时看到。
