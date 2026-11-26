# NAS Photo TV · 外观模式设计（浅色 / 深色）

> 版本：1.0 · 2026-11-25 · 适用 Android TV（Jetpack Compose）与 Apple TV（SwiftUI）
> 配套文件：[preview.html](preview.html)（可交互视觉稿）、[tokens.css](tokens.css)、[tokens.json](tokens.json)

---

## 0. 一句话结论

给这个「客厅里的电子相框」补两套外观：

| 模式 | 名称 | 定位 | 底色 |
|---|---|---|---|
| 深色 | **影院黑 Cinema** | 默认、线上现有值 | `#06131E` 深空黑 |
| 浅色 | **画廊白 Gallery** | 白天 / 明亮客厅 | `#E9EEF4` 晨雾白 |

**最重要的决定**：只有「浏览界面」跟着模式变；**看图、播放、屏保、沉浸式舞台永远保持深色**（下称「固定暗场」）。照片是主角，UI 不能因为切了外观就变成一块刺眼的白板。

---

## 1. 为什么电视端的浅色模式不能照搬手机

10-foot UI（三米外、坐着、遥控器）和手机 / 桌面有三点本质差异，直接决定了配色：

1. **不能出现大面积纯白**。OLED 电视全屏纯白既刺眼又有烧屏风险，夜间客厅更明显。所以浅色底用 `#E9EEF4`（约 84% 亮度）的冷调「晨雾白」，卡片才用纯白 `#FFFFFF` —— 像墙上挂了带白卡纸的相框，白只出现在小面积上。
2. **文字对比度要比 WCAG 最低线更宽裕**。电视有色偏、有环境光反射、观看距离远，本设计把所有正文色对四种底色都压到 **≥ 4.5:1**，重点文字 ≥ 6:1，焦点环 ≥ 9:1（见 §7）。
3. **焦点不能只靠颜色**。深色用「白色 3dp 描边 + 冰蓝辉光」；浅色里白描边会消失，必须翻转成 **深墨蓝 `#0B3A5C` 描边 + 淡墨色投影**，同时保留 1.05 放大。这一条是遥控器可用性的底线。

另外，浅色模式下品牌主色必须**加深**。线上的冰蓝 `#31B6ED` 在 `#E9EEF4` 上只有约 2.4:1，做文字根本读不清；浅色模式改用同色相的深冰蓝 `#096494`（5.52:1）。色相不变，品牌不丢。

---

## 2. 三种用户可选状态

```
TvAppearance
├── System  跟随系统（默认）   Android: Configuration.uiMode / tvOS: colorScheme
├── Light   画廊白           强制浅色
└── Dark    影院黑           强制深色
```

- **默认值 = 深色（影院黑）**，与线上现值完全一致，升级后不会突然变白；「跟随系统」由用户主动选择。
- `System` 的映射：系统夜间模式 → 影院黑，系统日间模式 → 画廊白，`UI_MODE_NIGHT_UNDEFINED` → 回落影院黑。
- 切换必须**立即生效**，不需要重启 Activity：主题在根 `NasPhotoTvTheme` 读取可观察状态，子组件自动重组。
- 偏好写入 `SharedPreferences` / `UserDefaults`，键名 `appearance`，取枚举名字符串（与项目现有 `enumOrDefault` 兼容）。

---

## 3. 完整 Token 表

字段名与 `Tokens.kt` 的 `TvColor` **一一对应**，实现时不需要改任何调用方。

### 3.1 模式相关 token（随外观翻转）

| Token | 深色 · 影院黑 | 浅色 · 画廊白 | 角色 / 用途 |
|---|---|---|---|
| `Bg` | `#06131E` | `#E9EEF4` | 全局底色 / 页面背景 |
| `BgElevated` | `#12141B` | `#F3F6FA` | 抬升面：弹层、面板、空状态底 |
| `Surface` | `#1A1D26` | `#FFFFFF` | 卡片底、顶栏按钮底（相框白卡纸） |
| `SurfaceHigh` | `#252935` | `#DDE5EE` | 聚焦行底、开关轨道、按下态 |
| `SurfaceGlass` | `rgba(255,255,255,.40)` | `rgba(255,255,255,.70)` | 玻璃胶囊底（压在照片上） |
| `SurfaceSoft` | `#122A3A` | `#D7E6F1` | 柔和功能面 / 选中胶囊 |
| `Stage` | `#0B202F` | `#DDE7F0` | 照片舞台 / 面板底（浏览界面跟随外观；看图等由 `ProvideTvStage` 强制深色） |
| `Divider` | `rgba(255,255,255,.12)` | `rgba(18,38,58,.12)` | 1dp 描边、分隔线、进度条轨道 |
| `TextPrimary` | `#F5F7FA` | `#0E1A28` | 主文字（标题、正文） |
| `TextSecondary` | `#A9B0BE` | `#44566B` | 次要文字（日期、元信息） |
| `TextTertiary` | `#8C97A8` ⚠️ | `#546579` | 三级文字（计数、提示、13sp） |
| `TextMuted` | `#8D9AA8` | `#516376` | 屏保 / 舞台上的弱化文字 |
| `Warm` | `#CDBA98` | `#755A34` | 暖色辅助（收藏、氛围文字） |
| `Accent` | `#31B6ED` | `#096494` | **品牌主色**：可操作 / 选中 / 成功 / 辉光 |
| `AccentDim` | `rgba(49,182,237,.20)` | `rgba(9,100,148,.12)` | 扩展名角标底、淡底 |
| `AccentAlt` | `#5B8CFF` | `#3557B8` | **次强调**：视频、智能相册、极光背景 |
| `AccentAltDim` | `rgba(91,140,255,.20)` | `rgba(53,87,184,.12)` | 同上淡底 |
| `InkOnAccent` | `#002232` | `#FFFFFF` | **主色填充上的文字 / 图标**（必须翻转） |
| `Danger` | `#FF6B6B` | `#AF2E26` | 关闭、离线、错误 |
| `Warn` | `#FFC857` | `#8A5700` | 收藏星标、太阳图标 |
| `FocusRing` | `#FFFFFF` | `#0B3A5C` | **焦点描边 3dp**（必须翻转） |
| `FocusRingGlow` | `rgba(49,182,237,.50)` | `rgba(11,58,92,.20)` | 深＝冰蓝辉光 / 浅＝淡墨投影 |

> ⚠️ `TextTertiary` 深色值从线上的 `#6F7787` 提亮到 `#8C97A8`：原值在 `Bg` 上只有 4.17:1、在 `SurfaceHigh` 上只有 3.22:1，不满足 13sp 正文的 AA 4.5:1。这是本次对深色模式的**唯一**取值修正，是纯增益，不改层次关系（仍明显弱于 `TextSecondary`）。若要求线上像素级一致，可保留原值并在文档标注为已知可访问性欠账。

### 3.2 压在照片上的常量（两套模式共用，不翻转）

| Token | 值 | 用途 |
|---|---|---|
| `CinemaPanel` | `rgba(8,29,43,.80)` | 影院浮层、控制条底 |
| `ScrimTop` | `rgba(0,0,0,.70)` | 照片顶部压暗 |
| `ScrimBottom` | `rgba(0,0,0,.85)` | 照片底部压暗、信息条渐变终点 |
| `TimeWhite`（派生） | `rgba(245,247,250,.90)` | 屏保时钟白，防烧屏不用纯白 |

这些值在两套模式里**字面相同**，因为它们永远压在照片上，与外观无关。

**固定暗场怎么落地**：`Stage` 本身是普通语义 face（会随模式翻转，见 §3.1），
所以看图 / 播放 / 屏保 / 画册由 `ProvideTvStage` 在**子树层面强制切回 `DarkPalette`**，
那一整棵子树的所有 token（含 `Stage`）都是深色。换句话说，「固定暗场」是
**子树级调色板覆盖**，不是把 `Stage` 写死 —— 这样浏览界面（设置右栏、分类 Tab、
相册卡片）在浅色下才不会残留大块深色，避免「半亮半暗」。舞台深色值恒为 `#0B202F`。

### 3.3 不随模式变化的尺寸 / 字号

`TvDimen`、`TvType`、`TvShapes`、`TvImage` 全部不变 —— 外观模式只换颜色，不动 10-foot 的密度、圆角和字阶。焦点放大比例 `1.05f / 1.12f`、焦点描边 `3dp` 同样不变，只换描边颜色。

---

## 4. 半透明色的重新定义（最容易出错的地方）

线上很多颜色是「白色 + alpha」：`SurfaceGlass`、`Divider`、`AccentDim`、`AccentAltDim`、`FocusRingGlow`。浅色模式**不能只把底层色值换掉**就完事 —— 白 12% 的分隔线在白底上等于没有。因此：

| 类别 | 深色 | 浅色 | 规则 |
|---|---|---|---|
| 描边 / 分隔 | 白 `alpha .12` | 墨 `#12263A` `alpha .12` | 换成**深色墨**做 alpha |
| 玻璃 | 白 `.40` | 白 `.70` | 提不透明度，浅底上才看得见 |
| 辉光 / 阴影 | 主色 `.50` glow | 墨 `.20` shadow | 从「发光」语义改为「投影」语义 |
| 主色淡底 | 主色 `.20` | 主色 `.12` | 淡底更收敛，避免浅底变脏 |
| 照片压暗 | 黑 `.70 / .85` | **不变** | 压在照片上，与外观无关 |

`ScrimTop` / `ScrimBottom` / `CinemaPanel` 永远压在照片上，保持黑，不参与翻转。

---

## 5. 焦点规范（翻转表）

| | 深色 | 浅色 |
|---|---|---|
| 描边 | `#FFFFFF` 3dp | `#0B3A5C` 3dp |
| 外发光 | 冰蓝 `rgba(49,182,237,.50)` | 淡墨 `rgba(11,58,92,.20)` 投影 |
| 放大 | `1.05f`（大卡 `1.12f`） | 同左，**不变** |
| 聚焦行底 | `SurfaceHigh #252935` | `SurfaceHigh #DDE5EE` |
| 主色按钮 | 冰蓝底 + 深墨字 | 深冰蓝底 + 白字 |

遥控器可达性要求（契约 §5）：**任何可聚焦元素都必须有肉眼可见的焦点态**，不能只靠颜色变化。浅色下白描边失效是本次最容易漏的回归点，务必用 `TvFocusRing` / `TvFocusStyle` 统一取 token，不要在组件里硬编码白色。

---

## 6. 固定暗场：哪些页面不跟着变

| 页面 | 模式 | 原因 |
|---|---|---|
| 媒体库 / 相册 / 人物 / 地点 | 跟随 | 浏览界面，是「墙面」 |
| 设置 / 连接 / 所有弹层 | 跟随 | 浏览界面 |
| **全屏看图** `PhotoViewerScreen` | **恒暗** | 照片是主角；白舞台会让照片发灰、切页白闪 |
| **视频播放** `VideoPlayerScreen` | **恒暗** | 影院语义 |
| **屏保** `ScreensaverScreen` | **恒暗** | 夜间客厅；白色屏保会造成眩光与烧屏 |
| **艺术画册** `ArtAlbumScreen` | **恒暗** | 满屏照片 |
| 沉浸式 Hero 的**照片区** | 由照片铺满 | 容器 `Stage` 底色跟着模式变，但被照片完全覆盖，视觉上仍是照片主角 |

**过渡规则**：从浅色浏览界面进入看图时，允许直接切到深色（不做交叉淡入到白），因为照片本身是满屏内容；从看图返回时反向切回。不要做「整个 UI 从白渐变到黑」的动画，那会在电视上非常晃眼。

---

## 7. 对比度验证（WCAG 2.1 AA，正文 ≥ 4.5:1）

按 sRGB 相对亮度计算，四种底色全部覆盖（`Bg / BgElevated / Surface / SurfaceHigh`）：

### 浅色 · 画廊白

| 前景 | Bg | BgElevated | Surface | SurfaceHigh | 结论 |
|---|---|---|---|---|---|
| `TextPrimary` | 15.04 | 16.18 | 17.54 | 13.80 | ✅ |
| `TextSecondary` | 6.45 | 6.95 | 7.53 | 5.92 | ✅ |
| `TextTertiary` | 5.12 | 5.51 | 5.98 | 4.70 | ✅ |
| `TextMuted` | 5.30 | 5.70 | 6.18 | 4.86 | ✅ |
| `Accent` | 5.52 | 5.94 | 6.44 | 5.06 | ✅ |
| `AccentAlt` | 5.61 | 6.04 | 6.55 | 5.15 | ✅ |
| `Danger` | 5.55 | 5.98 | 6.48 | 5.10 | ✅ |
| `Warn` | 5.23 | 5.63 | 6.10 | 4.80 | ✅ |
| `Warm` | 5.51 | 5.93 | 6.43 | 5.05 | ✅ |
| `FocusRing`（对 Surface） | — | — | 11.84 | 9.31 | ✅ |

填充上的文字：`#FFFFFF` on `Accent #096494` = **6.44:1**；on `AccentAlt #3557B8` = **6.55:1**；on `Danger #AF2E26` = **6.48:1**。全部 ≥ 4.5:1。

### 深色 · 影院黑

| 前景 | Bg | BgElevated | Surface | SurfaceHigh | 结论 |
|---|---|---|---|---|---|
| `TextPrimary` | 17.48 | 17.14 | 15.68 | 13.52 | ✅ |
| `TextSecondary` | 8.61 | 8.44 | 7.73 | 6.66 | ✅ |
| `TextTertiary`（修正后） | 6.35 | 6.23 | 5.70 | 4.91 | ✅ |
| `TextMuted` | 6.54 | 6.41 | 5.87 | 5.06 | ✅ |
| `Accent` | 8.08 | 7.92 | 7.25 | 6.25 | ✅ |
| `AccentAlt` | 5.93 | 5.82 | 5.32 | 4.59 | ✅ |
| `Danger` | 6.76 | 6.63 | 6.07 | 5.23 | ✅ |
| `Warn` | 12.19 | 11.96 | 10.94 | 9.43 | ✅ |

填充上的文字：`#002232` on `Accent #31B6ED` = **7.09:1**。

### 固定暗场

`TextPrimary` on `Stage #0B202F` = **15.51:1**，`Accent` = **7.17:1**，`FocusRing #FFFFFF` = **16.64:1**。

---

## 8. 切换与持久化实现约定

```
SettingsStore.appearance : TvAppearance   (mutableStateOf, 可观察)
        │
        ▼
NasPhotoTvTheme(appearance = SettingsStore.appearance)
        │  解析 System → (Configuration.uiMode & UI_MODE_NIGHT_MASK)
        ▼
ProvideTvPalette(palette)  →  CompositionLocal<LocalTvPalette>
        │
        ├── 浏览子树：LocalTvPalette = Light / Dark
        └── ProvideTvStage { ... } → 强制 DarkPalette（看图 / 播放 / 屏保）
        │
        ▼
TvColor.* 读取 LocalTvPalette.current；MaterialTheme 用对应 light/darkColorScheme
```

- `TvColor` 保持**对象 + 同名属性**，业务代码 38 个文件里的 `TvColor.Bg` 一概不改（契约 §6「禁止魔法色值」继续成立）。
- 顶层常量（如 `TimeWhite`、`StageBackground`）不能在类加载时读 `TvColor.*`，否则会固化成启动时的那套配色 —— 固定暗场一律写死为深色常量。
- `MaterialTheme.colorScheme` 必须同时切换 `lightColorScheme` / `darkColorScheme`，否则 M3 组件（`CircularProgressIndicator`、Slider、Switch 等）仍是深色。
- 系统夜间模式变化（`uiMode` 变更导致 Activity 重组或配置变更）要能触发根主题重新解析。
- `res/values/themes.xml` 的 `windowBackground` 保持深色，避免冷启动第一帧白闪；Compose 首帧就画正确底色。

---

## 9. 实现索引

| 平台 | 文件 | 内容 |
|---|---|---|
| Android | `ui/theme/Appearance.kt` | `TvAppearance` / `TvPalette` / `DarkPalette` / `LightPalette` / `LocalTvPalette` / `ProvideTvStage` / M3 映射 |
| Android | `ui/theme/Tokens.kt` | `TvColor` 改为从 `LocalTvPalette` 取值，字段名不变 |
| Android | `ui/theme/Theme.kt` | `NasPhotoTvTheme(appearance)` 重载 + 保留无参重载（默认深色，兼容所有 `@Preview`） |
| Android | `data/SettingsStore.kt` | `appearance` 状态、`setAppearance` / `cycleAppearance`、`persist` / `restore` 的 `appearance` 键 |
| Android | `ui/settings/SettingsAppearancePanel.kt` | 设置页「外观」右栏：用真实 token 并排渲染两套配色的迷你预览 |
| Android | `ui/settings/SettingsModel.kt` `SettingsRow.kt` `SettingsScreen.kt` | 新增 `APPEARANCE` 组与「外观模式」行，循环「跟随系统 → 浅色 → 深色」 |
| Android | `ui/NasPhotoTvApp.kt` | 根主题传入偏好；看图 / 播放 / 画册 / 屏保包 `ProvideTvStage` |
| Android | `ui/theme/TvFocusRing.kt`、`LibraryFocus.kt`、`ScreensaverBackground.kt`、`ScreensaverClock.kt`、`PhotoViewerScreen.kt`、`SettingsScreen.kt`、`SettingsScreensaverPanel.kt` | 把 `DrawScope` 里的 `TvColor.*` 改成「组合期取色 → 局部值捕获」，并把顶层 `val` 固定暗场常量写死 |
| tvOS | **未落盘** | 本次会话账号对 `apple/` 只读、且无 Xcode 工具链；完整可粘贴实现见 [tvOS.md](tvOS.md) |
| 文档 | `docs/appearance/*` | 本文件、[preview.html](preview.html) 视觉稿、token 导出、tvOS 接入说明 |

> **实现注意**：`TvColor.*` 现在是 `@Composable @ReadOnlyComposable` 的 getter，
> 只能在 Composable 上下文里读。`Canvas { }` / `drawBehind { }` / `drawWithContent { }`
> 的 `DrawScope` lambda 不是 Composable 上下文，必须先在外层取成局部 `Color` 再捕获；
> 顶层 `val` 的 initializer 同理（会固化成启动时的调色板），固定暗场一律写死深色常量。

---

## 10. Do / Don't

**Do**

- 新增视觉先改 token，组件按「角色」选 token（`Surface` = 卡片底，`Accent` = 可操作，`Warn` = 收藏）。
- 浅色卡片用纯白 + 极淡墨色投影制造层次；深色卡片用抬升亮度 + 辉光。
- 焦点态始终「放大 + 描边 + 光晕 / 投影」三件套。
- 照片上叠的任何东西都用黑 scrim，不随外观变。

**Don't**

- ❌ 浅色模式用 `#FFFFFF` 做全屏底色。
- ❌ 把 `#31B6ED` 直接当浅色模式的文字色（2.4:1，读不清）。
- ❌ 浅色模式保留白色焦点描边（等于没有焦点）。
- ❌ 忘记翻转 `InkOnAccent`（深墨字压在深冰蓝按钮上会糊成一团）。
- ❌ 让屏保 / 看图跟着变浅。
- ❌ 在页面里写死 `Color.White` / `Color(0xFF...)` 当背景或焦点描边。
- ❌ 顶层 `val`  initializer 里读 `TvColor.*`。

---

## 11. 与现有文档的关系

- `DESIGN.md` §2.1 记录的是早期调色板（`Accent #3DDC97` 极光青、`Bg #08090D`），**代码中的 `Tokens.kt` 已演进为冰蓝 `#31B6ED` / `#06131E`**。本设计以**代码现值为准**作为深色模式基线，`DESIGN.md` 的色表需要后续同步（不在本次改动范围，避免与在途工作冲突）。
- 本文件只新增 `docs/appearance/`，不修改 `DESIGN.md` / `CONTRACT.md`。
- 浅色 / 深色的完整视觉对照见 [preview.html](preview.html)。
