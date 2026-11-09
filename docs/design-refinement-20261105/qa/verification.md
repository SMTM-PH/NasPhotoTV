# 相册体验整改 · 验证记录

日期：2026-11-05。基于 `b1f8df2` 验证；构建时为本地工作区改动，后续代码与设计资料整理在 [`codex/tv-gallery-ui` 分支](https://github.com/SMTM-PH/NasPhotoTV/tree/codex/tv-gallery-ui)。这不是新正式版本发布记录。

## 构建与静态检查

- JDK 17.0.15，离线串行构建 `:app:assembleDebug` 成功，最终一次耗时 65 秒。
- 最终 APK 已安装到 `emulator-5580`；大小和 SHA256 见 [build-summary.json](build-summary.json)。
- `git diff --check` 通过，只有 Windows 换行提示。
- `:app:lintDebug` 未通过：16 个 Error、37 个 Warning。16 个错误均位于以下 6 个文件，已逐文件与基线提交比较，内容未改动：`themes.xml`、`MainActivity.kt`、`FnOsTransport.kt`、`LivePhotoVideo.kt`、`VideoPlayerScreen.kt`、`ScreensaverClock.kt`。本轮未隐藏或抑制这些错误。报告位于 `app/build/reports/lint-results-debug.html`。

## 模拟器操作

设备为独立的 `NasPhotoTV_Design_API35`，Android 15 / API 35 Google APIs x86_64，Host GPU。它是普通 Android 系统镜像，不能代替 Android TV 镜像或真实电视验收。重点操作画布为 1920×1080 像素、density 320，即 960×540dp。

所有内容来自 Debug 独立进程的离线示例，正常 NAS 凭据与收藏存储没有参与。遥控器流程用方向键、确认和返回验证；指针点击坐标来自 UI XML。

| 操作 | 实际结果与证据 |
|---|---|
| 默认回忆首页 | 仅三项主导航；两组往年回忆、两个授权相册，共四个集合。[首页](01-home-960dp.png) |
| 左右选择相册集合 | 选到「山海之间」，显示实际 2 张照片及日期范围。[相册集合](02-album-collection.png) |
| 确认播放 | 直接进入照片幻灯片，画面上无常驻大信息面板。[纯照片画面](03-slideshow-clean.png) |
| 轮播与暂停 | 播放从星夜照片推进到蓝色海岸，方向键暂停并进入工具。[推进后的 UI 树](06-playback-progress.xml) |
| 五项看图工具 | 收藏、播放照片、信息、放大、关闭均在 960dp 画布内完整显示。[工具条](05-viewer-controls.png) |
| 返回回忆 | 回到此前选中的「山海之间」，保持集合位置 3/4。[返回位置](07-home-return.png) |
| 无授权 / 仅授权空相册 | 都出现选择展示相册引导，无照片回忆集合，没有补入最近照片。[无授权](08-no-authorization.png)、[空相册](09-empty-album.png) |
| 逐相册选择与全部取消 | 全部取消后数量变为 0，选「山海之间」后变为 1；左键关闭面板，焦点回到入口。[清空](15-picker-cleared.png)、[选中](17-picker-selected.png)、[焦点恢复](18-picker-back.png) |
| 授权变更回首页 | 仅剩该相册和对应往年回忆，共 2 个集合。[变更后的首页](19-home-selected.png) |
| 二级导航 | 相册网格首排向上回到分类栏，可依次进入人物和收藏。[分类焦点](28-category-entry.png)、[人物](25-people-open.png)、[收藏](26-favorites-open.png) |
| 鼠标点击关闭 | 最终 APK 展开工具后，点击 UI 树定位的「关闭」，成功返回回忆首页。[点击前](29-viewer-pointer-controls.png)、[点击后](30-pointer-close-return.png) |

每个主要截图旁保留同名 XML，可核对可见文字和焦点。部分早期截图摄于本轮最后一次修复之前；最终 APK 重测了分类导航、鼠标关闭和身份范围断言，视觉布局相同。

## 展示范围检查

Debug 入口在设备上执行断言：无授权、失效相册 ID、授权空相册均返回空；授权过滤不混入其他相册；切换账号后授权为空；同一个账号槽位改变 NAS 身份后，旧缓存不出现在可授权列表。结果见 [运行日志](runtime-scope-checks.txt) 的 `identity-change` 记录。

生产外壳另用完整连接身份隔离媒体缓存与加载结果；异步结果提交前再次核对身份。此处为代码复核和离线断言，尚未用真实 NAS 验证登录切换和异步网络竞态。

## 未验收范围

- 真实电视遥控器、电视系统焦点行为、性能与长时间待机。
- 真实 NAS 登录、权限、人物接口、分页与大媒体库。
- 视频 / Live Photo 的实际解码和音视频操作。
- 竖图首页适配已实现保留比例，但本轮示例均为横图，没有竖图设备视觉证据。
- 其他设备密度、系统字体放大和 4K 输出未完成可靠验收。

普通图库仍能浏览全部媒体；「客厅展示相册」仅控制回忆首页和屏保，不是访问控制。默认关闭自动屏保，显式预览不会打开自动屏保开关。
