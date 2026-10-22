# NAS Photo TV

> **在电视上看 NAS 里的照片与视频，并把电视变成一台会呼吸的电子相框。**

Android TV 原型 App（Kotlin + Jetpack Compose）。包名 `com.nasphoto.tv`，单 Activity、纯 Compose、无 TV 专用库；
屏保待机显示对标 iPhone 的 StandBy —— 大号时钟 + 照片氛围 + 天气 + 防烧屏。

- 版本：`1.0.0-prototype`（versionCode 1）
- 技术栈：Kotlin 2.0.21 / AGP 8.7.3 / Compose BOM 2024.12.01（Compose 1.7.6、material3 1.3.1）/ minSdk 26、compileSdk 35、targetSdk 34
- 数据来源：**全部本地 Mock**（`data/mock/MockData.kt` + `res/raw/*.jpg`），不联网

---

## 0. 快速开始（TL;DR）

```powershell
# 1. 构建
cd E:\NasPhotoTV
.\gradlew.bat :app:assembleDebug

# 2. 安装到已连接的电视 / 模拟器
E:\Android\Sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk

# 3. 启动（Activity 名与包名一致：com.nasphoto.tv/.MainActivity）
E:\Android\Sdk\platform-tools\adb.exe shell am start -n com.nasphoto.tv/.MainActivity
```

APK 产物：`app/build/outputs/apk/debug/app-debug.apk`（约 16 MB，含 9 张占位图）。

---

## 1. 功能清单

| 模块 | 代码位置 | 已实现 |
|---|---|---|
| **媒体库 / 相册浏览** | `ui/library/` | 顶栏（Logo + NAS 状态点 + 5 个 Tab + 搜索/屏保/设置按钮）；「全部照片」Tab 有 Hero 大图 + 最近添加横排 + 相册横排；「年 · 月 · 日」Tab 四级下钻（年 → 月 → 日 → 当天媒体网格）；「相册」Tab 4 列网格；「最近添加」Tab 6 列网格；「收藏 · 人物 · 地点」Tab（收藏网格 + 人物/地点/回忆横排）；相册详情页（返回按钮 + 6 列媒体网格）；各层级空状态 |
| **全屏看图** | `ui/viewer/` | **图片优先铺满整屏**（无常驻侧栏，信息都是瞬态浮层）；左上 LIVE 徽标 + 控制栏可见时的状态句/来源/拍摄/标题/序号；`UP` 展开尺寸/文件/ID；左右切换（循环）+ 方向滑动动画；7 个工具按钮（上一张/下一张/信息/收藏/幻灯片/放大查看/关闭）；缩略图胶片条；幻灯片自动播放（5s/张，方向键暂停）；2× 放大 + 方向键平移；视频项显示封面 + ▶ |
| **视频播放（模拟）** | `ui/video/` | 16:9 画面窗 + 控制层 3 秒自动隐藏；播放/暂停；进度条 + 伪缓冲；快进快退 ±10s + OSD；上一个/下一个（跳过图片项）；字幕开关（样片字幕）；循环；右侧信息面板（MENU）；播完 5 秒倒计时自动连播 / 结束面板；媒体键完整映射；**无真实解码**（详见 §8） |
| **屏保待机显示** | `ui/screensaver/` | 5 种时钟（CINEMA 巨幕/FLIP/THIN/MINIMAL/DATE_FOCUS）× 3 种背景（照片轮播/极光动态/纯黑极简）；农历干支小字；**前景只有时钟与退出提示，不显示任何图片信息**（标题、拍摄日期、分辨率、地点都不上屏）；Ken Burns 推拉；像素位移 + 夜间呼吸防烧屏；任意键退出 |
| **设置** | `ui/settings/` | 两栏（左列表 420dp + 右面板）：NAS 连接（协议/地址/端口/账号/共享目录/测试连接/重扫）、屏保与待机（10 项 + 预览）、视频播放（4 项）、关于（版本/引擎/设备/设计说明/恢复默认）；右栏随聚焦项切换为 连接拓扑 / 屏保实时小预览 / 播放摘要 / 关于 |

---

## 2. 截图

| 媒体库首页（Hero + 横排） | 相册网格 |
|---|---|
| ![媒体库首页](docs/screenshots/01-library.png) | ![相册网格](docs/screenshots/02-album-grid.png) |

| 全屏看图 | 视频播放 |
|---|---|
| ![全屏看图](docs/screenshots/03-photo-viewer.png) | ![视频播放](docs/screenshots/04-video-player.png) |

| 屏保 · 翻页数字 | 屏保 · 日期日历 |
|---|---|
| ![屏保翻页数字](docs/screenshots/05-screensaver-flip.png) | ![屏保日期日历](docs/screenshots/06-screensaver-date.png) |

| 设置页 | |
|---|---|
| ![设置页](docs/screenshots/07-settings.png) | |

> 截图位于 `docs/screenshots/`（1920×1080，10-foot UI 画布），命名即上表的 `01-library.png` … `07-settings.png`。
> 尚未放入的文件会显示为破图，不影响文档阅读。

---

## 3. 运行环境与构建

### 3.1 环境要求

| 项 | 要求 | 本机实测 |
|---|---|---|
| JDK | **17+**（工程 `sourceCompatibility/targetCompatibility = 17`、`jvmTarget = 17`） | JDK 24.0.1 可用（Gradle 8.10.2） |
| Gradle | 用仓库自带 Wrapper（**8.10.2**），不要用全局 gradle | 已含 `gradle/wrapper/gradle-wrapper.jar` |
| Android SDK | **compileSdk 35**（platforms;android-35 + build-tools），路径写在 `local.properties` 的 `sdk.dir` | `E:\Android\Sdk` |
| 运行设备 | Android **8.0（API 26）及以上**；Android TV / Google TV / 电视盒子；`android.software.leanback` 为 **required**，touchscreen 非必需 | 当前连接的 `emulator-5554` 是手机镜像（API 35），也能跑，见 §3.4 |

Manifest 已声明：`leanback` required + `touchscreen` not required + `LEANBACK_LAUNCHER` 与 `LAUNCHER` 双入口、`android:banner`、
`INTERNET` / `ACCESS_NETWORK_STATE` / `WAKE_LOCK` 权限、`usesCleartextTraffic="true"`、`screenOrientation="landscape"`、
`launchMode="singleTask"`、`configChanges` 含 `uiMode|screenSize|screenLayout`。

### 3.2 构建

```powershell
# Debug（推荐）
.\gradlew.bat :app:assembleDebug
# 只验证编译（最快，团队协作时用这个）
.\gradlew.bat :app:compileDebugKotlin
# Release（未签名、未混淆，仅供体积评估）
.\gradlew.bat :app:assembleRelease
```

产物路径：
- Debug：`app/build/outputs/apk/debug/app-debug.apk`
- Release：`app/build/outputs/apk/release/app-release-unsigned.apk`

> ⚠ 团队协作注意：**同一时刻只跑一个 gradle**。契约约定用目录锁 `E:\NasPhotoTV\.buildgate` 互斥，
> 跑完务必删除该目录，否则其他成员的编译会一直等待。

### 3.3 安装到电视 / 盒子

```powershell
# 第一次连接（电视需在 设置 → 系统 → 开发者选项 中开启 ADB 调试）
adb connect 192.168.1.50:5555
adb devices                      # 确认设备状态为 device

# 安装 / 覆盖安装
adb install -r app\build\outputs\apk\debug\app-debug.apk

# 启动
adb shell am start -n com.nasphoto.tv/.MainActivity

# 看崩溃日志
adb logcat -s AndroidRuntime:E
```

也可以用 U 盘 + 电视自带「文件管理」安装 APK（需允许安装未知来源应用）。

### 3.4 模拟器（AVD）

工程自己包含 `@Preview(widthDp = 1920, heightDp = 1080)`（媒体库首页/相册/智能/空状态、看图页 ×2、屏保 ×4 等），
**最省事的验证方式是在 Android Studio 里直接看 Preview**，不依赖任何设备。

> ⚠ 关于 `android.software.leanback`：Manifest 里它是 `required="true"`，而**普通手机 AVD 镜像不声明 leanback**，
> 直接 `adb install` 可能报 `INSTALL_FAILED_MISSING_SHARED_LIBRARY` / 不满足特性。当前机器上的两个 AVD
> （`Pixel_8_API_35`、`Finexy_API_35_Clean`，均为 `google_apis/x86_64` 手机镜像）就是这种情况。
> 原型的处理办法与正式构建一致，见下表。

| 场景 | 做法 |
|---|---|
| **推荐：Android TV 模拟器** | SDK Manager 安装 `system-images;android-35;android-tv;x86`（TV 镜像自带 leanback），用它新建 AVD；或装 Android Studio 的 **TV 模板**（分辨率 1920×1080，density 320 → 1dp ≈ 1px，与设计稿 1:1） |
| **只有手机 AVD** | 把 `app/src/main/AndroidManifest.xml` 里 `android.software.leanback` 的 `android:required` 改成 `false`（一行改动，仅本地验证用，**不要提交**），然后 `adb install -r` |
| **真机电视** | 无需任何改动，直接安装 |

手机镜像下 App 仍是横屏铺满的电视版布局，只是**按键要映射**：

```powershell
# 方向键 = 键盘方向键；确认 = Enter；返回 = Esc / 遥控器 Back
# 物理键盘映射（AVD 默认就支持）：方向键、Enter、Esc、Tab(=菜单键)
```

仿真页的"方向键"在 AVD 上可以只用键盘，也可以用 `adb shell input keyevent`：

```
19/20/21/22 = 上/下/左/右     23 = DPAD_CENTER     66 = ENTER     4 = BACK     127 = MENU
```

---

## 4. 遥控器操作总表

通用规则（所有页面）：

| 键 | 行为 |
|---|---|
| 方向键 `DPAD_UP/DOWN/LEFT/RIGHT` | 移动焦点（行内左右、跨行上下由页面显式接管，不依赖 Compose 默认顺序） |
| `DPAD_CENTER` / `ENTER` | 确认（等价，代码里同时处理 `Key.DirectionCenter / Key.Enter / Key.NumPadEnter`） |
| `BACK` | 返回上一级；在 看图页 / 播放页 / 设置页 = 关闭该页回到媒体库 |
| 长按 / 双击 | **不支持**（一次性操作直接 ENTER） |

### 4.1 媒体库（首页 / 相册 / 详情）— `ui/library`

| 区域 | 方向键 | ENTER | BACK |
|---|---|---|---|
| 顶栏 Tab（全部照片 / 年 · 月 · 日 / 相册 / 最近添加 / 收藏 · 人物 · 地点） | 左右在 Tab 间移动 | 切换 Tab（并退出相册详情） | 相册详情内 = 回到列表 |
| 顶栏按钮（搜索 / 屏保 / 设置） | 左右移动 | 搜索 → 切到「全部照片」；屏保 → `AppRoute.ScreensaverPreview`（立即进入屏保）；设置 → `AppRoute.Settings` | — |
| Tab 按 `DOWN` | 交出焦点给主内容（Hero 优先） | — | — |
| Hero 大图 | 左右无；`DOWN` → 最近添加行 | 打开对应媒体（视频进播放器 / 图片进看图页），上下文标题「全部照片」 | — |
| 「最近添加」横排（卡片 300×180） | `LEFT/RIGHT` 行内移动；`UP` → Hero；`DOWN` → 相册行**同列**卡片 | 打开（上下文标题「最近添加」） | — |
| 「相册」横排（卡片 380×220 + 末尾「查看全部相册」） | `LEFT/RIGHT`；`UP` → 最近添加行同列 | 进入相册详情 / 切到「相册」Tab | — |
| 相册网格（4 列） | 左右 + 上下（Compose 默认同列） | 进入相册详情 | 回列表 |
| 时间线分桶网格（5 列，年 / 月 / 日 三级） | 左右 + 上下 | 下钻一级：年 → 月 → 日 → 当天媒体网格 | 逐级上返（日 → 月 → 年 → 交回外层） |
| 媒体网格（6 列，最近添加 / 收藏 / 相册详情 / 某一天） | 左右 + 上下；相册详情第一行 `UP` → 返回按钮 | 图片 → 看图页；视频 → 播放器（上下文标题 = 相册名 / 当天日期） | 相册详情内 = 回列表 |
| 空状态「去设置」按钮 | — | 跳设置页 | — |

### 4.2 全屏看图 — `ui/viewer/PhotoViewerScreen`

布局是**图片优先**：舞台 `fillMaxSize()` + `ContentScale.Fit`，整屏就是画框，竖构图也不会被副框缩小。
左上角只有 LIVE 徽标常驻；标题、来源、拍摄日期、序号都在 `chromeVisible` 的浮层里，3 秒无操作（或 ENTER 收起）后整屏只剩图片。

| 键 | 焦点在图片区 | 焦点在工具按钮行 / 胶片条 |
|---|---|---|
| `LEFT` / `RIGHT` | 上一张 / 下一张（首尾循环，带方向滑动）；放大模式下 = 平移 0.5 倍 | 在按钮/缩略图之间移动 |
| `UP` | 放大模式下上移；否则展开 / 收起详细信息（尺寸 / 文件 / ID） | 回到图片区（按钮行） |
| `DOWN` | 进入工具按钮行（并弹出控制栏）；放大模式下下移 | 交给焦点系统 |
| `ENTER` | 图片：切换控制栏显隐；**视频项**：`onOpenVideo` 进播放器 | 触发该按钮 |
| `TAB` / `MENU` | 跳到工具按钮行 | — |
| `PREV/NEXT`（媒体键） | 同 LEFT/RIGHT（Compose 映射为方向键时生效） | — |
| `PLAY/PAUSE`（媒体键） | 幻灯片播放 / 暂停 | — |
| 任意方向键 | 立刻暂停幻灯片（同一事件继续执行切换动作） | — |
| 任意按键（控制栏已隐藏时） | 先显示控制栏，**并继续执行原动作**（不吃键） | — |
| `BACK` / `ESC` | 关闭看图页回到媒体库 | 同左 |

工具按钮（7 个）：`◀ 上一张` `▶ 下一张` `ⓘ 信息` `★ 收藏` `⏵ 幻灯片` `⛶ 放大查看/适应屏幕` `✕ 关闭`。
幻灯片每 5 秒自动下一张，右下角显示进度条；控制栏 3 秒无操作自动隐藏（焦点在按钮行时不隐藏）。

### 4.3 视频播放 — `ui/video/VideoPlayerScreen`

形状：**16:9 画面窗**（`aspectRatio(16/9)` + `fillMaxHeight(0.78f)`，1920×1080 上约 1478×831，上下留黑边），
所有控制元素都在画面窗内。焦点由 `VideoPlayerState.focusSlot` 驱动（不参与 Compose 焦点链，避免双触发），
分区顺序：**顶部返回 → 进度条 → 中央播放键 → 底部按钮行**，上下穿梭时记住每个分区上次的位置。

| 键 | 控制层显示中 | 控制层隐藏时 |
|---|---|---|
| `UP` / `DOWN` | 在 4 个分区之间移动焦点 | 只唤出控制层 |
| `LEFT` / `RIGHT` | 底栏按钮行内移动：`快退10s → 播放/暂停 → 快进10s → 上一个 → 下一个 → 字幕 → 循环 → 信息`；**焦点在进度条上时 = 快退/快进 10 秒** | 快退 / 快进 10 秒（并弹出 OSD） |
| `ENTER` / `DPAD_CENTER` | 执行当前焦点控件（返回 / 进度条与播放键 = 播放暂停 / 各按钮） | 播放 / 暂停 |
| `MENU` | 打开 / 关闭右侧信息面板 | 同左 |
| `BACK` / `ESC` | 信息面板开着 → 先关面板；否则关闭播放器 | 关闭播放器 |
| 媒体键 | `PLAY/PAUSE/PLAY_PAUSE` 播放暂停；`FAST_FORWARD/REWIND` ±10 秒；`NEXT/PREVIOUS` 切换视频 | 同左 |
| 任意按键 | 每次操作都会重启控制层 3 秒倒计时 | 同上 |

其他行为：
- 控制层在 **3 秒** 无操作后自动隐藏（信息面板 / 结束面板打开时不隐藏）；
- 画面窗顶部条：`←` 返回按钮（52dp 圆形，聚焦放大 1.12）+ 标题 + `来自 <contextTitle> · 第 n / m 集` + 右上角 `NAS 直连 · 原片` / `NAS 转码 · 1080p`（随 `PlaybackSettings.transcode`）；
- 底栏按钮：`快退 10s` `播放/暂停` `快进 10s` ｜ `上一个` `下一个`（无上/下一个时置灰不可用）｜ `字幕 开/关` `循环 开/关` ｜ 右侧 `信息`；
- 进度条：已播放青色渐变 + 已缓冲白 24%（伪值，取 60% 与当前进度的较大者）；聚焦时轨道加高 12→20dp 并加白描边；
- 播完分支：`autoPlayNext && 有下一个` → 右下角倒计时角标「即将播放：xxx · N 秒后自动连播 · 回车取消」（`ENTER` 或 `信息` 键取消）；`loopMode` → 从头播；否则 → **结束面板**「重新播放 / 下一个 / 返回媒体库」（`◀ ▶` 选择、`ENTER` 确认、`BACK` 关闭）；
- 列表里混排图片：进入播放器时若初始项是图片，直接把列表交给图片浏览器（`onOpenPhoto`）；
- `items` 为空 → 直接 `onClose()`。

### 4.4 屏保待机 — `ui/screensaver/ScreensaverScreen`

| 键 | 行为 |
|---|---|
| **任意键**（方向键 / 确认 / BACK / 音量 / 数字键…） | 退出屏保回到媒体库。**第一次按键只退出，绝不触发别的页面动作**（事件被 `onKeyEvent` 返回 `true` 吞掉） |
| BACK（Activity 级兜底） | 通过 `OnBackPressedDispatcher` 也走 `onExit()`，防止 BACK 漏到外壳退两层 |

进入屏保的方式：① 设置页「预览屏保」；② 顶栏「屏保」按钮；③ **空闲自动进入** —— 外壳每秒检测，
`SettingsStore.screensaver.enabled == true` 且静止时间 ≥ `idleTimeoutSec`（默认 60 秒）时进入；
在看图页、播放器、屏保预览路由下不自动进入。

### 4.5 设置 — `ui/settings/SettingsScreen`

布局：顶栏（青点 + `设置` + NAS 胶囊 + 右上角键位提示）→ 左列表（420dp）/ 右面板 → 底部状态条（聚焦行提示 + `分组 · 第 n / 30 项`）。
**初始焦点落在「屏保」开关**（最常改的一项）。左栏共 4 组 24 行。

| 键 | 行为 |
|---|---|
| `UP` / `DOWN` | 在设置行之间移动（自动滚动保证聚焦行可见）；在"可微调"的行上（端口 / 账号）为 ±1 精调 |
| `LEFT` / `RIGHT` | 行内调值：开关 / 协议 / 端口（±10）/ 账号 / 共享目录 / 屏保各项循环 / 播放各项。开关行与值行会**吃掉**左右键，不会跑到右面板 |
| `ENTER` | 执行：动作行立即执行（测试连接 / 重扫 / 预览屏保 / 设计说明 / 恢复默认）；其余行等同于"往后调一格"；端口行 = 恢复协议默认端口；设备名/地址行 = 切换候选值 |
| `BACK` | 「设计说明」展开时先收回；否则关闭设置页回媒体库 |

四组内容（`SettingsKey`）：

| 组 | 行 |
|---|---|
| `NAS 连接` | 设备名称、协议、地址、端口、账号、共享目录、测试连接、重新扫描媒体库 |
| `屏保与待机` | 屏保开关、静止后进入、照片停留、时钟样式、背景、显示日期、夜间自动调暗、Ken Burns 动效、预览屏保 |
| `视频播放` | 自动连播下一集、循环播放、默认显示字幕、NAS 端转码 |
| `关于` | 版本（1.0.0-prototype）、引擎（Compose）、设备（Android TV）、设计说明（ENTER 展开 5 条设计原则）、恢复默认设置（ENTER 两次确认，3 秒超时） |

右面板随聚焦分组切换：`NAS` → 连接拓扑（电视 ↔ 路由器 ↔ NAS 三个胶囊 + 流动光点连线 + 延迟/抖动/带宽/路径/账号 + 连接串）；`屏保` → 16:9 实时小预览（背景 + 五种时钟小样 + 日期叠加 + 夜间调暗暖色 + Ken Burns 慢缩放；屏保不显示任何图片信息），聚焦「时钟样式 / 背景」行时下方出现可选胶囊条；`播放` → 封面卡 + 四条参数摘要 + 行为说明；`关于` → Logo + 版本 + 统计 + 设计原则。

> 「测试连接」是**演示友好**的实现：本次会话第一次探测会失败（让"连接失败"状态也能被看到），之后都成功；「重新扫描」是 1.5 秒假进度。两者都是纯 UI 状态，不写回 `SettingsStore` 的数据（`nasTesting/nasTestOk/nasTestLatency` 在 `SettingsFeedback` 里）。

---

## 5. 目录结构

```
E:\NasPhotoTV
├─ CONTRACT.md                     团队协作契约（只读参考）
├─ README.md                       本文
├─ DESIGN.md                       设计规范（token / 布局 / 焦点 / 屏保 / 动效 / 可访问性）
├─ docs/
│  ├─ acceptance.md                验收清单（按页面勾选）
│  └─ screenshots/                 截图（01-library.png … 07-settings.png）
├─ media_samples/                  素材源文件（1920×1080 jpg，由 tools/gen_assets.py 生成）
├─ tools/gen_assets.py             占位素材生成脚本（Pillow）：media_samples/*.jpg + res/raw/* + 启动图/图标
├─ build.gradle.kts                插件版本（AGP 8.7.3 / Kotlin 2.0.21 / compose plugin）
├─ settings.gradle.kts             仓库配置（google + mavenCentral，FAIL_ON_PROJECT_REPOS）
├─ gradle.properties               JVM 3G / 开启 parallel + caching
├─ local.properties                sdk.dir（本机）
└─ app/
   ├─ build.gradle.kts             minSdk 26 / target 34 / compile 35 / Compose BOM 2024.12.01
   └─ src/main/
      ├─ AndroidManifest.xml       leanback + 双入口 + banner
      ├─ res/raw/                  9 张占位图：wall_aurora / wall_sunset / wall_ocean / wall_forest / wall_mono / wall_candy / video_mountain / video_city / video_family
      ├─ res/drawable/             app_banner.png(320×180)、app_icon.png(288×288)
      ├─ res/values/               strings.xml（app_name = "NAS 相册 TV"）、themes.xml（NoActionBar + 黑底 + 边到边）
      └─ java/com/nasphoto/tv/
         ├─ MainActivity.kt                      单 Activity：沉浸式全屏 + FLAG_KEEP_SCREEN_ON + 按键时间戳
         │
         ├─ ui/                                  ── UI 层 ──
         │  ├─ NasPhotoTvApp.kt                  外壳：路由 / 屏保空闲检测 / 页面切换（交叉淡入）
         │  ├─ TvContract.kt                     AppRoute、LibraryState、ViewerRequest、PlayerRequest
         │  ├─ theme/Tokens.kt                   ★ 设计 token：TvColor / TvDimen / TvType
         │  ├─ theme/Theme.kt                    NasPhotoTvTheme、TvShapes、M3 ColorScheme/Typography 映射
         │  ├─ common/MediaImage.kt              MediaImage(url, …)：统一媒体加载 + 渐变占位
         │  ├─ common/FocusCard.kt               FocusCard：放大 + 白描边 + 青光晕（看图/播放器在用）
         │  ├─ library/LibraryScreen.kt          媒体库五个 Tab（含年·月·日三级下钻）+ 相册详情 + 页面级空状态
         │  ├─ library/LibraryComponents.kt      顶栏 / 区块标题 / Hero / 媒体卡片 / 相册卡片 / 时间线分桶卡片 / 空状态 / 返回头
         │  ├─ library/LibraryFocus.kt           TvFocusBox、PendingFocus、scrollRowTo/scrollGridTo、GridContentPadding
         │  ├─ library/LibraryData.kt            LibraryTab 枚举、HeroInfo、ChronoBucket（年/月/日分桶）、空安全派生与格式化
         │  ├─ viewer/PhotoViewerScreen.kt       全屏看图（状态机 + 按键 + 全屏舞台 + 幻灯片）
         │  ├─ viewer/ViewerState.kt             PhotoViewerState：切换/控制栏/收藏/缩放平移/幻灯片
         │  ├─ viewer/ViewerToolBar.kt           7 个工具按钮 + KeyHint 键位胶囊
         │  ├─ viewer/ViewerInfoBar.kt           左上瞬态浮层 HUD（LIVE 徽标 + 来源/拍摄/标题/序号）+ 缩略图胶片条
         │  ├─ video/VideoPlayerScreen.kt        播放器页：16:9 画面窗 + 控制层 + 面板装配
         │  ├─ video/VideoKeyHandler.kt          按键映射（分区焦点 moveVertical/moveHorizontal、结束面板、媒体键）
         │  ├─ video/VideoPlayerState.kt         播放状态机（100ms 步进模拟）+ 列表查找工具 + 时长格式化
         │  ├─ video/VideoControls.kt            顶部条 / 中央播放键 / 进度条 / 底栏按钮
         │  ├─ video/VideoStage.kt               画面层：封面 + 暗角 + 压暗 + 字幕条 + 快进 OSD + 正在播放角标
         │  ├─ video/VideoOverlays.kt            右侧信息面板 / 自动连播倒计时角标 / 结束面板
         │  ├─ video/TvFocusSurface.kt           播放器专用焦点外框（页面状态驱动，1.08/1.12 + 白描边 + 青光晕）
         │  ├─ screensaver/ScreensaverScreen.kt  屏保入口：背景/遮罩/防烧屏容器/时钟前景/按键退出（不显示图片信息）
         │  ├─ screensaver/ScreensaverClock.kt   5 种时钟 + GlassCard + EntranceFade
         │  ├─ screensaver/ScreensaverBackground.kt 照片轮播 + Ken Burns、极光、纯黑、可读性遮罩
         │  ├─ screensaver/ScreensaverState.kt   每秒时钟、中文日期/星期/农历干支、tabular 数字样式
         │  └─ settings/
         │     ├─ SettingsScreen.kt              页面骨架：顶栏 / 左列表 / 右面板 / 底部状态条 / 按键分发
         │     ├─ SettingsModel.kt               SettingsGroup / SettingsKey（24 行）/ SettingsActions / SettingsFeedback
         │     ├─ SettingsRow.kt                 一行设置项 + 组标题 + 值文本/开关态判定
         │     ├─ SettingsFocusRow.kt            可聚焦行（高 62dp、放大 1.02、白 3dp 描边、左右调值）
         │     ├─ SettingsChips.kt               值胶囊 / 静态胶囊 / TvSwitch / 进度条 / ‹ › 字形
         │     └─ SettingsNasPanel / SettingsScreensaverPanel / SettingsPlaybackPanel / SettingsAboutPanel.kt  右栏四个面板
         │
         └─ data/                                ── 数据层 ──
            ├─ model/Models.kt                   ★ MediaItem / Album / AlbumType / MediaType / NasConnection /
            │                                       ScreensaverSettings / PlaybackSettings / WeatherInfo
            ├─ MediaRepository.kt                ★ 仓库接口（唯一需要替换的抽象边界）
            ├─ RepositoryProvider.kt             RepositoryProvider.current + MockMediaRepository 实现
            ├─ SettingsStore.kt                  ★ 全局设置（Compose State）+ cycle* 便捷方法 + SharedPreferences persist/restore
            └─ mock/
               ├─ MockData.kt                    20 项 recent / 11 个相册 / 每个相册 9~24 项；screensaverPool()
               ├─ MockWeatherProvider.kt         按小时稳定的 3 组天气
               └─ MockMedia.kt                    res/raw 解码 + LruCache(24) + 降采样；AppContextHolder
```

★ = 公共基础文件（契约规定不要改），也是接入真实 NAS 时真正要动的边界。

### 5.1 页面之间的跳转关系

```
                 ┌──────────────── NasPhotoTvApp (外壳: route + viewer + player) ────────────────┐
                 │                                                                            │
  空闲 ≥ idleTimeoutSec │ 顶栏「屏保」按钮                                              │
                 ▼                           ▼                                                │
          ScreensaverScreen ◄── 预览屏保 ── LibraryScreen ──ENTER 图片──► PhotoViewerScreen      │
                 │                           ▲   │                          │  │               │
                 │ 任意键                     │   └──ENTER 视频──► VideoPlayerScreen ◄──ENTER 视频项
                 └──────────────► 回到媒体库    │            ◄─ onOpenPhoto ─┘  (= onOpenVideo)  │
                                             └──────────── SettingsScreen ───────────────────────┘
                                                            BACK / onOpenLibrary
```

- 路由是朴素的 `when(route)` + 三个状态（`route` / `ViewerRequest?` / `PlayerRequest?`），没有 navigation-compose；
- 页面切换用 `AnimatedContent` + `fadeIn(220) / fadeOut(160)`；
- 进入看图页 / 播放器 / 屏保后，外壳的空闲计时**不触发**屏保（`player == null && viewer == null`）。

---

## 6. 接入真实 NAS 的路线图

原型的设计前提：**所有"可能变成网络/解码"的地方都被收在 3 个边界后面**，所以接通真机不需要重写 UI。

```
        UI 层（一字不改）
  LibraryScreen / PhotoViewerScreen / VideoPlayerScreen / ScreensaverScreen / SettingsScreen
        │                     │                          │
        │ 数据              │ 图片解码               │ 视频解码
        ▼                     ▼                          ▼
  ① MediaRepository      ② MediaImage             ③ VideoStage
   RepositoryProvider     (换 Coil)               (换 Media3 ExoPlayer)
```

### 步骤 0：为什么原型这样设计（设计取舍）

| 决策 | 原型做法 | 原因 | 接真机时的收益 |
|---|---|---|---|
| 数据抽象 | `MediaRepository` 接口 + `RepositoryProvider.current` | UI 只依赖接口；Mock 与真实实现可在一行内切换 | 只要实现接口，5 个页面零改动 |
| `MediaItem.url` 是"可加载地址" | Mock 用 `"raw/wall_aurora"`，注释已约定真实环境为 `http(s)://` 或 `file://` | 加载层只认字符串，协议细节被挡在数据层 | URL 只换值，不改类型 |
| 图片加载 | 自己写的 `MediaImage` + `MockMedia`（`BitmapFactory` + `LruCache`） | 原型只依赖本地资源，不加 Coil 依赖、构建更快、离线可跑 | 只替换 `MediaImage` 内部实现，调用点全不动 |
| 播放 | 100ms 时间步进模拟，`VideoPlayerState` 里只有 `positionMs/playing` 两个"播放器真相" | 没有 media3 依赖；先把手感与按键验证完 | 把这两个字段换成 `Player` 的 `stateFlow` 收集即可 |
| 设置 | `SettingsStore` 用 `mutableStateOf`，可选 `persist()/restore()` | 原型不强制落盘，重启回默认值 | 换成 DataStore/加密存储时只改这一个文件 |
| 网络 | 全 App 零网络请求 | 演示不会被网络状态干扰 | 新增依赖集中在数据层三个文件 + Manifest |

### 步骤 1：实现 `MediaRepository`（本项目最关键的一步）

接口（`data/MediaRepository.kt`，全部 `suspend`，注释要求真实实现放在 `Dispatchers.IO`）：

```kotlin
interface MediaRepository {
    val connection: NasConnection
    suspend fun ping(): Boolean
    suspend fun getRecent(limit: Int = 20): List<MediaItem>
    suspend fun getAlbums(): List<Album>
    suspend fun getMedia(albumId: String): List<MediaItem>
    suspend fun search(query: String): List<MediaItem>
    suspend fun getScreensaverPhotos(): List<MediaItem>   // 默认实现：getRecent(50) 里挑 PHOTO
    suspend fun getMediaItem(id: String): MediaItem?      // 默认返回 null
}
```

三种实现建议 **按优先级**（家庭 NAS 场景 WebDAV 命中率最高、依赖最轻）：

| 实现 | 协议 / 依赖 | 映射方式 | 适用 / 注意 |
|---|---|---|---|
| `WebDavMediaRepository` | OkHttp + `PROPFIND`（可自写 XML 解析，或引入 sardine/jackrabbit-webdav） | 目录 → `Album`（`AlbumType.FOLDER`，`subtitle` 放路径）；目录下文件 → `MediaItem`；`getcontentlength`→`sizeBytes`、`getlastmodified`→`dateTaken`、扩展名→`extension`/`type` | 群晖/威联通/极空间/Nextcloud 都开 WebDAV；HTTP 走 80/443，穿 NAT 简单；`Range` 请求支持流式播放 |
| `SmbMediaRepository` | jcifs-ng（`smb://user@host:445/photo`） | 同上，`SmbFile.listFiles()` | NAS 原生协议、速度最好；Android 上要自己管 NetBIOS/签名/guest，库维护活跃度需评估；**建议与步骤 3 的 SMB 流式播放一起做** |
| `JellyfinMediaRepository` | Jellyfin REST API（`/Users/{u}/Items`、`/Items/{id}/Images/Primary`） | `Movie/Video/Photo` → `MediaItem`；`getAlbums` 用媒体库/Library | 已有 Jellyfin 服务端的家庭最省事：缩略图、转码、封面、进度全都有；**但要接受"再部署一个服务端"** |
| `LocalFolderMediaRepository`（补充） | `MediaStore` / SAF / 已挂载 SMB 的 `file://` | — | 盒子/部分电视插 U 盘或系统级挂载的情况，零网络依赖，适合当兜底 |

实现要点：
- `ping()`：一次轻量调用（PROPFIND 目录深度 0 / SMB 列目录）计时，写回 `SettingsStore.reportNasResult(latencyMs)`（该方法已存在但**目前无人调用**，是给真实实现准备的钩子）；
- `search(query)`：拿不到服务端搜索就用本地索引（见步骤 6），日期/文件名/类型匹配；
- 错误处理：抛出可区分的 `NasException`（网络不可达 / 认证失败 / 路径不存在），UI 用空状态 + 提示承接（现有空状态文案已经写了「检查 NAS 连接」）；
- **切换方式**：

```kotlin
// data/RepositoryProvider.kt
val current: MediaRepository by lazy { MockMediaRepository() }   // 改成 ↓
// val current: MediaRepository by lazy { WebDavMediaRepository(SettingsStore.nas) }
```
或者按设置页开关/构建变体（`BuildConfig.DEBUG`）二选一。

> ⚠️ 当前外壳 `NasPhotoTvApp` 直接把 `MockData` 同步填进 `LibraryState`，并把 `MockData.screensaverPool()` / `MockWeatherProvider.current()` 传屏保。
> 接真机的第一步其实是：外壳改成 `LaunchedEffect { state = repo.getRecent() ... }` 的异步装配（页面签名不用变，`LibraryState` 字段也不用变）。
> 这一步是**唯一**需要 Lead 动的"接线"改动。

### 步骤 2：图片加载换成 Coil

- 替换文件：**只有 `ui/common/MediaImage.kt`**（把 `MockMedia.loadImage` 换成 `AsyncImage(model = url, contentScale, placeholder/failure = Placeholder(colorForUrl(url)))`）。
  所有调用方（库卡片 / Hero / 看图 / 屏保 / 胶片条）签名不变。
- 已为 Coil 预留的参数：`targetWidth`（缩略图/大图分级）、`cornerRadiusDp`、`ContentScale`、`contentDescription`。
- 建议配置：`crossfade(180)`、`diskCache` 独立文件缓存（限制 512MB~1GB）、`memoryCache` 按 1/8 堆、
  `ImageRequest.size` 用 `targetWidth` 限定（电视 1080p 屏不需要 6000px 原图）；
- 屏保请用**中尺寸**（现在传 `targetWidth = 1280`），不要原图直读（见步骤 7 性能）。
- HEIC：Coil 在 API 28+ 系统解码器支持 HEIC/HEIF，API 26/27 需要额外解码器或服务端转 JPG（见 §7 量产清单与 §8 已知限制）。

### 步骤 3：视频播放换成 Media3 ExoPlayer

- 替换文件：`ui/video/VideoStage.kt`（把 `MediaImage` 换成 `AndroidView { PlayerView(it) }`）+ `VideoPlayerState.kt`（`positionMs/playing` 改为收集 `Player` 状态）。
- 数据源：
  - **HTTP/WebDAV 直链**：`DefaultMediaSourceFactory` + `Datasource.Factory` 直接用，最简单、支持 Range 拖动；
  - **SMB**：ExoPlayer 没有官方 SMB DataSource，可选 ① 服务端 WebDAV/HTTP 暴露同一文件（推荐）② 用 jcifs-ng 写自定义 `DataSource`（需要支持 seek，实现成本高）③ 先落地到本地缓存再播。
- 转码（`PlaybackSettings.transcode`）：如果 NAS 上有 Jellyfin/Plex，直接把 `MediaItem.url` 指向它的 HLS/直链即可；否则在 NAS 侧用 ffmpeg 转（盒子解码能力有限时是唯一解）。
- 字幕：ExoPlayer `addTextOutput` / `MediaItem.SubtitleConfiguration`（`.srt/.ass` 需转换或服务端烧录）。
- 现有 UI 已经处理了：伪缓冲条（改成 `bufferedPercentage`）、快进快退 ±10s（`seekTo`）、自动连播、循环、字幕开关。

### 步骤 4：缩略图与元数据缓存

| 缓存对象 | 位置 | 建议 |
|---|---|---|
| 缩略图（列表/胶片条） | 本地磁盘 | 统一生成 ~320px 缩略图（前端生成或 NAS 侧 `thumb` 接口），Coil disk cache 即可；**不要用原图缩放列表** |
| 目录列表 / 相册 | 内存 + 磁盘 JSON | 冷启动先渲染上次快照，再后台刷新（避免电视开机转圈） |
| EXIF（拍摄时间、分辨率、GPS） | 磁盘 DB | Room 表 `media(id, dateTaken, w, h, size, ext, albumId, starred, exifJson)` |
| 收藏 / 星标 | 本地 DB + NAS 侧 `.nasPhotoTvStar` 文件或扩展属性 | 写回 NAS 才能多设备同步（见 §7「多 NAS 账户」） |

### 步骤 5：局域网发现（DLNA/SSDP/NSD）

- 现在 `NasConnection` 是手填（`host/port/username/sharePath`，`displayPath` 生成 `smb://media@192.168.1.20:445/photo`）。
- 建议：设置页「测试连接」旁边加「扫描局域网」→ ① `NsdManager` 发现 `_webdav._tcp` / `_smb._tcp` / `_http._tcp`；② SSDP `M-SEARCH` 找 `upnp:rootdevice`，读 `Location` 的 description xml 找媒体服务器；③ 兜底做 `/24` 网段 445/5005 端口快扫（仅演示用）。
- 注意：Android 对多播/广播有权限与省电限制；TV 盒子常把组播屏蔽，做超时降级即可。

### 步骤 6：认证与 TLS

- 凭据存储：`EncryptedSharedPreferences` / Keystore 包装；**不要**把密码写进 `SettingsStore` 的明文持久化。
- SMB：`smb://user:pwd@host` 只是展示；实际登录走 jcifs-ng `Nmblookup`/`CIFSContext`，密码不落日志。
- WebDAV：优先 Digest/Bearer Token（群晖/Nextcloud 的 app password），自签证书用 `network_security_config.xml` 做**域名固定信任**，不要全局 `trustAllCerts`。
- 现在 Manifest 里 `usesCleartextTraffic="true"` 是为了原型方便，量产应改为 `false` 并对局域网 IP 段单独放行（`domain-config`）。
- 多 NAS：`NasConnection` 是单条配置，量产需要 `List<NasConnection>` + 当前选中项（见 §7「多 NAS 账户 / 共享照片」）。

---

## 7. 量产改造清单

> 以下是"原型 → 可发布产品"的差距清单，按优先级排列；每条都标了动哪一层。

### P0 · 必须做（不做就是 demo）

| 项 | 现状 | 改造方案 | 动的层 |
|---|---|---|---|
| **空闲检测** | 外壳用 `System.currentTimeMillis()` + `View.OnKeyListener` 记录最后按键，每秒轮询 | ① 改用 `MainActivity.dispatchKeyEvent` 已有的时间戳 + `onUserInteraction()` + `View.OnGenericMotionListener`（遥控器鼠标/手柄）；② 加入 `PowerManager.isInteractive` 判断，屏幕熄灭时不算空闲（或直接进屏保）；③ 用协程 `select` 替代 1 秒轮询；④ 遮罩：播放视频/幻灯片时不进屏保 | Activity + 外壳 |
| **真实数据** | Mock | 步骤 1~4 | data |
| **真实解码** | 模拟播放 | 步骤 3 | video |
| **设置持久化** | `SettingsStore.persist()/restore()` 已写好，但**全工程没有任何调用点**（外壳启动时不 `restore()`、改动后不 `persist()`），所以重启回默认值 | 在 `MainActivity.onCreate` / 外壳首帧调 `SettingsStore.restore()`，在 `updateXxx` 之后或切后台时调 `persist()` | 外壳 |
| **崩溃与空态** | 已处理大部分空列表 | 给 `NasException` 类错误配 UI 提示 + 重试按钮；NAS 断线时屏保要退回「极光动态」而不是黑屏 | 各页 |

### P1 · 体验（决定"值不值得留在客厅"）

| 项 | 建议 |
|---|---|
| **Android TV Daydream（DreamService）** | 原型屏保是 App 内的 Compose 页面。要做到"电视待机自动起屏保"，两条路：① **合规路线**：实现 `DreamService`（`<service android:name=".PhotoDream" android:permission="android.permission.BIND_DREAM_SERVICE">` + `<intent-filter><action android:name="android.service.dreams.DreamService"/></intent-filter>`），在 `onAttachedToWindow` 里用 `setContent { ScreensaverScreen(...) }` 复用现有 Composable（**屏保页面本身不用改**，只要把 `onExit → finish()`）；用户/厂商在系统「屏保」设置里选中本 App，就能在熄屏→待机时自动出现。② **自家设备路线**：TV 厂商把 `DreamService` 设为出厂默认屏保，或盒子侧用 `ACTION_DREAM` 白名单。注意：DreamService 有 `setInteractive(false)`、`setFullscreen(true)`、`setScreenBright(false)` 等选项；进入 Dream 需要系统处于"Daydream 可用"状态（部分国产 TV 系统会裁剪）。 |
| **防烧屏** | 现有：像素位移 ±8dp / 2 分钟、夜间呼吸 alpha 0.90~1.00（15s 单程）、文字白色 90%、无纯白大面积、背景压暗 30~45%、极光星点不闪。可加强：① 长时间（>2h）后降低整体亮度 10~15%；② 每 30 分钟做一次 3~5 分钟的"文字整体淡出→只留背景"；③ OLED 电视遵循厂商 API（如 `Display.getHdrCapabilities`/厂商私有亮度接口）；④ 时钟/天气按 StandBy 式在 1/2/3 个布局间**换位**，而不是只在原地抖 8dp。 |
| **低功耗** | 屏保常驻必须做到：① 屏保期间不跑每秒一帧的无限动画（现有 `rememberInfiniteTransition` 会跟 Choreographer 走，被系统暂停时自动冻结，已经是好的基础）；② 时钟只在秒变化的 Text 重组（`ClockParts` 已分层）；③ 背景照片每 10s 才换，Ken Burns 26s 单程；④ `WAKE_LOCK` 目前没用上，用 `FLAG_KEEP_SCREEN_ON` 足够；⑤ 关掉不必要的 `requestAnimationFrame`，屏幕变暗后降帧。 |
| **开机自启 / 默认启动器** | 客厅设备通常需要"开机即相框"：① TV 厂商预装可以把 App 设为 `HOME`（需要 `<category android:name="android.intent.category.HOME"/>`）；② 普通 Android TV 用 `BOOT_COMPLETED` 广播 + 前台服务拉起（需 `RECEIVE_BOOT_COMPLETED` 权限，Google TV 对后台启动有额外限制）；③ 用 `android.intent.action.MAIN` + `singleTask` 已有，只需加自启；④ 注意 Google Play 对 `HOME`/自启动的政策要求。 |
| **多 NAS 账户 / 共享照片** | `NasConnection` 单条 → 列表 + 当前项；每台 NAS 独立缩略图缓存命名空间；「家庭共享」：家人把自己的 NAS/相册加入同一台电视（需要账号体系与权限模型）。 |
| **隐私（家庭照片上云）** | 这是本品最核心的信任点：① 所有数据**端到端留在局域网**，不做任何云端索引/上传；② 联网仅用于天气（可关，`showWeather`）与自动更新；③ 天气接口要用明文隐私说明（城市级定位或手填城市，避免精确定位）；④ 卸载/换机提供"清除本地缓存与凭据"入口；⑤ 若做远程访问（外网看家里照片），必须显式开启 + 二次确认。 |
| **性能（4K 电视）** | 4K 面板 + 内存小的盒子是最大风险：① **永远不要原图直读全屏**：列表用 320px 缩略图、看图用 1280~1920px、屏保用 1280px（现有 `MediaItem`/`MediaImage` 已带 `targetWidth` 分级，保持这个约束）；② `BitmapFactory`/Coil 都做 `inSampleSize`，并限制位图缓存上限；③ 预取：屏保下一张提前 1 个间隔解码；胶片条按可见范围预取；④ 帧率：屏保动画目标 30fps（10-foot UI 不需要 60）；⑤ 冷启动 < 2s：先渲染缓存快照。 |

### P2 · 工程化

- 单元测试：`PhotoViewerState` / `VideoPlayerState`（纯 Kotlin 状态机，最好测）、`LibraryData` 派生逻辑、`lunarLabel`；
- UI 测试：`compose-ui-test` 的 `performKeyInput` 覆盖遥控器路径（可用 `createAndroidComposeRule` + 1920×1080）；
- 基线：`baseline-profile` + `startup` profile；
- CI：`assembleDebug` + `compileDebugKotlin` + lint + 截图对比（Paparazzi/Roborazzi）；
- 文案外置：现在所有中文直接写死（原型有意为之），量产抽 `strings.xml` + 多语言；
- 无障碍：TalkBack 的 `contentDescription` 已部分具备，需补齐卡片/按钮语义与焦点顺序。

---

## 8. 已知限制（原型**没有**做的事）

| # | 限制 | 说明 / 影响 |
|---|---|---|
| 1 | **没有真实视频解码** | `VideoPlayerState` 用 100ms 时间步进模拟进度（`withFrameNanos` 计量真实流逝），画面是 `res/raw` 的封面图。播放、暂停、快进、连播、循环、字幕**全是 UI 行为**，不产生声音与真实帧。`VideoStage.kt` 与 `VideoPlayerState.tick` 已留好接 Media3 的替换点。 |
| 2 | **没有真实 NAS 连接** | 零网络请求。`MockMediaRepository.ping()` 只是 `Thread.sleep(300)` 后返回 `true`；`SettingsStore.reportNasResult` 从未被真实调用；设置页的「测试连接/重扫」是 `SettingsFeedback` 里的纯 UI 假状态（首次必失败一次，之后成功）。 |
| 3 | **没有字幕文件** | 字幕开关只显示一条写死的中文样片（`SAMPLE_SUBTITLE`），不解析 `.srt/.ass`（设置页 `PB_SUBTITLES` 的提示文案写的是"同名 srt/ass 自动加载"，那是规划）。 |
| 4 | **没有 HEIC 解码** | Mock 数据里有 `extension = "HEIC"`，但实际加载的是同名 JPG 占位图；API 26/27 无系统 HEIC 解码器。 |
| 5 | **没有账号体系** | 无登录、无多用户、无儿童模式；`NasConnection` 只有 `username`（没有密码字段），设置页的「账号」行是在候选字母/序号间循环的演示值。 |
| 6 | **收藏不持久化** | 看图页的星标是 `PhotoViewerState.starredOverrides`（内存），退出即丢；没有写回 `MediaItem.starred` 或数据层。 |
| 7 | **没有真实搜索 UI** | 顶栏「搜索」按钮只会切到「全部照片」Tab；`MediaRepository.search()` 与 `MockMediaRepository.search()` 已实现但没有输入法/搜索页（电视端软键盘体验差，建议按首字母/日期筛选）。 |
| 8 | **相册分组是 Mock 结果** | 「人物/地点/回忆/收藏」智能相册在 `MockData` 里是静态数据，没有真实人脸聚类/GPS 聚合/时间回忆生成。 |
| 9 | **农历是近似值** | `lunarLabel` 以 2026-10-10 = 九月初一为锚点、29/30 天交替推算；干支年公式准确，月/日在锚点附近准确，远日期会有偏差。 |
| 10 | **天气是本地假数据** | `MockWeatherProvider` 按小时轮换 3 组预置天气，不请求任何网络。 |
| 11 | **设置不落盘（默认）** | `SettingsStore.persist()/restore()` 已实现（SharedPreferences `nas_photo_tv_settings`），但外壳没有在启动时调用 `restore()`，也没有在任何改动后调用 `persist()`，所以重启回默认值。 |
| 12 | **不支持长按/双击/手势** | 契约明确不支持；看图页没有双指缩放/旋转/滑动翻页。 |
| 13 | **不扫码 / 不投屏 / 不分享** | 没有手机端配网、没有 DLNA 投屏出口、没有分享/下载。 |
| 14 | **性能只验证到 1080p** | 所有尺寸按 1920×1080 dp 画布设计（1080p 电视 1dp≈1px）；4K 面板下依赖系统 density 缩放，未做 4K 实测。 |
| 15 | **播放设置的"NAS 端转码/字幕"只是展示** | `transcode` 只影响播放器右上角角标文案与信息面板里的一行；`showSubtitles` 只控制字幕层初始可见性。 |
| 16 | **构建环境验证状态** | 工程可编译（`app/build/outputs/apk/debug/app-debug.apk` 已产出，约 16 MB）。本轮文档改动只涉及 md 文件，未重跑编译。 |

### 本轮文档实测到的环境事实

- 本机 JDK 24.0.1 + Gradle 8.10.2 + AGP 8.7.3 + compileSdk 35（`local.properties` → `E:\Android\Sdk`）；
- debug APK 已成功产出（`app/build/outputs/apk/debug/app-debug.apk`，16,361,957 字节）；
- 已连接的 `emulator-5554` 为手机镜像（SDK 35 / Android 15、`sdk_gphone64_x86_64`、1080×2400、density 420），
  **没有** `android.software.leanback` 特性，因此直接安装会因 `required="true"` 被拒 —— 请按 §3.4 用 TV 镜像或临时把 `required` 改 `false`；
- 本机可用 AVD：`Pixel_8_API_35`、`Finexy_API_35_Clean`（都是 `system-images\android-35\google_apis\x86_64` 手机镜像，未安装 TV 镜像）。

---

## 9. 相关文档

| 文档 | 内容 |
|---|---|
| [CONTRACT.md](CONTRACT.md) | 团队协作契约：代码所有权、公共 API、遥控器交互规范、编译方式 |
| [DESIGN.md](DESIGN.md) | 设计规范：设计原则、**完整设计 token 表**、六个页面的 ASCII 结构图（含尺寸）、焦点规范、屏保设计（StandBy 对应关系 + 防烧屏）、动效规范、可访问性 |
| [docs/acceptance.md](docs/acceptance.md) | 验收清单：按页面列出的可勾选检查项（含空态/边界/防烧屏/遥控器全路径） |
| [tools/gen_assets.py](tools/gen_assets.py) | 占位素材生成脚本（`python tools/gen_assets.py`，需要 Pillow） |
