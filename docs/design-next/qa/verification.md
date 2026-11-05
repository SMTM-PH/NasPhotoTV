# 新版界面验证记录

日期：2026-11-04，北京时间。

## 验证范围

使用独立 AVD `NasPhotoTV_Design_API35`，设备序列号 `emulator-5580`。系统为 Android 15 / API 35 / Google APIs x86_64 **通用 Android 镜像**，不是 Android TV 镜像，也不是真实电视。预览入口为仅 Debug 存在的 `com.nasphoto.tv/.DesignPreviewActivity`，使用六张离线示例摄影图片、四个示例相册和三个首字占位人物，不访问 NAS。

预览使用独立 `:designPreview` 进程和偏好设置命名空间。正常 NAS 账号、密码和收藏配置未用于本次检查。

## 构建与静态检查

- Lead 已完成 `assembleDebug` 和 `processReleaseMainManifest`，结果 `BUILD SUCCESSFUL`。
- 本次最终 Debug APK SHA256：`c8ee3726c3a185179b7f301096fd667c1c3396824f3830b48c2c449f1bdf7c09`。
- Release 合并清单中没有 `DesignPreviewActivity`；示例图片位于 Debug source set。
- `lintDebug` 的完整结果与基线核对见 [lint-summary.md](lint-summary.md)。本轮不能宣称完整 lint 全通过。

## 1920×1080，160dpi（1920×1080dp）

应用内操作均使用方向键、确认和返回。首次运行时仅点击过系统首次全屏说明的 `Got it` 按钮；该系统说明已关闭，最终截图无此遮挡。

| 检查 | 结果 | 证据 |
|---|---|---|
| 首页实际绘制、照片加载、完整导航和焦点环 | 通过，已逐图查看 | [01-home-1920dp.png](01-home-1920dp.png) |
| 首页 Up → 右移至相册 → 确认切换 | 通过，四张相册可见 | [02-albums-1920dp.png](02-albums-1920dp.png) |
| 相册第 2 张「山野漫游」→ 详情 → Back | 通过，回到同一张相册，XML 节点 focused=true | [03-album-return.png](03-album-return.png)、[album-return.xml](album-return.xml) |
| 人物 B → 详情 → Back | 通过，回到人物 B，XML 节点 focused=true | [04-person-return.png](04-person-return.png)、[person-return.xml](person-return.xml) |
| 从顶栏设置进入设置页，Back 关闭设置 | 通过，设置文字与双栏正常显示 | [05-settings-1920dp.png](05-settings-1920dp.png) |

冷启动时通用模拟器完成首屏约需 14–20 秒；曾发生 `am start -W` 等待超时后界面才正常显示，因此最终截图以实际绘制并人工查看为准。启动 splash 与系统全屏提示图均未作为最终截图。

软件图形后端运行中模拟器曾两次整进程退出（`swiftshader_indirect` 与 `swangle_indirect` 均出现）；此前 App crash buffer 未出现应用崩溃。Windows 事件记录确认 17:26:16 与 17:31:19 的 `qemu-system-x86_64-headless.exe` 宿主进程发生 `0xc0000005` 访问冲突，模块 unknown；主机当时仍有约 38GB 可用内存。可确认宿主模拟器崩溃，根因未知；这不等同于 App Java/Kotlin 崩溃，也不能排除 App 焦点/滚动触发软件渲染缺陷。第二次已禁用 ModemSimulator，仍未消除退出。切换 `-gpu host -feature -ModemSimulator` 后，指定同链复测通过；它支持交互结果，但不证明软件图形后端退出的根因已修复。宿主事件详见 [emulator-host-failure.md](emulator-host-failure.md)。

## 1920×1080，320dpi（960×540dp）

| 检查 | 结果 | 证据 |
|---|---|---|
| 紧凑首页、六个 Tab、三个图标操作、Hero 标题/日期/按钮可读 | 通过，已逐图查看 | [07-home-960dp.png](07-home-960dp.png)、[10-fresh-home-960dp.png](10-fresh-home-960dp.png) |
| 紧凑设置、聚焦地址行、右侧连接状态 | 通过，已逐图查看 | [08-settings-960dp.png](08-settings-960dp.png)、[settings-focus-960.xml](settings-focus-960.xml) |
| 首页相册行可经 Down 滚动进入、卡片可见 | 通过，已逐图查看 | [09-home-albums-960dp.png](09-home-albums-960dp.png) |
| 最近第 6 项 → Down 相册第 4 项 → Up 最近第 4 项 → 看图 → Back → Up Hero | Host GPU 复测通过，焦点与双轴滚动正确 | 见下方逐步记录 |

Host GPU 在同一专用 AVD、同一 APK、960×540dp 下的最终复测：

1. 使用清空当前任务的启动参数进入全新 Home，焦点在 Hero：[11-host-home-960dp.png](11-host-home-960dp.png)。
2. Down 进入最近第一张，Right 五次（每次间隔约 350ms），焦点为第六张「湖畔时光」，[host-recent-last.xml](host-recent-last.xml) 中 focused=true。
3. Down 后相册横轴滚至第四项「一起的时光」，聚焦节点 bounds 为 `[909,512][1707,974]`，白框完整可见：[12-host-cross-down-960dp.png](12-host-cross-down-960dp.png)、[host-cross-down.xml](host-cross-down.xml)。
4. Up 回最近第四项「湖山来信」，[host-cross-up.xml](host-cross-up.xml) 中 focused=true，bounds 为 `[617,476][1100,791]`。
5. Confirm 打开照片，再 Back，焦点仍为「湖山来信」，bounds 保持 `[617,476][1100,791]`：[13-host-viewer-return-960dp.png](13-host-viewer-return-960dp.png)、[host-viewer-return.xml](host-viewer-return.xml)。
6. Up 后首页竖轴滚回 Hero，Hero 白框重新显示：[14-host-hero-return-960dp.png](14-host-hero-return-960dp.png)、[host-hero-return.xml](host-hero-return.xml)。

上述最终截图逐张用图像查看工具检查。Host GPU 复测结束时 `adb logcat -b crash -d` 返回空内容，专用 AVD 在本次链内未退出。测试结束仅关闭 `emulator-5580`，保留其 AVD 配置；原始启动日志未纳入交付，以免附带无关机器标识。

## 证据边界

这些结果验证的是新界面的 Compose 实际渲染、示例数据浏览和已列出的遥控器交互。它们不代表真实电视、真实 NAS 登录、相册网络分页、视频解码、长期屏保或生产发布验收。

摄影来源和许可见 [image-sources.md](image-sources.md)。
