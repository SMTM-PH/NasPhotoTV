# NAS Photo TV —— 原型开发协作契约（READ ME FIRST）

> Android TV (Kotlin + Jetpack Compose) 原型。所有队友必须遵守本文件，保证各部分能直接拼起来编译通过。

## 0. 工程速览
- 包名 `com.nasphoto.tv`，根目录 `E:\NasPhotoTV`，模块 `app`
- 编译验证命令（**禁止并行跑多个 gradle，先 `Get-ChildItem E:\NasPhotoTV\.gradle\lockcheck` 之外只允许一个 gradle 进程，用合作文件锁**）：
  ```
  E:\NasPhotoTV\gradlew.bat -p E:\NasPhotoTV :app:compileDebugKotlin -q
  ```
  规则：启动 gradle 前先 `New-Item E:\NasPhotoTV\.buildgate -ItemType File -ErrorAction SilentlyContinue` 式抢锁，用
  ```
  while (-not (New-Item -Path E:\NasPhotoTV\.buildgate -ItemType Directory -ErrorAction SilentlyContinue)) { Start-Sleep -Seconds 5 }
  ```
  跑完立刻 `Remove-Item E:\NasPhotoTV\.buildgate -Recurse -Force`（用 try/finally 思路，一条命令里写）。
- 只跑 `:app:compileDebugKotlin` 就够，不要 `assembleDebug`（Lead 会做最终构建）。
- 下载依赖可能较慢，单次编译最长可能 3-8 分钟，`timeoutMs` 给 600000。

## 1. 已存在、可直接使用的公共代码（**不要修改**，如需改动先在群里找 Lead）
```
ui/theme/Tokens.kt        TvColor / TvDimen / TvType 设计 token
ui/theme/Theme.kt         NasPhotoTvTheme(), TvShapes
data/model/Models.kt      MediaItem, Album, AlbumType, MediaType, NasConnection, ScreensaverSettings, PlaybackSettings, WeatherInfo
data/MediaRepository.kt   MediaRepository 接口
data/mock/MockMedia.kt    MockMedia.loadImage(url, targetWidth): ImageBitmap? ; AppContextHolder.context
ui/common/MediaImage.kt   MediaImage(url, contentDescription, modifier, contentScale, targetWidth, cornerRadiusDp)
```
`MediaItem.url` 形如 `"raw/wall_aurora"`，`MediaImage` 能直接加载；缺失时自动渐变占位。

设计 token 关键值：
- 背景 `TvColor.Bg`(0xFF08090D)；卡片 `TvColor.Surface`；文字 `TextPrimary/TextSecondary/TextTertiary`
- 强调色 `TvColor.Accent`(极光青 0xFF3DDC97)；次强调 `AccentAlt`(星蓝 0xFF5B8CFF)；`Danger`
- 边距 `TvDimen.ScreenPadding=64.dp`，圆角 `RadiusS/M/L/XL = 10/16/24/32.dp`，间距 SpaceXs/S/M/L/Xl/Xxl = 8/12/20/32/48/72.dp
- 字号 `TvType.Display=56sp, Section=30sp, CardTitle=22sp, Body=20sp, Caption=16sp, Tiny=13sp`
- 焦点：放大 `TvDimen.FocusScale=1.08f`，白色 3dp 描边 `TvColor.FocusRing`，卡片聚焦时加青色辉光 `TvColor.FocusRingGlow`

## 2. 依赖
已加入：compose-bom 2024.12.01（Compose 1.7.6 / material3 1.3.1）、material-icons-extended、core-ktx、activity-compose、lifecycle-runtime-compose、lifecycle-viewmodel-compose、ui-tooling。
**没有** TV 专用库（tv-foundation/tv-material）、没有 media3、没有 coil、没有 navigation-compose 之外的导航库。
导航用最朴素的 `when(route)` + 自己写的状态；焦点用 `Modifier.focusRequester/focusable` + `onFocusChanged`（不依赖 tv-foundation 的 TvLazyRow）。
如需新依赖，先找 Lead；**默认不允许新增依赖**（要加必须能保证能下载到、并通知全组）。

## 3. 目录与文件所有权（各人只改自己名下的文件）
| 文件 | 负责人 |
|---|---|
| `ui/theme/*` `data/model/*` `data/MediaRepository.kt` `data/mock/MockMedia.kt` `ui/common/MediaImage.kt` `TvContract.kt` `MainActivity.kt` `ui/NasPhotoTvApp.kt` `ui/AppState.kt` `ui/common/FocusCard.kt` | **Lead** |
| `data/mock/MockData.kt` `data/RepositoryProvider.kt` `data/SettingsStore.kt` `data/mock/MockWeatherProvider.kt` | **data-engineer** |
| `ui/library/LibraryScreen.kt` `ui/library/*` | **library-dev** |
| `ui/viewer/PhotoViewerScreen.kt` `ui/viewer/*` | **viewer-dev** |
| `ui/viewer/VideoPlayerScreen.kt` `ui/video/*` | **video-dev** |
| `ui/screensaver/ScreensaverScreen.kt` `ui/screensaver/*` | **screensaver-dev** |
| `ui/settings/SettingsScreen.kt` `ui/settings/*` | **settings-dev** |
| `README.md` `DESIGN.md` `docs/*` 设计文档与截图 | **docs-writer** |
| `store/` 商店长图 | **store-artist** |

不要改别人的文件；需要别人加方法时找 Lead 协调。

## 4. 屏幕入口签名（严格照此实现，Lead 在外壳里调用）
```kotlin
// 媒体库（首页 + 相册 + 文件夹浏览）
@Composable
fun LibraryScreen(
    state: LibraryState,                 // 见 TvContract.kt（Lead 提供结构，library-dev 负责创建文件但按契约填字段）
    onOpenPhoto: (items: List<MediaItem>, index: Int, contextTitle: String) -> Unit,
    onOpenVideo: (items: List<MediaItem>, index: Int, contextTitle: String) -> Unit,
    onNavigate: (AppRoute) -> Unit,      // 跳转设置 / 屏保预览
    modifier: Modifier = Modifier,
)
```
```kotlin
// 图片浏览器
@Composable
fun PhotoViewerScreen(
    items: List<MediaItem>,
    initialIndex: Int,
    contextTitle: String,
    onClose: () -> Unit,
    onOpenVideo: (MediaItem, List<MediaItem>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
)
```
```kotlin
// 视频播放器
@Composable
fun VideoPlayerScreen(
    items: List<MediaItem>,
    initialIndex: Int,
    contextTitle: String,
    settings: PlaybackSettings,
    onClose: () -> Unit,
    onOpenPhoto: (items: List<MediaItem>, index: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
)
```
```kotlin
// 屏保（待机显示）
@Composable
fun ScreensaverScreen(
    settings: ScreensaverSettings,
    photos: List<MediaItem>,
    weather: WeatherInfo,
    onExit: () -> Unit,          // 遥控器任意键退出
    modifier: Modifier = Modifier,
)
```
```kotlin
// 设置
@Composable
fun SettingsScreen(
    store: SettingsStore,
    onClose: () -> Unit,
    onPreviewScreensaver: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
)
```

## 5. 遥控器交互规范（电视端，重点！）
- `DPAD_UP/DOWN/LEFT/RIGHT` 移动焦点；`ENTER/DPAD_CENTER` 确认；`BACK` 返回上一级（Viewer/播放器/设置里 BACK=关闭）。
- **任何可聚焦元素都必须有肉眼可见的焦点态**：放大 + 白色描边（或青色描边 + 光晕），不能只靠颜色变化。
- 焦点放大要预留空间，避免被父容器 clip 切边（父容器加 `padding` 或 `clipToBounds=false`）。
- 列表用 `LazyRow`/`LazyColumn` + `focusRequester` 手动控制初始焦点；不要依赖 Compose 默认焦点顺序做复杂网格。
- 网格建议：`LazyRow` 分页（每页 N 列）或 `LazyVerticalGrid`，必须支持左右在行内移动、上下跨行。
- 列表滚动要让聚焦项"可见"：用 `LazyListState.animateScrollToItem(聚焦项 - 半屏偏移)`。
- 长按/双击不支持；一次性操作直接 ENTER。
- 屏保：任意键（含方向键/返回/音量）退出，但**第一次按键只退出，不能同时触发页面动作**。

## 6. 编码规范
- Kotlin，Compose，`@Composable` 尽量拆分小组件；文件内 private 函数放底部。
- 所有中文文案直接写中文字符串（不用 stringRes，原型优先速度）。
- 所有颜色/字号/间距用 `ui/theme/Tokens.kt` 里的 token。
- 每个 Screen 至少提供一个 `@Preview(widthDp = 1920, heightDp = 1080)`。
- 不要用 `java.time` 以外的新 API 之外的东西；minSdk 26，可以用 java.time。
- 不要写网络请求；所有数据来自 repository/mock。
- 代码里不要留 TODO 到崩溃的路径（LazyRow 空列表要处理）。

## 7. 协作方式
- 每个人完成后：1) 自己跑通 `:app:compileDebugKotlin`；2) 用 `send_message` 给 Lead 汇报：完成文件列表、公开函数签名、需要 Lead 注意的点、编译是否通过。
- 如果发现契约有问题、或需要 Lead 改公共文件，立刻 `send_message` 给 lead，不要擅自改公共文件。
- 汇报格式简短：
  ```
  DONE <你的名字>
  files: ...
  compile: PASS/FAIL(原因)
  notes: ...
  ```
