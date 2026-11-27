# AGENTS.md —— NasPhotoTV 仓库上手指南（给 AI Coding Agent）

> 面向未来接手的 AI 编码 agent。所有结论均来自当前仓库代码与文档核对；
> 文档中过时的部分会在文中显式标注，遇到冲突**以代码为准**。

---

## 1. 项目概览

在电视上看飞牛 NAS 里的照片与视频，并把电视变成会呼吸的电子相框。

| 项 | 事实 |
|---|---|
| Android 客户端 | `app/`，包名 `com.nasphoto.tv`，Kotlin + Jetpack Compose，**单 Activity**（`MainActivity.kt`），无 Fragment、无 navigation-compose |
| 版本 | Android `1.2.15`（versionCode 21，`app/build.gradle.kts`）；tvOS `1.2.15`（build 21，`apple/`） |
| tvOS 客户端 | `apple/NasPhotoTV/`，SwiftUI，见 `apple/README.md` |
| 数据源 | **真实飞牛 fnOS NAS**：局域网发现、WSS 登录、签名 HTTP、AccessToken 串流原片；不是 Mock |
| 设计画布 | 10-foot UI，按 960×540 dp 归一化（`ui/theme/TvDensity.kt`），1920×1080 为验收基准 |
| 协议 | Apache-2.0（`LICENSE` / `NOTICE`） |

> 注意：`CONTRACT.md` 第 1、2 节仍写有 `data/mock/*`、`MockMedia.loadImage(...)` 等内容，
> 当前代码树里**没有 `data/mock/` 目录**，数据全部走 `data/fn/` + `data/FnOsMediaRepository.kt`。
> 读到契约里的 mock 描述时按历史文本理解，不要据此新建 mock 文件。

---

## 2. 架构说明

### 2.1 外壳与路由（`ui/NasPhotoTvApp.kt`）

`NasPhotoTvApp()` 是唯一外壳：包在 `NasPhotoTvTheme(appearance = SettingsStore.appearance)` 内，
用 `remember { mutableStateOf }` 持有三个**互相独立**的状态：

- `route: AppRoute` —— 页面路由（`ui/TvContract.kt` 中的 sealed interface：
  `Library / Setup / Settings / Accounts / PhonePhotos / ArtAlbum / ScreensaverPreview`）
- `viewer: ViewerRequest?` —— 看图请求（含 `items / initialIndex / contextTitle / slideshowProfile`）
- `player: PlayerRequest?` —— 播放请求

路由通过 `AnimatedContent(targetState = Triple(route, viewer, player))` 内的 `when { ... }` 手动分发，
**不是 navigation-compose**（依赖里也没有 navigation 库）。`viewer` / `player` 优先级高于 `route`，
二者为 null 时才落到 `LibraryScreen`；库页用 `rememberSaveableStateHolder()` 保住 Tab、
年/月层级与滚动位置。`ScreensaverScreen` 是覆盖层（不是替换），退出后焦点与滚动位置原样恢复。

该外壳还负责：真实数据加载与并发补齐（`coroutineScope { launch { ... } }`）、
账号身份切换时清 `MediaCache` / 图片缓存 / `FnOsTransport.token`（隐私要求）、
两步验证覆盖层、空闲超时自动进入屏保、任意键退出屏保（含 KeyUp 吞掉以防误触下层）。

### 2.2 设计 token 体系（`ui/theme/`）

| 文件 | 作用 |
|---|---|
| `ui/theme/Tokens.kt` | `TvColor`（语义色）、`TvDimen`（间距/圆角/焦点）、`TvType`（字号）、`TvImage`（缩略图像素） |
| `ui/theme/Appearance.kt` | `TvAppearance`、`TvPalette`、`DarkPalette`/`LightPalette`、`LocalTvPalette`、`ProvideTvPalette`、`ProvideTvStage`、`tvColorScheme` |
| `ui/theme/Theme.kt` | `NasPhotoTvTheme()`、`TvShapes`、M3 `Typography` |
| `ui/theme/TvFocusRing.kt` | `Modifier.tvFocusRing(radius)`，焦点描边绘制 |
| `ui/theme/TvDensity.kt` | 密度归一化（`ProvideTvDesignDensity`） |

关键值（以 `Tokens.kt` 为准）：`ScreenPadding=64.dp`、圆角 `10/16/24/32.dp`、
间距 `8/12/20/32/48/72.dp`、`Display=56sp / Section=30sp / CardTitle=22sp / Body=20sp / Caption=16sp / Tiny=13sp`、
`FocusScale=1.05f`、`FocusRingWidth=3.dp`。
`CONTRACT.md` 写的 `FocusScale=1.08f` 已过时，以代码 `1.05f` 为准。

### 2.3 飞牛接入层（`data/fn/`）

| 文件 | 职责 |
|---|---|
| `data/fn/LanNasDiscovery.kt` | 局域网自动发现（/24 网段 + TLS 证书识别飞牛设备） |
| `data/fn/FnOsApi.kt` | WSS 登录（RSA+AES）、签名请求、会话/令牌、上传、两步验证 |
| `data/fn/FnOsSigner.kt`、`FnOsReqId.kt` | `authx` 签名与递增 reqid |
| `data/fn/FnOsTofu.kt` | 首次证书指纹落盘 + 变更提示（TOFU） |
| `data/fn/FnOsTransport.kt` | OkHttp 拦截器统一注入 AccessToken；Coil 图片与 ExoPlayer 共用客户端 |
| `data/fn/NasAddress.kt`、`FnOsCrypto.kt`、`FnOsDeviceId.kt` | 协议/主机/端口/子路径解析、加解密、设备标识 |

### 2.4 数据层（`data/`）

`FnOsMediaRepository.kt` 是门面，组合 `FnOsGallery / FnOsSmartAlbums / FnOsFolders / FnOsMapPlaces /
FnOsPhotoLocation` 等，对外提供时间线、相册、人物、文件夹、地点、原片串流与收藏判定。
配套：`MediaCache.kt`（内存缓存，带账号归属）、`FavoritesStore.kt`（本机星标，按账号分区）、
`SettingsStore.kt`（SharedPreferences，含外观/屏保/放映/账号）、`SlideshowProfile.kt`（放映参数）、
`PhonePhotoUpload.kt` / `FnOsPhonePhotoUpload.kt` / `PhonePhotoNasBackup.kt` / `MusicUploadServer.kt`
（手机传图/备份/背景音乐）、`AppUpdate.kt`（在线更新）、`Paging.kt` / `MediaUrls.kt` / `MediaIdentity.kt`。

### 2.5 屏保子系统（`ui/screensaver/`）

`ScreensaverScreen.kt`（入口/任意键退出）、`ScreensaverClock.kt`（五种时钟）、
`ScreensaverBackground.kt`（照片轮播/极光/纯黑 + 防烧屏位移呼吸）、`ScreensaverPhotoWall.kt`、
`ScreensaverState.kt`。照片严格来自当前账号明确授权的相册，切换账号/失效不回退。

---

## 3. 关键约定（CONTRACT.md + 代码）

### 3.1 公共文件不要随意改

`CONTRACT.md` 第 3 节是文件所有权表；以下为 Lead 名下公共文件，改动需先找 Lead：
`ui/theme/*`、`data/model/*`、`data/MediaRepository.kt`、`ui/common/MediaImage.kt`、
`ui/common/FocusCard.kt`、`ui/TvContract.kt`、`ui/NasPhotoTvApp.kt`、`MainActivity.kt`。
其余按 `ui/library/*`、`ui/viewer/*`、`ui/video/*`、`ui/screensaver/*`、`ui/settings/*`
各自归属，**不要改别人名下的文件**；需要别人加方法时找 Lead 协调。

### 3.2 编码与交互规范

- 所有颜色/字号/间距用 `ui/theme/Tokens.kt` 的 token，禁止页面里写魔法值。
- 遥控器焦点：可聚焦元素必须肉眼可见——**放大 + 描边 + 光晕**，不能只靠颜色变化；
  焦点放大要预留空间避免被父容器 clip（`ui/common/FocusCard.kt` 与 `TvFocusRing.kt` 是范例）。
- 所有中文文案**直接写中文字符串**，不用 stringRes（原型优先速度）。
- 每个 Screen 至少一个 `@Preview(widthDp = 1920, heightDp = 1080)`。
- `minSdk 24`（Android 7.0），`java.time` 通过 core library desugaring 提供；
  `compileOptions` 与 `jvmTarget` 均为 17。
- 长按/双击/手势不支持；屏保首次按键只退出，不触发下层动作。

---

## 4. 外观模式特别注意（最容易踩坑）

项目提供「影院黑」(Dark，默认) 与「画廊白」(Light) 两套外观，另有「跟随系统」。

1. **`TvColor.*` 现在是 `@Composable @ReadOnlyComposable get()`**，从 `LocalTvPalette` 读取语义角色。
   只能在 Composable 上下文里读；不能像普通常量那样在任意地方引用。
2. **DrawScope 里必须先取色到局部值再捕获**：`Canvas` / `drawBehind` / `drawWithContent` 的
   DrawScope 不是 Composable 上下文。正确写法见 `ui/theme/TvFocusRing.kt:26`、
   `ui/settings/SettingsScreen.kt:664`、`ui/library/LibraryFocus.kt:290`、
   `ui/settings/SettingsScreensaverPanel.kt:549`（源码注释也写了这条规则）。
3. **顶层 `val` 的 initializer 不能读 `TvColor.*`**：会把启动时的配色固化下来；固定暗场请写死常量。
4. **看图 / 播放 / 屏保 / 艺术画册由 `ProvideTvStage` 强制固定暗场**：`ui/NasPhotoTvApp.kt` 中
   `VideoPlayerScreen`、`PhotoViewerScreen`、`ArtAlbumScreen`、`ScreensaverScreen` 外层都包了
   `ProvideTvStage`（内部 `ProvideTvPalette(DarkPalette)`），与用户选择的外观无关，照片才是主角。
5. **浅色焦点环是墨蓝 `#0B3A5C`**（`Appearance.kt` 的 `LightPalette.focusRing = 0xFF0B3A5C`，
   glow 为 `0x330B3A5C`）；深色为白描边 + 冰蓝辉光。
6. **设置页有外观组**：`ui/settings/SettingsAppearancePanel.kt` 用两块真实 token 渲染的迷你预览
   并排展示两套配色；`SettingsStore.appearance` 改动后整棵树自动重组，无需重启 Activity。

---

## 5. 构建与验证

```bash
# 唯一需要的编译验证命令（assemble 由最终构建方做）
./gradlew :app:compileDebugKotlin
```

| 项 | 值 |
|---|---|
| Gradle Wrapper | 仓库自带 `8.10.2`（`gradle/wrapper/gradle-wrapper.properties`） |
| JDK | 17（`sourceCompatibility` / `jvmTarget` = 17） |
| AGP / Kotlin | 8.7.3 / 2.0.21 |
| compileSdk / buildTools | 35 / 35.0.0；SDK 路径在 `local.properties` 的 `sdk.dir` |

机器资源有限时**串行跑 gradle，避免并行**：可用
`--no-daemon --no-parallel -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m"`，
同一时间只跑一个 Gradle；`CONTRACT.md` 还要求用 `.buildgate` 目录做协作锁，跑完立即删除。

### 关于手机传图两个符号

早期协作说明曾把 `FnOsPhotoImportUncertain`（`data/fn/FnOsApi.kt` 中抛出）和
`repository.uploadPhonePhoto(...)`（`ui/NasPhotoTvApp.kt` 中引用）列为"尚未定义、裸编译会失败"的
WIP 符号。**当前代码里这两个符号均已定义：**

- `data/fn/FnOsPhotoImportUncertain.kt` —— `internal class FnOsPhotoImportUncertain`
- `data/FnOsMediaRepository.kt` —— `internal suspend fun uploadPhonePhoto(file, onImportStarting)`

**不要把这两个符号当成外观模式的 bug 去改**——它们属于手机传图模块。

### 本机验证记录

当前环境 PATH 里没有 `java`、`JAVA_HOME` 也未设置，但仓库外的工具链位于
`/vol3/1000/project/.tools/jdk-17.0.20.1+1`（JDK 17）与
`/vol3/1000/project/.tools/gradle-8.10.2`。按以下方式执行过
`:app:compileDebugKotlin --rerun-tasks`，结果为 **BUILD SUCCESSFUL**：

```bash
export JAVA_HOME=/vol3/1000/project/.tools/jdk-17.0.20.1+1
export GRADLE_USER_HOME=/vol3/1000/project/.tools/gradle-home
/vol3/1000/project/.tools/gradle-8.10.2/bin/gradle --offline --no-daemon \
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" :app:compileDebugKotlin
```

换机器时以该机器上的 JDK 17 与 `./gradlew :app:compileDebugKotlin` 的真实输出为准。

---

## 6. 文档地图

| 文档 | 内容 |
|---|---|
| `README.md` | 功能总览、构建安装、遥控器操作、目录结构、NAS 接入、已知限制 |
| `CONTRACT.md` | 协作契约：公共文件、遥控器规范、编译约定（部分内容已过时，以代码为准） |
| `DESIGN.md` | 设计规范：token、布局、焦点、屏保、动效、可访问性 |
| `docs/acceptance.md` | 按页面列出的验收清单 |
| `docs/appearance/` | 外观设计与视觉稿：`README.md`、`tokens.json` / `tokens.css`、`preview.html`、`tvOS.md` |
| `docs/releases/` | 历史发布说明（`v1.2.0.md` … `v1.2.14.md`） |
| `apple/README.md` | tvOS 版本构建、签名与分发 |

> `README.md` 已移除版本更新日志，历史版本信息在 `docs/releases/`。

---

## 7. 给 agent 的操作建议

1. 动手前先读 `CONTRACT.md` 和 `ui/TvContract.kt`；但记住 CONTRACT 中 mock / FocusScale 等描述已过时，
   与代码冲突时以代码为准。
2. 改 UI 必须跑 `./gradlew :app:compileDebugKotlin`；外观相关改动尤其要验证 DrawScope 取色与暗场包裹。
3. **不要新增依赖**（默认禁止，确需加必须找 Lead 协调）。
4. 不要改别人名下的文件；公共文件改动先找 Lead。
5. 涉及 `apple/` 目录时，注意 `apple/NasPhotoTV/UI/Theme.swift` 的 `Tv` token 要与 Android
   `ui/theme/Tokens.kt` 对齐（详见 `docs/appearance/tvOS.md`）。
