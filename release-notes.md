NAS Photo TV 原生 Apple TV 预览版 1.2.8（build 14），最低 tvOS 17。

- 图库先读取 fnOS 时间线，再按年核对分页数量，异常时按天补齐。支持年 → 月 → 日浏览。
- 文件夹使用独立接口，进入时读取该目录的媒体和直接子目录。
- 支持 HTTP(S) 反代地址及路径前缀、三种竖屏视频画面、屏保展示相册、极光背景和农历。
- 已用真实 NAS 验证 11 个年份共 5,294 项，以及 156 个目录，最深 8 层。

附件 `NasPhotoTV-1.2.8-tvOS-simulator.zip` 可在 Xcode 的 Apple TV 模拟器安装。`NasPhotoTV-1.2.8-tvOS-unsigned-xcarchive.zip` 是未签名的真机 Release 归档，目前无法直接安装到 Apple TV。真机 IPA 和 TestFlight 待配置 Apple Developer 签名后提供。

当前为预览版。多账号切换、真机 4K/HEVC 播放验证及 TestFlight 分发尚未完成。
