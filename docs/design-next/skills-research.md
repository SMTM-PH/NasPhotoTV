# TV App UX/UI Skills 调研

调研日期：2026-11-04。面向 NasPhotoTV 的 Android TV 原生界面升级。

本轮已搜索技能目录并阅读候选技能源码和 Android Developers 官方设计资料；**未安装第三方技能**。以下区分第三方设计建议、官方平台规则和本项目的实施建议。

## 实际搜索

通过已安装的 `find-skills` 工作流执行：

```text
npx --yes skills find "android tv"
npx --yes skills find "tv design"
npx --yes skills find "dpad"
```

- `android tv`：命中 `android-tv-design`、`leanback-to-compose-tv-migration`、`android-tv-expert`、`android-tv-skill` 等候选。
- `tv design`：再次命中 `android-tv-design`，另有 `tvos-design-guidelines` 等 Apple TV 方向结果。
- `dpad`：未发现比上述候选更匹配的设计技能；该关键词出现了不相关结果，因此未据此推荐技能。

候选的技能目录链接来自命令实际输出。部分 `skills.sh` 页面在本次网页读取中不可访问，因此优先检查对应 GitHub 原始 `SKILL.md`，不把目录搜索命中视为内容质量验证。

## 候选与取舍

| 技能 | 来源属性 | 本次核验的用途 | 适用判断 |
| --- | --- | --- | --- |
| `developerscoffee/10-foot-design@android-tv-design` | 第三方社区技能 | Android TV / Google TV / Fire TV 的遥控器导航、焦点状态、布局、组件、颜色与排版 | **最适合本次 UX/UI 设计。** 规则聚焦电视使用场景，仍以官方文档校核平台行为和数值。 |
| `android/skills@leanback-to-compose-tv-migration` | Android 官方技能；源码标注作者 Google LLC | View / Leanback 向 Compose for TV 迁移，包含 TV Material、焦点恢复和沉浸式布局建议 | 本项目已有 Compose，参考其电视交互规则；不把迁移旧界面的完整流程当成本次必做事项。 |
| `anubhavg-icpl/vibe@android-tv-expert` | 第三方社区技能 | Compose for TV、焦点管理、遥控器交互、推荐频道、媒体播放 | 可补充工程实践；其中写死的依赖版本不能直接当作当前项目的升级要求。 |
| `markmcd/android-tv-skills@android-tv-skill` | 第三方社区技能 | 通过 ADB 发现、控制和自动化操作 Android TV 设备 | **不是 UI 设计技能。** 可供未来设备操作参考，不作为此次视觉设计依据。 |
| `ehmo/platform-design-skills@tvos-design-guidelines` | 第三方技能目录候选 | 搜索命中 Apple tvOS 设计方向 | 本次仅核验到搜索结果，未阅读完整技能源码；不作为已审查的 Android 设计依据。 |

### 可追溯链接

- 10-Foot Design：[技能源码](https://github.com/DevelopersCoffee/10-foot-design/blob/main/skills/android-tv-design/SKILL.md)、[基础规则](https://github.com/DevelopersCoffee/10-foot-design/blob/main/skills/android-tv-design/reference/foundations.md)、[技能目录](https://skills.sh/developerscoffee/10-foot-design/android-tv-design)。
- Android 官方迁移技能：[技能源码](https://github.com/android/skills/blob/main/tv/leanback-to-compose-tv-migration/SKILL.md)、[技能目录](https://skills.sh/android/skills/leanback-to-compose-tv-migration)。
- Android TV Expert：[技能源码](https://github.com/anubhavg-icpl/vibe/blob/master/skills/android-tv-expert/SKILL.md)、[技能目录](https://skills.sh/anubhavg-icpl/vibe/android-tv-expert)。
- Android TV 设备控制：[项目说明](https://github.com/markmcd/android-tv-skills)、[技能目录](https://skills.sh/markmcd/android-tv-skills/android-tv-skill)。
- tvOS 候选：[技能目录](https://skills.sh/ehmo/platform-design-skills/tvos-design-guidelines)。

## 官方规范与本项目建议

| 已核验的官方规范 | 在 NasPhotoTV 中的应用建议 |
| --- | --- |
| TV 布局文档以 960×540 dp、16:9 为设计参考；重要控件建议留约 5% 安全边距，即左右约 48 dp、上下约 27 dp，背景可以铺满。 | 顶部导航、照片标题和底部遥控器提示放入安全区域；照片背景和装饰允许延伸到屏幕边缘。 |
| 内容浏览可用纵向内容行和横向项目；任务型界面适合两栏，避免过多面板造成认知负担。 | 图库采用内容为主的首页及清晰内容行；连接和设置保持简洁层级。 |
| 用户必须能用四向遥控器到达所有可聚焦元素；导航方向应直接、可预测。 | 检查导航、照片卡片、筛选和播放入口之间的路径；不依赖触摸、悬停或难以发现的手势。 |
| 焦点系统区分默认、聚焦和按下状态；同一时刻只有一个焦点，可组合缩放、描边、光晕和颜色反馈。官方列出 1.025、1.05、1.1 等缩放示例。 | 采用统一的聚焦描边与克制缩放。选择具体比例时检查照片裁切、相邻卡片间距和边缘遮挡；不要只用微弱颜色差异表示焦点。 |
| 返回键逐级回到之前的目的地，持续返回最终到 Launcher；不应用退出确认阻拦用户，也无需屏幕返回按钮。 | 复核图库、详情、播放、设置的返回链；破坏性确认界面仍提供明确的取消操作。 |
| 大字、足够对比度和清晰无衬线字更适合远距离观看；装饰字体和细窄笔画可能降低可读性。 | 使用简短中文导航和标题，减少密集正文；具体说明留在用户主动打开的设置或帮助界面。 |
| TV 布局构建建议使用浅色文字配深色背景，并以 dp / sp 表达尺寸；控件应能在约三米外辨认。 | 深色界面衬托照片，保持标题、状态和操作提示对比度；按实际电视密度检查布局。 |
| Android 官方迁移技能推荐 TV Material 组件及内容行焦点恢复。 | 复用项目已有 Compose 焦点组织，返回列表时恢复原项目；涉及 TV Material 组件升级时单独检查兼容性和行为。 |

官方来源：

- [TV UI Design 总览](https://developer.android.com/design/ui/tv)
- [Layouts](https://developer.android.com/design/ui/tv/guides/styles/layouts)
- [Focus system](https://developer.android.com/design/ui/tv/guides/styles/focus-system)
- [Navigation on TV](https://developer.android.com/design/ui/tv/guides/foundations/navigation-on-tv)
- [Typography](https://developer.android.com/design/ui/tv/guides/styles/typography)
- [Build TV layouts](https://developer.android.com/training/tv/playback/compose/layouts)
- [TV navigation 实施与验证建议](https://developer.android.com/training/tv/get-started/navigation)

## 为什么采用 10-Foot Design，而不迁移全部 Compose 组件

本次任务是升级电视端体验，项目本身已经有 Kotlin / Compose 界面。10-Foot Design 的价值在于提供电视场景下的设计检查项，与现有实现方式可以结合：远距离阅读、单一明显焦点、四向路径、安全边距和低密度布局。

Android 官方迁移技能对启动旧 View / Leanback 迁移很有用，其中关于 TV Material 的要求属于该迁移工作流。阅读该技能不意味着现有每个自定义 Compose 组件都必须在本次替换。此次应先保证现有业务入口和数据流可用，再实现统一视觉与交互；具体组件如有无法满足遥控器焦点行为的问题，再局部替换并验证。

该取舍是**本项目的实施判断**，不是 Android 官方要求。第三方技能中“深色界面更省电”等笼统描述也不作为本项目结论：显示功耗取决于面板类型和具体内容，本次采用深色主要是为了照片展示和视觉舒适性。

## 安装与验证边界

本次仅查阅、评估与应用设计建议，没有全局或项目级安装上述候选技能。后续如需正式安装首选技能，可执行：

```text
npx skills add developerscoffee/10-foot-design@android-tv-design -g -y
```

技能搜索和文档阅读不等于界面验收。静态预览、debug 示例、构建通过、模拟器遥控器操作和真实电视表现应分别记录；本研究文档不对这些后续检查作通过声明。
