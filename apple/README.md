# NAS Photo TV for Apple TV

> Android TV 版的 **原生 tvOS 重写**（Swift + SwiftUI）。与 Android 端共用同一套飞牛 fnOS 私有协议，
> 在 Apple TV 上直连 NAS 看照片，并把电视变成电子相框（屏保）。

- 分支：`feature/apple-tv`（从 main 的 1.2.4 分出）
- 工程：`apple/NasPhotoTV.xcodeproj`（SwiftUI，tvOS 17.0+，Bundle ID `com.nasphoto.tv.apple`）
- 状态：✅ `xcodebuild -sdk appletvsimulator` 编译通过；✅ 已安装到 **Apple TV 4K (tvOS 27.2) 模拟器**并**连通真实飞牛 NAS**（192.168.31.184:5667）跑完整链路；真机待实测

> **真实 NAS 实测（2026-11-17）**：登录 200ms、`user_photo/stat` 照片 4550 / 视频 744、图库首屏/相册 34/人物 36（老婆 516 张、我 421 张）/文件夹 2（Photos 2943 项）/智能分类 80（「天空云彩」821 张）/人物详情/缩略图下载（251KB JPEG）全部通过。截图见 [simulator-live-connected.png](simulator-live-connected.png)。

---

## 1. 功能对照 Android 端（第二轮补齐）

| 能力 | 状态 | 说明 |
|---|---|---|
| 加密登录（WSS + RSA + AES） | ✅ | 与 Android 端同一套握手，见下文协议要点 |
| 两步验证（2FA 动态码） | ✅ | 同一 WebSocket 会话转正，不重新握手 |
| 自签证书 TOFU | ✅ | 首次信任叶子证书 SHA-256，指纹按 host 存 UserDefaults |
| 局域网自动发现 NAS | ✅ | /24 逐 IP 探测 TCP 5667（350ms 超时）+ TLS 证书主题含 `fnOS` 判定 |
| 图库（全库按年份） | ✅ | `gallery/getList` 按 offset 逐页拉全（单页 500）+ 文件名/日期/格式搜索 |
| 相册（自己的 + 分享给我的） | ✅ | `album/list` + `album_grant/list_to_me` 去重合并 |
| 文件夹 | ✅ | `photo/folder/list`（兜底 `folder/view`）+ `folder_view/getFileList` 分页 |
| 人物 | ✅ | `ai-person/list` + 按天时间线逐日翻页拉全（对齐 Android 1.2.3 修复）+ 屏保人物照片池 |
| 智能分类（场景事物） | ✅ | `ai-smart-rec/categories` + `magic-search/do` 语义搜索，旧版响应走 `gallery/getOne` 补全 |
| 收藏 | ✅ | NAS `isCollect` ∪ 本地 `FavoritesStore`（按账号分区 UserDefaults），看图页可切换 |
| 视频播放 | ✅ | AVPlayer 自建控制条：←→ 快退/快进 10 秒、播放暂停切换、3.2s 自动隐藏、自动连播/单条循环、错误文案 |
| 实况照片按需播放 | ✅ | 静音、播一次、角标 `LIVE · 实况`、提示 `实况播放中 · 播完自动回到静态` |
| 全屏看图增强 | ✅ | 2× 放大 + ±0.5 平移、全景 0.33 步长巡览（贴边翻页）、5s 幻灯片跳过视频、工具条 5 chip、胶片条、信息 HUD |
| 屏保 | ✅ | 5 种时钟样式（←→ 切换）、日期/夜间调暗/Ken Burns 开关、人物照片池、退出只按键 |
| 屏保背景音乐 + 手机上传 | ✅ | 面板打开期间启动 8899–8908 本地 HTTP 服务（token 路径鉴权、`/upload?name=`、`/list`），AVAudioPlayer 单曲循环 |
| 访问码（访问控制网关） | ✅ | `/access_code_verify` + `x-access-code`，HTTP 401 自动重试 |
| 设置页 | ✅ | 待机档位 `[30,60,120,300,600,1800]`、停留档位 `[5,10,15,30,60]`、时钟样式、播放、音乐、关于 |
| 年→月→日三级下钻 | ⬜ 未做 | tvOS 版用「按年份分组 + 搜索」替代，功能等价但层级不同 |
| 多账号切换 / 客厅展示相册 / 极光背景 / 农历 | ⬜ 未做 | Android 端有；tvOS 版后续迭代 |

## 2. 目录结构

```
apple/
├── NasPhotoTV.xcodeproj/          # 手写工程文件（PBXFileSystemSynchronizedRootGroup，目录即源码）
└── NasPhotoTV/
    ├── App/
    │   ├── NasPhotoTVApp.swift    # @main 入口
    │   └── RootView.swift         # 连接页 ⇄ 首页 状态机
    ├── Core/                      # fnOS 协议层（从 Kotlin 逐文件移植）
    │   ├── FnOsCrypto.swift       # MD5/SHA-256、AES-256-CBC(PKCS7)、RSA(PKCS#1 v1.5) + SPKI 剥壳
    │   ├── FnOsSigner.swift       # authx 签名（RAW/ENCODED 双模式，Java URLEncoder 语义）
    │   ├── FnOsApi.swift          # actor：WS 握手/加密信封/2FA/errno 分类/重登/重试
    │   ├── FnOsTransport.swift    #（在 FnOsTofu.swift 内）URLSession + TOFU 信任 + AccessToken
    │   ├── FnOsTofu.swift         # 证书指纹 TOFU 信任链
    │   ├── FnOsReqId.swift        # 28 位十六进制请求 ID（4+16+2）
    │   ├── FnOsDeviceId.swift     # 设备 did（24 位 hex，UserDefaults 持久化）
    │   └── Json.swift             # 防御式 JSON 取值（对应 org.json 习惯）
    ├── Data/
    │   ├── Models.swift           # MediaItem / Album / Person / NasFolder / 会话与挑战模型
    │   ├── FnOsRepository.swift   # 图库/相册/人物/文件夹/智能分类/收藏/搜索（分页拉全 + 去重）
    │   ├── AppSettings.swift      # 连接配置 + 屏保/播放偏好（UserDefaults / JSON）
    │   ├── FavoritesStore.swift   # 本地收藏（按账号分区）
    │   ├── LanNasDiscovery.swift  # /24 TCP 5667 探测 + TLS 证书识别飞牛 NAS
    │   └── MusicStore.swift       # 曲目目录 + 8899~8908 HTTP 上传服务 + AVAudioPlayer 循环播放
    ├── Media/
    │   └── RemoteImage.swift      # 带鉴权的远程图片（AccessToken 注入 + NSCache 256MB）
    └── UI/
        ├── Theme.swift            # 设计 token（对齐 Android Tokens.kt：深空黑 + 冰蓝）
        ├── ConnectView.swift      # 连接页（局域网发现 / 测试连接 / 两步验证动态码）
        ├── HomeView.swift         # 首页 7 个 Tab + 集合详情 + 搜索 + 设置 + 静置进屏保
        ├── PhotoViewer.swift      # 全屏看图（放大/全景/实况/幻灯片/收藏/胶片条）
        ├── VideoPlayerView.swift  # AVPlayer 视频播放
        ├── ScreensaverView.swift  # 屏保（5 种时钟样式）
        └── MusicPanel.swift       # 背景音乐上传面板 + 播放效果桥
```

## 3. 构建与运行

```bash
# 命令行编译（tvOS 27.2 模拟器 SDK；已安装 tvOS 模拟器运行时）
xcodebuild -project apple/NasPhotoTV.xcodeproj -target NasPhotoTV \
  -configuration Debug -sdk appletvsimulator \
  SYMROOT=apple/.build/SYM OBJROOT=apple/.build/OBJ \
  ARCHS=arm64 ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO build
# 产物：apple/.build/SYM/Debug-appletvsimulator/NasPhotoTV.app

# 装进 Apple TV 4K 模拟器（网络盘直接装会报 "Failed to create promise"，先拷到本地盘）
rm -rf /tmp/NasPhotoTV.app && cp -R apple/.build/SYM/Debug-appletvsimulator/NasPhotoTV.app /tmp/
xcrun simctl boot "Apple TV 4K (4th generation)"
xcrun simctl install booted /tmp/NasPhotoTV.app
xcrun simctl launch booted com.nasphoto.tv.apple
```

Xcode 图形界面：打开 `apple/NasPhotoTV.xcodeproj`，选 Apple TV 4K 模拟器或真机直接 ⌘R。
真机安装需要免费的 Apple ID 做个人签名（Signing & Capabilities → Team）。

## 4. fnOS 协议要点（与 Android 端严格对齐）

1. **加密登录**：`wss://host:port/websocket?type=main` → `util.crypto.getRSAPub` 拿公钥 →
   随机 32 字符 ASCII 作 AES-256 key（随机 16 字节 IV，CBC/PKCS7）→ 业务 JSON 用 AES 加密，
   AES key 用 RSA(PKCS#1 v1.5) 加密 → 发 `{"req":"encrypted","iv":...,"rsa":...,"aes":...}` 信封。
2. **两步验证**：密码正确但开了 2FA 时返回空 token + `isBindTwofaSecret`，同一 WebSocket 上发
   `user.2fa.loginVerify`（`stay:2`、`isTrustedDevice:true`、固定 `did`）换正式 token。
3. **HTTP 鉴权**：`/p/api/v1/*` 请求带 `AccessToken` + `authx`
   （`nonce=<6位>&timestamp=<毫秒>&sign=md5(SALT + path + nonce + timestamp + md5(payload) + SECRET)`）；
   业务码 5000 自动在 RAW/ENCODED 签名模式间切换重试，401/5001 自动重登一次。
4. **自签证书**：TOFU —— 首连信任并按 host 记录叶子证书指纹，指纹变化时先清旧指纹并拒绝连接
   （下次连接重新确认），防静默降级。
5. **图片鉴权**：缩略图/原图 URL 只需 `AccessToken` 头（+Cookie），无需 authx 签名，与 Android 端 Coil 行为一致。

### 真实 NAS 实测中定位并修复的协议细节

| 现象 | 根因 | 修复 |
|---|---|---|
| 刚发消息就报「连接已断开」 | `task.resume()` 后 TLS+WS 升级是异步的，`wsOpen` 还没置位 | `wsRequest` 先等 `didOpen`（12s 超时）再发 |
| 发了握手但 NAS 永不回 | 飞牛只处理 **text frame**（opcode 0x1），`.data()` 发的 binary frame 被静默忽略 | 改用 `task.send(.string(...))` |
| 登录响应 succ 却判失败 | 服务端推的 `{"sysNotify":"tokenExpired"}` 被当成响应消费；响应未按 reqid 匹配 | 丢弃 sysNotify，`wsRequest` 按 `reqid` 精确匹配 |
| 每次 API 都重新登录（8 次握手） | `ensureSession` 没缓存返回的 session | 缓存 session，重登前关闭旧 WS |
| 智能分类详情返回空 | magic-search 的 id 被当字符串写入，`int64` 读不到 | `Json.int/int64` 兼容数字字符串 |

## 5. 与 Android 版的已知差异

- **屏保天气为空**：Android 端仓库里也没有 `MockWeatherProvider` 实现（只有文档与注释），两端都不显示天气。
- **年→月→日三级下钻**：Android 是三层下钻；tvOS 用「按年份分组 + 搜索」替代，信息等价但层级更浅。
- **多账号切换 / 客厅展示相册 / 极光背景 / 农历文案**：Android 有，tvOS 未做。
- **遥控器按键映射差异**：tvOS 的 SwiftUI 只暴露方向键 / 播放暂停 / 返回三个命令，
  「确认」通过聚焦按钮触发；因此看图页把放大、收藏、信息、播放放到可聚焦工具条，
  视频/实况同时支持「播放暂停键」快捷触发。
- 设计画布按 1920×1080 逻辑尺寸（tvOS 1x），颜色/字号/焦点缩放与 Android `Tokens.kt` 一致。

## 6. 后续路线

1. **多账号与客厅展示相册**：账号分区缓存 + 图片缓存按账号清空（隐私）。
2. **年→月→日下钻**：对齐 Android 的三级时间线。
3. **性能**：看图页预加载前后各一张；图库网格分页加载。
4. **真机验证**：Apple TV 4K 上实测 4K / HEVC 视频、自签证书、局域网发现。
5. **发布**：个人开发者账号签名，TestFlight 分发。
