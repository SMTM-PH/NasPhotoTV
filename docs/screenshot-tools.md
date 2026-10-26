# App 截图工具推荐（Android TV）

> 面向本项目 `com.nasphoto.tv` 的截图工具选型与实操指南。
> 目标画布：**1920×1080（16:9）**，10-foot UI，`density 320`（1dp ≈ 1px）。
> 文档已按「开发调试 / 文档配图 / 上架商店」三种用途分类，可按需跳读。

---

## 0. 速查：该选哪个

| 你的目的 | 推荐工具 | 一句话理由 |
|---|---|---|
| **日常开发截图**（快速抓一张看效果） | **`adb screencap`** | 零安装、一条命令、无失真 |
| **录制交互过程 / 演示视频** | **`scrcpy`** | 实时镜像 + 录制，还能用键鼠当遥控器 |
| **批量截多张并拼成对比图** | **`adb` + `ffmpeg`** | 项目已有 `ffmpeg`，可自动裁切/拼接 |
| **上架应用商店的规范截图** | **Figma / Canva + 1920×1080 模板** | 需要统一边框、卖点文案、多语言版本 |
| **真机电视截图（非模拟器）** | **`adb connect` + `screencap`** | 电视开 ADB 调试后同样可截，无需额外软件 |
| **需要带透明度的像素级分析** | **Android Studio Layout Inspector** | 可查看图层、颜色、尺寸，但**不能替代截图** |

---

## 1. adb screencap（首选，零依赖）

Android 自带的系统级截屏，**不依赖任何第三方 App**，在电视/盒子/模拟器上都能用。

### 1.1 基础用法

```bash
# 单次截图并拉到本地
adb shell screencap -p /sdcard/shot.png
adb pull /sdcard/shot.png ./docs/screenshots/01-library.png
```

更简洁的**一行管道写法**（本次实测可用，PNG 直出无失真）：

```bash
adb exec-out screencap -p > ./docs/screenshots/01-library.png
```

> ⚠️ **必须用 `exec-out`，不要用 `shell`**。
> `adb shell screencap -p > file.png` 会因 Windows/Linux 的 LF→CRLF 转换导致 PNG 损坏（表现为打不开或花屏）。

### 1.2 多设备时指定目标

```bash
adb devices                      # 列出设备，例如 emulator-5554
adb -s emulator-5554 exec-out screencap -p > shot.png
```

### 1.3 优缺点

| 优点 | 缺点 |
|---|---|
| 系统级，无兼容性问题 | 只出**单帧**，不能录视频 |
| 原始分辨率、无压缩失真 | 无光标/焦点提示叠加 |
| 可脚本化、可批量 | 电视休眠/屏保时截到的是黑屏或屏保画面 |
| 不占用设备前台 | 需要设备已连 adb |

### 1.4 电视场景注意事项

- **先退出屏保**再截图：本项目 1 分钟无操作即进屏保（`ScreensaverScreen`），会截到时钟画面而非目标页面；
- **遥控器焦点**是 10-foot UI 的关键信息，截图前先用 `adb shell input keyevent` 把焦点移到目标控件，让焦点辉光出现在图里；
- 真正需要「无焦点」的干净截图时，可以按一次 `DOWN` 让焦点淡出，或在设置里关闭焦点辉光（仅调试用）。

```bash
# 把焦点移到目标位置后再截图（示例：首页顶栏第 2 个 Tab）
adb shell input keyevent 19   # UP
adb shell input keyevent 22   # RIGHT
adb exec-out screencap -p > 02-chrono.png
```

---

## 2. scrcpy（录屏 + 实时镜像，强烈推荐）

> 开源免费（Apache-2.0），Genymobile 出品，**无需 root、无需在设备装 App**。
> 仓库：https://github.com/Genymobile/scrcpy

### 2.1 为什么适合 TV 项目

- **实时看到电视画面**，键鼠直接当遥控器（方向键 = DPAD，Enter = OK，Esc = Back）；
- 支持**录制 MP4**，适合做功能演示、Bug 复现；
- 支持**裁剪与分辨率限制**，低带宽下也流畅；
- 可直接截图为 PNG（`Ctrl+s` 或 `--record`）。

### 2.2 安装

```bash
# Ubuntu / Debian
sudo apt install scrcpy

# macOS
brew install scrcpy

# Windows（scoop / choco）
scoop install scrcpy
choco install scrcpy
```

### 2.3 常用命令

```bash
# 实时镜像（自动匹配设备分辨率）
scrcpy

# 无窗口 + 直接录制成 mp4（适合服务器/无桌面环境）
scrcpy --no-window --record=demo.mp4
# 录完后按 Ctrl+C 结束，文件自动封装完成

# 限制分辨率与码率（低配 NAS / 远程时更流畅）
scrcpy --max-size 1280 --video-bit-rate 2M

# 指定设备（多设备时）
scrcpy -s emulator-5554

# 只接管键盘（鼠标仍归本机，方便边操作边截图）
scrcpy --keyboard=uhid --mouse=disabled
```

### 2.4 无头服务器上的注意点

本项目的验证环境是**无显示器的 NAS**（`--no-window` 无头模拟器）。这种情况下：

- `scrcpy` 需要显示服务（X11/Wayland）才能开窗口，无头时请只用 `--no-window --record`；
- 若还要「边录边看」，可配合 `Xvfb` 虚拟显示或直接用第 3 节的 `ffmpeg` 方案。

### 2.5 优缺点

| 优点 | 缺点 |
|---|---|
| 实时画面 + 可录制 | 需要安装客户端程序 |
| 键鼠当遥控器，效率高 | 无头服务器需额外配置 |
| 支持裁剪、降码率 | 长时间录制占用磁盘 |
| 跨平台 | 部分老版本对 Android TV 的 DPAD 映射需手动校准 |

---

## 3. adb + ffmpeg（批量与合成，项目已具备）

本项目环境中 `ffmpeg` 已可用（`/usr/bin/ffmpeg`），适合**自动化批量截图 + 拼图**。

### 3.1 批量抓取多个页面

```bash
#!/usr/bin/env bash
# tools/batch-shots.sh —— 按预设路径批量截图
set -e
OUT=docs/screenshots
mkdir -p "$OUT"

shot() {
  local name=$1
  adb exec-out screencap -p > "$OUT/$name.png"
  echo "saved $OUT/$name.png"
}

# 首页 → 时间线 → 相册 → 设置
adb shell am start -n com.nasphoto.tv/.MainActivity >/dev/null; sleep 3
shot 01-library

adb shell input keyevent 19 >/dev/null; sleep 1   # UP 到顶栏
adb shell input keyevent 22 >/dev/null; sleep 1   # RIGHT 到「年·月·日」
adb shell input keyevent 23 >/dev/null; sleep 2   # OK
shot 02-chrono
```

### 3.2 用 ffmpeg 处理截图

```bash
# 统一缩放为 1920x1080（防止个别设备返回不同分辨率）
ffmpeg -i raw.png -vf scale=1920:1080:flags=lanczos -y out.png

# 裁掉电视系统的状态栏（若截图含系统 UI）
ffmpeg -i raw.png -vf "crop=1920:1080:0:0" -y out.png

# 两张截图横向拼接做对比图（Before / After）
ffmpeg -i before.png -i after.png -filter_complex \
  "[0:v][1:v]hstack=inputs=2" -y compare.png

# 2×2 宫格拼图
ffmpeg -i a.png -i b.png -i c.png -i d.png -filter_complex \
  "[0:v][1:v]hstack[t];[2:v][3:v]hstack[b];[t][b]vstack" -y grid.png

# 截图序列合成延时视频（例如屏保时钟走秒）
ffmpeg -framerate 2 -i frame_%03d.png -c:v libx264 -pix_fmt yuv420p clock.mp4
```

### 3.3 跳过手写脚本：直接用本项目已有工具

项目里已有 `tools/shot.ps1`（Windows/PowerShell 版）。Linux/macOS 可用等价的：

```bash
# 用法：./tools/shot.sh <name>
#!/usr/bin/env bash
set -e
NAME=${1:-shot}
adb exec-out screencap -p > "docs/screenshots/${NAME}.png"
echo "saved docs/screenshots/${NAME}.png"
```

---

## 4. 应用商店截图制作（上架用）

> 商店截图不是「设备截图」，而是**带设计排版的宣传图**，通常需要统一边框、卖点文案、多语言。

### 4.1 官方尺寸要求（Android TV / Google Play）

| 用途 | 尺寸 | 格式 | 数量 |
|---|---|---|---|
| **TV 截图（必填）** | **1920×1080**（16:9） | PNG / JPEG | 3 张起，最多 8 张 |
| **TV Banner（必填）** | **1280×720** | PNG / JPEG | 1 张 |
| **TV Icon** | 512×512 | PNG（32-bit） | 1 张 |
| 手机/平板截图（若同时上架） | 短边 ≥ 1080 | PNG / JPEG | 2~8 张 |

> ⚠️ **上表为社区通用经验值，正式提交前请以 Play Console 上传页的实时提示为准**。
> Google 会不定期调整素材规范，本环境网络受限无法在线复核官方文档，
> 因此这里不给「绝对权威」的承诺——**以控制台报错为准**是最稳妥的做法。
>
> ⚠️ Google Play 对 **Android TV** 有独立审核要求：截图必须展示 TV 界面（leanback 布局），
> 不能直接用手机版截图充数，否则会被拒。

### 4.2 推荐设计工具

| 工具 | 适合谁 | 亮点 |
|---|---|---|
| **Figma**（免费版够用） | 团队协作、需反复改版 | 社区有大量 TV / 商店截图模板（搜 "Android TV screenshots"），组件化、多语言版本易管理 |
| **Canva** | 非设计师、想快速出图 | 拖拽式，内置设备外框（TV mockup），导出 1920×1080 一键完成 |
| **Photoshop / Affinity Photo** | 需精细控制 | 图层/蒙版能力强，适合做品牌化排版 |
| **Sketch** | macOS 团队 | 符号库适合多页面复用 |
| **即时设计 / 稿定设计** | 国内团队 | 中文模板丰富，可直接导出商店所需尺寸 |

### 4.3 排版要点（10-foot UI 的截图尤其重要）

1. **一屏一个卖点**：8 张上限里不要塞满功能，优先「时间线 / 相册 / 全屏看图 / 屏保 / 设置」；
2. **字号要大**：电视观看距离 3m+，图内文案字号建议 ≥ 48px（1920 宽画布下）；
3. **保留焦点辉光**：让用户看出这是遥控器可操作的 TV 应用；
4. **不要拉伸截图**：设备截图按原始 16:9 等比放入边框，避免变形；
5. **文字安全区**：留出上下各 10% 边距，避免不同商店裁剪时被切。

### 4.4 建议的 8 张截图规划（对应本项目）

| # | 页面 | 卖点文案建议 |
|---|---|---|
| 1 | 媒体库首页（Hero 大图） | 在大屏上重新发现你的照片 |
| 2 | 时间线（年·月·日下钻） | 十年回忆，四级下钻一目了然 |
| 3 | 相册网格 | 与飞牛 NAS 相册自动同步 |
| 4 | 全屏看图（信息浮层 + 胶片条） | 遥控器浏览原图，纤细信息浮层 |
| 5 | 屏保 · 翻页数字 | 不看时，它是一台会呼吸的电子相框 |
| 6 | 屏保 · 日期日历 | 5 种时钟 × 3 种背景，随你切换 |
| 7 | 设置页（NAS 连接） | 局域网自动发现，填一次就够 |
| 8 | 视频播放 | 4K 原片直连串流 |

---

## 5. 真机电视截图方案

### 5.1 电视开启 ADB 调试

各家电视路径略有差异，通常在：

```
设置 → 系统 → 关于 → 版本号（连点 7 次开启开发者选项）
→ 系统 → 开发者选项 → USB 调试 / 网络调试（ADB）开启
```

### 5.2 通过局域网连接

```bash
adb connect 192.168.1.50:5555
adb devices                          # 确认状态为 device
adb exec-out screencap -p > tv.png
```

### 5.3 常见失败原因

| 现象 | 原因 | 解决 |
|---|---|---|
| `unauthorized` | 电视未确认授权弹窗 | 回到电视点「允许」，勾选「始终允许」 |
| `connection refused` | 电视未开 ADB 或端口不同 | 检查开发者选项，部分机型用 `:5555` 以外端口 |
| 截图为全黑 | DRM 保护（Netflix 等）或屏幕已休眠 | 退出受保护播放器；唤醒屏幕 |
| 截图为屏保画面 | 空闲超时进入屏保 | 先按任意键退出屏保 |

> 本项目模拟器调试时也踩过这个问题：容器重建后 adb 密钥不同步会出现 `unauthorized`，
> 需要把模拟器的 `/avd/adbkey` 同步到 `/root/.android/adbkey`（详见 README §3.4）。

---

## 6. 不推荐 / 慎用的方式

| 方式 | 问题 |
|---|---|
| **手机拍电视屏幕** | 有摩尔纹、反光、色偏，绝不能用于文档或商店 |
| **电视自带「截图」功能** | 部分机型把截图存到私有目录且压缩严重，导出困难 |
| **第三方「截屏 App」**（如 Screenshot Easy 等） | 在 TV 上无触摸操作体验极差，且会占用前台、可能被 leanback 限制；`adb screencap` 已能覆盖全部需求 |
| **`adb shell screencap > file.png`** | 换行符转换会损坏 PNG，必须 `exec-out` |
| **Android Studio 的「截图」按钮** | 仅在模拟器连接时可用，且对 TV 镜像支持不稳定；不如直接用 adb |

---

## 7. 本项目推荐工作流

```bash
# 1) 确认设备
adb devices

# 2) 启动 App 并等待加载
adb shell am start -n com.nasphoto.tv/.MainActivity
sleep 3

# 3) 用遥控器键移到目标页面（焦点辉光会进入截图）
adb shell input keyevent 19   # UP
adb shell input keyevent 22   # RIGHT
adb shell input keyevent 23   # OK

# 4) 截图（务必 exec-out）
adb exec-out screencap -p > docs/screenshots/01-library.png

# 5) 校验尺寸（应为 1920x1080）
python3 -c "import struct;d=open('docs/screenshots/01-library.png','rb').read();print(struct.unpack('>II',d[16:24]))"
```

**需要录演示视频**时改用：

```bash
scrcpy --no-window --record=demo.mp4 --max-size 1920
# 操作完成后 Ctrl+C，demo.mp4 自动可用
```

---

## 8. 环境依赖速查

| 工具 | 本项目环境 | 说明 |
|---|---|---|
| `adb` | ✅ 已装（`platform-tools`） | 核心，必须 |
| `ffmpeg` | ✅ 已装（`/usr/bin/ffmpeg`） | 批量处理、拼图、转码 |
| `scrcpy` | ❌ 未装 | 需要时可 `apt install scrcpy` |
| Python 3 | ✅ 已装 | 用于校验 PNG 尺寸等小脚本 |
| ImageMagick | ❌ 未装 | 可选，`montage` 拼图更简单 |

---

## 9. 相关文档

| 文档 | 内容 |
|---|---|
| [README.md](../README.md) | 项目总览、构建与安装、屏幕截图章节（§2） |
| [README.md §3.4](../README.md) | 无头模拟器运行参数（截图前必须保证模拟器稳定） |
| [acceptance.md](acceptance.md) | 验收清单，可作为截图页面的选取依据 |
| [DESIGN.md](../DESIGN.md) | 设计规范（含 10-foot UI 尺寸与焦点规范） |
