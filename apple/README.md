# NAS Photo TV for Apple TV

> Android TV 版的 **原生 tvOS 重写**（Swift + SwiftUI）。与 Android 端共用同一套飞牛 fnOS 私有协议，
> 在 Apple TV 上直连 NAS 看照片，并把电视变成电子相框（屏保）。

- 分支：`feature/apple-tv`（首期核心闭环，从 main 的 1.2.4 分出）
- 工程：`apple/NasPhotoTV.xcodeproj`（SwiftUI，tvOS 17.0+，Bundle ID `com.nasphoto.tv.apple`）
- 状态：✅ 已通过 `xcodebuild -sdk appletvsimulator` 编译验证；真机（Apple TV 4K）待实测

---

## 1. 首期范围（核心闭环）

| 能力 | 状态 | 说明 |
|---|---|---|
| 加密登录（WSS + RSA + AES） | ✅ | 与 Android 端同一套握手，见下文协议要点 |
| 两步验证（2FA 动态码） | ✅ | 同一 WebSocket 会话转正，不重新握手 |
| 自签证书 TOFU | ✅ | 首次信任叶子证书 SHA-256，指纹按 host 存 UserDefaults |
| 图库（全库按年份） | ✅ | `gallery/getList` 按 offset 逐页拉全（单页 500） |
| 相册（自己的 + 分享给我的） | ✅ | `album/list` + `album_grant/list_to_me` 去重合并 |
| 相册详情（按年份分组） | ✅ | `album/photos` 分页 + 去重 |
| 全屏看图 | ✅ | 方向键/遥控滑动翻页、确认键信息条、实况/视频角标 |
| 屏保（StandBy 风格） | ✅ | 大号时钟 + 照片氛围 + 防烧屏交替缩放，任意键只退出 |
| 访问码（访问控制网关） | ✅ | `/access_code_verify` + `x-access-code`，HTTP 401 自动重试 |
| 视频播放 | ⬜ 下一轮 | AVPlayer + HLS/直链探测 |
| 人物 / 智能分类 / 文件夹 | ⬜ 下一轮 | 沿用 Android 端已验证的接口组合 |
| 屏保背景音乐 / 图片上传 | ⬜ 远期 | |

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
    │   ├── Models.swift           # MediaItem / Album / FnOsStats / 会话与挑战模型
    │   ├── FnOsRepository.swift   # 图库/相册/屏保照片池/统计（分页拉全 + 去重）
    │   └── AppSettings.swift      # 连接配置 + 屏保偏好（UserDefaults / JSON）
    ├── Media/
    │   └── RemoteImage.swift      # 带鉴权的远程图片（AccessToken 注入 + NSCache 256MB）
    └── UI/
        ├── Theme.swift            # 设计 token（对齐 Android Tokens.kt：深空黑 + 冰蓝）
        ├── ConnectView.swift      # 连接页（测试连接 / 两步验证动态码）
        ├── HomeView.swift         # 首页：图库 / 相册 / 设置 + 焦点规范 + 静置进屏保
        ├── PhotoViewer.swift      # 全屏看图
        └── ScreensaverView.swift  # 屏保
```

## 3. 构建与运行

```bash
# 命令行编译（tvOS 模拟器 SDK；本仓库环境未装 tvOS 模拟器运行时，仅编译验证）
xcodebuild -project apple/NasPhotoTV.xcodeproj -target NasPhotoTV \
  -configuration Debug -sdk appletvsimulator \
  SYMROOT=apple/.build/SYM OBJROOT=apple/.build/OBJ \
  ARCHS=arm64 ONLY_ACTIVE_ARCH=YES CODE_SIGNING_ALLOWED=NO build
# 产物：apple/.build/SYM/Debug-appletvsimulator/NasPhotoTV.app
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

## 5. 与 Android 版的已知差异

- 屏保**天气为空**（Android 端也是本地假数据，tvOS 版暂未做天气占位）。
- 不支持长按/双击/手势：遥控器方向键翻页，确认键弹信息条（同 Android 规范）。
- 设计画布按 1920×1080 逻辑尺寸（tvOS 1x），颜色/字号/焦点缩放与 Android `Tokens.kt` 一致。
- 首版不含视频播放、人物、智能分类、文件夹、背景音乐上传（见上方范围表）。

## 6. 后续路线

1. **视频播放**：`AVPlayer` + `videoUrl`/`originalUrl` 直链探测，全屏播放器与看图共用信息条。
2. **人物 / 智能分类 / 文件夹**：接口组合已在 Android 端验证，照搬即可。
3. **性能**：看图页预加载前后各一张；图库网格分页加载。
4. **发布**：个人开发者账号签名，TestFlight 分发。
