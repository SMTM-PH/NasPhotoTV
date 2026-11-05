# 静态检查记录

2026-11-04；执行 `gradlew.bat --no-daemon --no-parallel :app:lintDebug`。

检查已执行完成，结果未通过：16 errors、37 warnings；另有 10 条 Information。首次离线检查缺少 `collection-jvm:1.4.0` 缓存，联网重试后已完成分析。

以下 16 项 Error 涉及的 6 个文件与 `b1f8df2` 全文一致（已用 Git 逐文件比较）。本次新建/修改的界面和 debug 文件未报告 Error；没有另跑基线 lint，因此这里只确认错误所在源码未改动，不声称执行过基线检查。

| 检查项 | 文件与行 |
| --- | --- |
| NewApi | `app/src/main/res/values/themes.xml:7` |
| RestrictedApi | `app/src/main/java/com/nasphoto/tv/MainActivity.kt:44` |
| RestrictedApi | `app/src/main/java/com/nasphoto/tv/MainActivity.kt:48` |
| RestrictedApi | `app/src/main/java/com/nasphoto/tv/MainActivity.kt:48` |
| UnsafeOptInUsageError | `app/src/main/java/com/nasphoto/tv/data/fn/FnOsTransport.kt:61` |
| UnsafeOptInUsageError | `app/src/main/java/com/nasphoto/tv/ui/common/LivePhotoVideo.kt:61` |
| UnsafeOptInUsageError | `app/src/main/java/com/nasphoto/tv/ui/common/LivePhotoVideo.kt:94` |
| UnsafeOptInUsageError | `app/src/main/java/com/nasphoto/tv/ui/common/LivePhotoVideo.kt:95` |
| UnsafeOptInUsageError | `app/src/main/java/com/nasphoto/tv/ui/common/LivePhotoVideo.kt:96` |
| UnsafeOptInUsageError | `app/src/main/java/com/nasphoto/tv/ui/common/LivePhotoVideo.kt:98` |
| UnsafeOptInUsageError | `app/src/main/java/com/nasphoto/tv/ui/video/VideoPlayerScreen.kt:102` |
| UnsafeOptInUsageError | `app/src/main/java/com/nasphoto/tv/ui/video/VideoPlayerScreen.kt:246` |
| UnsafeOptInUsageError | `app/src/main/java/com/nasphoto/tv/ui/video/VideoPlayerScreen.kt:247` |
| UnsafeOptInUsageError | `app/src/main/java/com/nasphoto/tv/ui/video/VideoPlayerScreen.kt:247` |
| UnsafeOptInUsageError | `app/src/main/java/com/nasphoto/tv/ui/video/VideoPlayerScreen.kt:247` |
| UnusedContentLambdaTargetStateParameter | `app/src/main/java/com/nasphoto/tv/ui/screensaver/ScreensaverClock.kt:175` |

原始完整报告：`app/build/reports/lint-results-debug.html`、`app/build/reports/lint-results-debug.xml`、`app/build/reports/lint-results-debug.txt`。

这些静态检查问题尚未在本次界面设计任务中修复。Debug APK 构建结果与静态检查结果分别记录。
