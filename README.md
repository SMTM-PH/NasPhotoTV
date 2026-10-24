# NAS Photo TV

> **在电视上看 NAS 里的照片与视频，并把电视变成一台会呼吸的电子相框。**

Android TV 原型 App（Kotlin + Jetpack Compose）。包名 `com.nasphoto.tv`，单 Activity、纯 Compose、无 TV 专用库；
屏保待机显示对标 iPhone 的 StandBy —— 大号时钟 + 照片氛围 + 天气 + 防烧屏。

- 版本：`1.0.0-prototype`（versionCode 1）
- 技术栈：Kotlin 2.0.21 / AGP 8.7.3 / Compose BOM 2024.12.01（Compose 1.7.6、material3 1.3.1）/ minSdk 26、compileSdk 35、targetSdk 34
- 数据来源：**真实飞牛相册**（`data/fn/`：WSS 加密登录 + 签名 HTTP + AccessToken 注入 + 局域网自动发现），直连 NAS 原片串流

---

## 0. 快速开始（TL;DR）

```bash
# 1. 构建（Windows 用 gradlew.bat，Linux/macOS 用 ./gradlew）
./gradlew :app:assembleDebug

# 2. 安装到已连接的电视 / 模拟器
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 3. 启动（Activity 名与包名一致：com.nasphoto.tv/.MainActivity）
adb shell am start -n com.nasphoto.tv/.MainActivity
```

APK 产物：`app/build/outputs/apk/debug/app-debug.apk`（约 21 MB，含 Media3/OkHttp/Coil）。

不想自己构建？可直接从 [GitHub Releases](https://github.com/SMTM-PH/NasPhotoTV/releases) 下载 APK 安装包。

首次运行需要在设置页填 NAS 地址（默认 `5667`）+ 账号密码，或等局域网自动发现。

---

## 1. 功能清单

| 模块 | 代码位置 | 已实现 |
|---|---|---|
| **媒体库 / 相册浏览** | `ui/library/` | 顶栏（Logo + NAS 状态点 + 5 个 Tab + 搜索/屏保/设置按钮）；「全部照片」Tab 有 Hero 大图 + 最近添加横排 + 相册横排；「年 · 月 · 日」Tab 四级下钻（年 → 月 → 日 → 当天媒体网格）；「相册」Tab 4 列网格；「最近添加」Tab 6 列网格；「收藏 · 人物 · 地点」Tab（收藏网格 + 人物/地点/回忆横排）；相册详情页（返回按钮 + 6 列媒体网格）；各层级空状态 |
| **全屏看图** | `ui/viewer/` | **图片优先铺满整屏**（无常驻侧栏，信息都是瞬态浮层）；左上 LIVE 徽标 + 控制栏可见时的状态句/来源/拍摄/标题/序号；`UP` 展开尺寸/文件/ID；左右切换（循环）+ 方向滑动动画；7 个工具按钮（上一张/下一张/信息/收藏/幻灯片/放大查看/关闭）；缩略图胶片条；幻灯片自动播放（5s/张，方向键暂停）；2× 放大 + 方向键平移；视频项显示封面 + ▶ |
| **视频播放** | `ui/video/` | **真实 Media3 ExoPlayer 解码**，走带 AccessToken 的 OkHttp 数据源串流 NAS 原片；全屏播放 + 控制层 3 秒自动隐藏；播放/暂停；进度条 + 当前/总时长；快进快退 ±10s；媒体键 `NEXT/PREVIOUS` 切换视频；播完自动连播；**解码失败显示可见错误原因**（4K/10bit 超规格等，详见 §8） |
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

| 屏保 · 翻页数字 | 屏保 · 巨幕时钟 |
|---|---|
| ![屏保翻页数字](docs/screenshots/05-screensaver-flip.png) | ![屏保巨幕时钟](docs/screenshots/06-screensaver-date.png) |

| 设置页 | |
|---|---|
| ![设置页](docs/screenshots/07-settings.png) | |

> 截图位于 `docs/screenshots/`（1920×1080），全部来自连接真实飞牛 NAS 的 Android TV 模拟器实测画面，命名即上表的 `01-library.png` … `07-settings.png`。
> 摄于 10 月 20 日版本；此后看图页已改为图片优先铺满整屏（信息浮层化）、屏保前景不再显示图片信息，最新布局以 §4 的文字描述为准。

---

## 3. 运行环境与构建

### 3.1 环境要求

| 项 | 要求 | 本机实测 |
|---|---|---|
| JDK | **17+**（工程 `sourceCompatibility/targetCompatibility = 17`、`jvmTarget = 17`） | JDK 17 (Temurin) 实测通过 |
| Gradle | 用仓库自带 Wrapper（**8.10.2**），不要用全局 gradle | 已含 `gradle/wrapper/gradle-wrapper.jar` |
| Android SDK | **compileSdk 35**（platforms;android-35 + build-tools 34/35），路径写在 `local.properties` 的 `sdk.dir` | 已用 build-tools 34/35 实测 |
| 运行设备 | Android **8.0（API 26）及以上**；Android TV / Google TV / 电视盒子；`android.software.leanback` 为 **required**，touchscreen 非必需 | Android TV 官方镜像（API 36 / 1920×1080 / density 320）实测通过 |

Manifest 已声明：`leanback` required + `touchscreen` not required + `LEANBACK_LAUNCHER` 与 `LAUNCHER` 双入口、`android:banner`、
`INTERNET` / `ACCESS_NETWORK_STATE` / `WAKE_LOCK` 权限、`usesCleartextTraffic="true"`、`screenOrientation="landscape"`、
`launchMode="singleTask"`、`configChanges` 含 `uiMode|screenSize|screenLayout`。

### 3.2 构建

```bash
# Debug（推荐）—— Windows 用 gradlew.bat，Linux/macOS 用 ./gradlew
./gradlew :app:assembleDebug
# 只验证编译（最快）
./gradlew :app:compileDebugKotlin
# Release（未签名、未混淆，仅供体积评估）
./gradlew :app:assembleRelease
```

内存受限的机器（如 8 GB NAS / 小主机）建议显式压低堆与并行度，否则 Gradle daemon 可能被 OOM kill：

```bash
./gradlew --no-daemon --no-parallel \
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" :app:assembleDebug
```

产物路径：
- Debug：`app/build/outputs/apk/debug/app-debug.apk`
- Release：`app/build/outputs/apk/release/app-release-unsigned.apk`

> ⚠ 团队协作注意：**同一时刻只跑一个 gradle**（多人/多 Agent 并行编译会争抢 `~/.gradle` 与模块构建目录）。
> 需要互斥时用一个简单的目录锁（如 `mkdir .buildgate`）串行化，跑完删掉即可。

### 3.3 安装到电视 / 盒子

```bash
# 第一次连接（电视需在 设置 → 系统 → 开发者选项 中开启 ADB 调试）
adb connect 192.168.1.50:5555
adb devices                      # 确认设备状态为 device

# 安装 / 覆盖安装
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 启动
adb shell am start -n com.nasphoto.tv/.MainActivity

# 遥控器按键（无真机时也能验证焦点导航）
adb shell input keyevent KEYCODE_DPAD_RIGHT
adb shell input keyevent KEYCODE_DPAD_CENTER

# 看崩溃日志
adb logcat -s AndroidRuntime:E
```

也可以用 U 盘 + 电视自带「文件管理」安装 APK（需允许安装未知来源应用）。

### 3.4 模拟器 / 无头验证

工程自带 `@Preview(widthDp = 1920, heightDp = 1080)`（媒体库/看图/屏保/设置等），
**最省事的是在 Android Studio 里直接看 Preview**，不依赖任何设备。

> ⚠ Manifest 里 `android.software.leanback` 是 `required="true"`，**手机镜像不声明 leanback**，
> 直接 `adb install` 会被特性校验拒绝。请用 TV 镜像（下表第一行）。

| 场景 | 做法 |
|---|---|
| **推荐：Android TV 镜像** | SDK Manager 安装 `system-images;android-36;android-tv;x86_64`（或 android-35 的 TV 版），新建 AVD 时选 `tv_1080p` 设备模板（1920×1080 / density 320 → 1dp ≈ 1px，与设计稿 1:1） |
| **只有手机 AVD** | 临时把 `android.software.leanback` 的 `required` 改 `false`（仅本地验证，**不要提交**） |
| **真机电视** | 无需改动，直接安装 |

#### 无头（headless）跑 TV 镜像的四个关键参数

在没有显示器的服务器/NAS 上用 `-no-window` 跑 TV 镜像时，下面四个参数缺一个就会出现**周期性 SIGSEGV（exit 139）**，
表现为「跑几分钟容器就退出」，很容易被误判成内存不足：

```bash
emulator -avd tv36 -no-window -no-audio -no-snapshot \
  -gpu swangle_indirect \      # swiftshader_indirect 在渲染 Compose 模糊/辉光时会崩
  -feature -ModemSimulator \   # 默认 modem 模拟器会因容器内 IPv6 解析失败而崩
  -timezone Asia/Shanghai \    # 否则屏保时钟差 8 小时（系统时区为 UTC）
  -cores 2 -memory 1536
# 容器还需 --shm-size=2g：Docker 默认 64MB 不够 1080p 的帧缓冲
```

#### 用 adb 当遥控器

```bash
adb shell input keyevent 19   # UP
adb shell input keyevent 20   # DOWN
adb shell input keyevent 21   # LEFT
adb shell input keyevent 22   # RIGHT
adb shell input keyevent 23   # DPAD_CENTER（确认）
adb shell input keyevent 4    # BACK
adb shell input keyevent 127  # MENU
adb shell input keyevent 88   # MEDIA_NEXT（切下一个视频）
```

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

形状：**全屏播放**（`Box` 铺满，视频用 `PlayerView` + `RESIZE_MODE_FIT` 自适应），控制层是覆盖在画面上的瞬态浮层。

| 键 | 控制层显示中 | 控制层隐藏时 |
|---|---|---|
| `UP` / `DOWN` | 唤出 / 隐藏控制层 | 唤出控制层 |
| `LEFT` / `RIGHT` | 快退 / 快进 10 秒（并唤出控制层） | 同左 |
| `ENTER` / `DPAD_CENTER` | 播放 / 暂停 | 播放 / 暂停 |
| `BACK` / `ESC` | 关闭播放器，返回媒体库 | 同左 |
| 媒体键 | `PLAY/PAUSE/PLAY_PAUSE` 播放暂停；`NEXT` / `SKIP_FORWARD` 下一个视频；`PREVIOUS` / `SKIP_BACKWARD` 上一个视频 | 同左 |
| 任意按键 | 每次操作都会重启控制层 3 秒倒计时 | 同上 |

其他行为：
- 控制层在 **3 秒** 无操作后自动隐藏；
- 顶部条：标题 + `来自 <contextTitle>` + 右上角 `第 n / m 个`；
- 底部：进度条（已播放青色）+ 当前时间 / 总时长 + 右侧按键提示；
- 播完分支：`autoPlayNext && 有下一个` → 自动切下一个；否则停下并显示控制层；
- **解码失败**：`onPlayerError` 会显示「这个视频无法播放」+ 具体原因（设备解码能力不足 / 格式不支持 / 网络失败 / 文件不存在），并提示可用「下一曲」跳过；这对 4K 60fps HEVC 10bit 等超出电视解码能力的素材尤其重要；
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
<仓库根目录>
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
         │  ├─ video/VideoPlayerScreen.kt        播放器页：全屏 Media3 播放 + 顶部标题条 + 底部进度/播放键 + 解码失败提示
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
            ├─ FnOsMediaRepository.kt            真实飞牛相册实现（时间线/相册/人物/视频串流）
            ├─ SettingsStore.kt                  ★ 全局设置（Compose State）+ cycle* 便捷方法 + SharedPreferences persist/restore
            ├─ MediaCache.kt                    最近一次拉取快照（设置页屏保预览/播放摘要读它）
            ├─ FavoritesStore.kt                收藏（内存态，见 §8）
            ├─ AppContextHolder.kt              全局 applicationContext
            └─ fn/
               ├─ FnOsApi.kt                    WSS 握手 + 加密登录 + 签名请求
               ├─ FnOsTransport.kt              OkHttp 单例（TLS/TOFU/cookie/token 注入）+ ExoPlayer 数据源
               ├─ FnOsCrypto.kt FnOsSigner.kt FnOsReqId.kt   加密/签名/reqid
               ├─ FnOsTofu.kt                   首次信任 + 指纹变化检测
               └─ LanNasDiscovery.kt            局域网 /24 扫描 + 证书识别
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

## 6. 真实 NAS 接入状态（已实现）

> 本章原为「接入路线图」，数据层与播放层现已全部落地，故改写为**实现说明**。

### 6.1 已实现

| 能力 | 位置 | 说明 |
|---|---|---|
| 局域网自动发现 | `data/fn/LanNasDiscovery.kt` | 扫本机所在 /24 网段的 5667 端口，按 TLS 证书里的 `O=fnOS` 识别飞牛设备（兼容自签证书），只读证书不发凭据 |
| WSS 加密登录 | `data/fn/FnOsApi.kt` | `wss://<host>:5667/websocket?type=main` → `util.crypto.getRSAPub` 取 RSA 公钥与 `si` → AES-CBC 加密 `user.login` → 拿 AccessToken |
| 签名 HTTP | `data/fn/FnOsSigner.kt` `FnOsCrypto.kt` `FnOsReqId.kt` | 每个业务请求带签名与递增 reqid |
| TOFU 证书信任 | `data/fn/FnOsTofu.kt` | 首次见到的证书指纹落盘，变化时提示并可重试 |
| AccessToken 注入 | `data/fn/FnOsTransport.kt` | OkHttp 拦截器统一注入；Coil 图片与 ExoPlayer 视频共用同一客户端 |
| 相册数据 | `data/FnOsMediaRepository.kt` | 时间线 `gallery/getList`、相册 `album/list` + `album/photos`、共享相册、AI 人物、视频串流 `/p/api/v1/stream/v/<id>` |
| 真实解码 | `ui/video/VideoPlayerScreen.kt` | Media3 ExoPlayer + 带 token 的 OkHttp 数据源；解码失败显示可见原因 |

### 6.2 尚未实现（详见 §8）

- 4K / 10bit / 高帧率素材的自动降级或 NAS 端转码请求；
- 字幕文件解析（`.srt/.ass`）；
- 搜索输入界面；
- 收藏持久化写回数据层。

## 7. 量产改造清单

> 以下是"原型 → 可发布产品"的差距清单，按优先级排列；每条都标了动哪一层。

### P0 · 必须做（不做就是 demo）

| 项 | 现状 | 改造方案 | 动的层 |
|---|---|---|---|
| **空闲检测** | 外壳用 `System.currentTimeMillis()` + `View.OnKeyListener` 记录最后按键，每秒轮询 | ① 改用 `MainActivity.dispatchKeyEvent` 已有的时间戳 + `onUserInteraction()` + `View.OnGenericMotionListener`（遥控器鼠标/手柄）；② 加入 `PowerManager.isInteractive` 判断，屏幕熄灭时不算空闲（或直接进屏保）；③ 用协程 `select` 替代 1 秒轮询；④ 遮罩：播放视频/幻灯片时不进屏保 | Activity + 外壳 |
| **真实数据** | ✅ 已接入飞牛相册（WSS 登录 + 签名 HTTP） | 完成 | data |
| **真实解码** | ✅ 已接入 Media3 ExoPlayer | 补 4K/10bit 素材的降级与转码 | video |
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

> 数据层已接入真实飞牛相册（WSS 加密登录 + 签名 HTTP + AccessToken 注入），
> 下表只列**仍未实现**的能力。原「全部 Mock」时期的条目已作废。

| # | 限制 | 说明 / 影响 |
|---|---|---|
| 1 | **4K / 10bit / 高帧率视频可能无法播放** | 素材直接来自相机/无人机原片。实测 DJI 4K 60fps HEVC 10bit 在模拟器与部分电视盒子上被判 `NO_EXCEEDS_CAPABILITIES`。已加 `onPlayerError` 提示与「下一曲」跳过，但**没有自动转码或降级选流**。 |
| 2 | **没有字幕文件** | 字幕开关只显示一条写死的中文样片，不解析 `.srt/.ass`。 |
| 3 | **没有 HEIC 解码（API 26/27）** | 低版本 Android 无系统 HEIC 解码器；RAW 走 NAS 生成的高清 JPG 预览规避。 |
| 4 | **收藏不持久化** | 看图页星标是内存态 `starredOverrides`，退出即丢，未写回数据层。 |
| 5 | **没有搜索输入 UI** | 顶栏「搜索」只会切到「全部照片」Tab；`MediaRepository.search()` 已实现但无电视端输入界面。 |
| 6 | **人物/地点依赖 NAS 侧聚类** | 「收藏 · 人物 · 地点」读的是 `ai-person` 接口，NAS 未开启 AI 相册时为空。 |
| 7 | **农历是近似值** | `lunarLabel` 以锚点推算，远日期有偏差。 |
| 8 | **天气是本地假数据** | `MockWeatherProvider` 按小时轮换预置天气，不请求网络。 |
| 9 | **不支持长按/双击/手势** | 看图页无双指缩放/旋转/滑动翻页（遥控器 2× 放大 + 方向键平移代替）。 |
| 10 | **不扫码 / 不投屏 / 不分享** | 无手机端配网、无 DLNA 投屏出口。 |
| 11 | **性能只验证到 1080p** | 尺寸按 1920×1080 设计；4K 面板未实测。 |
| 12 | **播放设置里的「转码」只是展示** | `transcode` 只影响角标文案，不真正请求 NAS 转码流。 |

### 本轮文档实测到的环境事实

- **本次实测环境（Linux / 飞牛 fnOS）**：JDK 17 (Temurin) + Gradle 8.10.2 + AGP 8.7.3 + compileSdk 35 + build-tools 34/35；
- debug APK 已成功产出（`app/build/outputs/apk/debug/app-debug.apk`，约 21 MB）；
- 运行环境：Android TV 官方系统镜像 `system-images;android-36;android-tv;x86_64`（Android 16、`sdk_google_atv64_x86_64`、1920×1080 / density 320），
  在 Docker + KVM 中无头运行（`-no-window`），通过 `adb` 驱动遥控器按键；
- **模拟器稳定性关键参数**（缺一会周期性 SIGSEGV）：
  `-gpu swangle_indirect`（`swiftshader_indirect` 在渲染 Compose 模糊/辉光时会崩）、
  `-feature -ModemSimulator`（默认 modem 模拟器会因容器内 IPv6 解析失败而崩）、
  `--shm-size=2g`（Docker 默认 64MB 不够 1080p 帧缓冲）、`-timezone Asia/Shanghai`；
- **已用真实 NAS 数据跑通**：登录 → 时间线（9 个年份 / 2470 项）→ 年/月/日四级下钻 → 相册（33 个文件夹）→ 全屏看图 → 屏保待机 → 设置页；
- 已知差异：本机 NAS 视频素材为 DJI 4K 60fps HEVC 10bit，超出模拟器解码能力，故视频「播放」未取得真实画面，
  但解码器启动、错误提示、切换视频三条路径均已验证。

---

## 9. 相关文档

| 文档 | 内容 |
|---|---|
| [CONTRACT.md](CONTRACT.md) | 团队协作契约：代码所有权、公共 API、遥控器交互规范、编译方式 |
| [DESIGN.md](DESIGN.md) | 设计规范：设计原则、**完整设计 token 表**、六个页面的 ASCII 结构图（含尺寸）、焦点规范、屏保设计（StandBy 对应关系 + 防烧屏）、动效规范、可访问性 |
| [docs/acceptance.md](docs/acceptance.md) | 验收清单：按页面列出的可勾选检查项（含空态/边界/防烧屏/遥控器全路径） |
| [tools/gen_assets.py](tools/gen_assets.py) | 占位素材生成脚本（`python tools/gen_assets.py`，需要 Pillow） |
