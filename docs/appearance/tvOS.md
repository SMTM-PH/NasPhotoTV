# tvOS 端外观模式接入说明（本次未自动落盘）

> **为什么没有直接改源码**：本次会话的运行账号（`dsh`）对 `apple/` 只有读权限
> （`drwx---r-x`，owner=`panhong`），`write` / `touch` 均被 `Permission denied` 拒绝；
> 同时环境里没有 Xcode / Swift 工具链，无法编译验证。
> 因此下面给出**可直接粘贴**的完整实现，取值与 Android `Appearance.kt`、
> [tokens.json](tokens.json) 完全一致。Android 端已实现并通过编译验证。

---

## 1. 新增 `apple/NasPhotoTV/UI/Appearance.swift`

```swift
import SwiftUI

/// 外观模式偏好 —— 与 Android `TvAppearance` 对齐。
/// 默认 `.dark`（影院黑），与线上现值一致，升级后不会突然变白。
enum TvAppearance: String, CaseIterable, Codable {
    case system
    case light
    case dark

    static let `default`: TvAppearance = .dark
    static let storageKey = "appearance"

    /// 值胶囊用的短标签（10-foot 单行不省略）
    var label: String {
        switch self {
        case .system: return "跟随系统"
        case .light: return "浅色"
        case .dark: return "深色"
        }
    }

    var fullLabel: String {
        switch self {
        case .system: return "跟随系统"
        case .light: return "浅色 · 画廊白"
        case .dark: return "深色 · 影院黑"
        }
    }

    /// nil = 跟随系统（不施加 preferredColorScheme）
    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }

    var next: TvAppearance {
        let all = TvAppearance.allCases
        let index = all.firstIndex(of: self) ?? 0
        return all[(index + 1) % all.count]
    }

    static func fromStored(_ raw: String?) -> TvAppearance {
        raw.flatMap { TvAppearance(rawValue: $0) } ?? .default
    }
}
```

## 2. 替换 `apple/NasPhotoTV/UI/Theme.swift`

用**动态 `UIColor` provider**：`SwiftUI.Color(UIColor { traits in ... })` 会随
`preferredColorScheme` / 系统主题解析，因此所有已有的 `Tv.Color.xxx` 静态调用点
（29 处 `textSecondary`、21 处 `textPrimary` …）**一个都不用改**。
`cinemaPanel` / `scrim` / `timeWhite` 是压在照片上的常量，两套模式取同一个深色值；
`stage` 是普通语义面、随模式翻转，看图 / 播放 / 屏保需要显式使用深色舞台（见文末说明）。

```swift
import SwiftUI
import UIKit

/// NAS Photo TV 设计规范 —— 与 Android `Tokens.kt` / `Appearance.kt` 对齐。
/// 两套外观：深色「影院黑」、浅色「画廊白」；看图 / 播放 / 屏保恒为深色。
enum Tv {
    enum Color {
        static let bg             = tvColor(dark: 0x06131E, light: 0xE9EEF4)
        static let bgElevated     = tvColor(dark: 0x12141B, light: 0xF3F6FA)
        static let surface        = tvColor(dark: 0x1A1D26, light: 0xFFFFFF)
        static let surfaceHigh    = tvColor(dark: 0x252935, light: 0xDDE5EE)
        static let surfaceGlass   = tvColor(dark: 0xFFFFFF, light: 0xFFFFFF, darkAlpha: 0.40, lightAlpha: 0.70)
        static let surfaceSoft    = tvColor(dark: 0x122A3A, light: 0xD7E6F1)
        static let stage          = tvColor(dark: 0x0B202F, light: 0xDDE7F0)
        static let textPrimary    = tvColor(dark: 0xF5F7FA, light: 0x0E1A28)
        static let textSecondary  = tvColor(dark: 0xA9B0BE, light: 0x44566B)
        static let textTertiary   = tvColor(dark: 0x8C97A8, light: 0x546579)
        static let textMuted      = tvColor(dark: 0x8D9AA8, light: 0x516376)
        static let warm           = tvColor(dark: 0xCDBA98, light: 0x755A34)
        static let inkOnAccent    = tvColor(dark: 0x002232, light: 0xFFFFFF)
        static let accent         = tvColor(dark: 0x31B6ED, light: 0x096494)
        static let accentDim      = tvColor(dark: 0x31B6ED, light: 0x096494, darkAlpha: 0.20, lightAlpha: 0.12)
        static let accentAlt      = tvColor(dark: 0x5B8CFF, light: 0x3557B8)
        static let accentAltDim   = tvColor(dark: 0x5B8CFF, light: 0x3557B8, darkAlpha: 0.20, lightAlpha: 0.12)
        static let danger         = tvColor(dark: 0xFF6B6B, light: 0xAF2E26)
        static let warn           = tvColor(dark: 0xFFC857, light: 0x8A5700)
        static let divider        = tvColor(dark: 0xFFFFFF, light: 0x12263A, darkAlpha: 0.12, lightAlpha: 0.12)
        static let focusRing      = tvColor(dark: 0xFFFFFF, light: 0x0B3A5C)
        static let focusGlow      = tvColor(dark: 0x31B6ED, light: 0x0B3A5C, darkAlpha: 0.50, lightAlpha: 0.20)

        // ↓ 压在照片上的浮层 / 遮罩：两套模式字面相同（固定暗场）
        static let cinemaPanel    = tvColor(dark: 0x081D2B, light: 0x081D2B, darkAlpha: 0.80, lightAlpha: 0.80)
        static let scrimTop       = tvColor(dark: 0x000000, light: 0x000000, darkAlpha: 0.70, lightAlpha: 0.70)
        static let scrimBottom    = tvColor(dark: 0x000000, light: 0x000000, darkAlpha: 0.85, lightAlpha: 0.85)
        static let timeWhite      = tvColor(dark: 0xF5F7FA, light: 0xF5F7FA, darkAlpha: 0.90, lightAlpha: 0.90)
        // ↑ 固定暗场
    }

    enum Dimen {
        static let screenPadding: CGFloat = 64
        static let spaceXs: CGFloat = 8
        static let spaceS: CGFloat = 12
        static let spaceM: CGFloat = 20
        static let spaceL: CGFloat = 32
        static let spaceXl: CGFloat = 48
        static let radiusM: CGFloat = 16
        static let radiusL: CGFloat = 24
        static let focusScale: CGFloat = 1.05
        static let focusRingWidth: CGFloat = 3
    }

    enum Font {
        static let display: CGFloat = 56
        static let clockHuge: CGFloat = 200
        static let section: CGFloat = 30
        static let cardTitle: CGFloat = 22
        static let body: CGFloat = 20
        static let caption: CGFloat = 16
        static let tiny: CGFloat = 13
    }
}

/// 用 UIColor 的 dynamic provider 生成随 colorScheme 切换的语义色。
/// `userInterfaceStyle != .light` 表示深色或未指定 → 回落影院黑（客厅默认暗环境）。
private func tvColor(
    dark darkHex: UInt32,
    light lightHex: UInt32,
    darkAlpha: Double = 1,
    lightAlpha: Double = 1
) -> SwiftUI.Color {
    SwiftUI.Color(UIColor { traits in
        let isDark = traits.userInterfaceStyle != .light
        return UIColor(
            hex: isDark ? darkHex : lightHex,
            alpha: CGFloat(isDark ? darkAlpha : lightAlpha)
        )
    })
}

private extension UIColor {
    convenience init(hex: UInt32, alpha: CGFloat = 1) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: alpha
        )
    }
}

extension SwiftUI.Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha
        )
    }
}

/// 聚焦态卡片：放大 + 描边 + 辉光 / 投影。浅色下描边自动变墨蓝。
struct TvFocusStyle: ViewModifier {
    let isFocused: Bool

    func body(content: Content) -> some View {
        content
            .scaleEffect(isFocused ? Tv.Dimen.focusScale : 1.0)
            .overlay(
                RoundedRectangle(cornerRadius: Tv.Dimen.radiusM)
                    .strokeBorder(
                        isFocused ? Tv.Color.focusRing : .clear,
                        lineWidth: Tv.Dimen.focusRingWidth
                    )
            )
            .shadow(color: isFocused ? Tv.Color.focusGlow : .clear,
                    radius: isFocused ? 18 : 0)
    }
}

extension View {
    func tvFocus(_ isFocused: Bool) -> some View {
        modifier(TvFocusStyle(isFocused: isFocused))
    }
}
```

> ⚠️ 若 tvOS 上 `.preferredColorScheme` 未驱动 `UIColor` 动态色解析，
> 备选方案是把 `Tv.Color` 改成 `static func color(_ scheme: ColorScheme) -> Color`
> 并在各 View 用 `@Environment(\.colorScheme)` 传入 —— 但那需要改动所有调用点。
> 建议先在真机 / 模拟器上验证动态色解析（见 §6 验证清单）。

## 3. `Data/AppSettings.swift` 增加偏好

在 `AppSettings` 类中加入：

```swift
    @Published var appearance: TvAppearance {
        didSet { defaults.set(appearance.rawValue, forKey: TvAppearance.storageKey) }
    }
```

在 `private init()` 里（`autoReconnect = ...` 之后）加入：

```swift
        appearance = TvAppearance.fromStored(defaults.string(forKey: TvAppearance.storageKey))
```

在类末尾加入：

```swift
    /// 遥控器循环：跟随系统 → 浅色 → 深色 → 跟随系统
    func cycleAppearance() {
        appearance = appearance.next
    }
```

## 4. `App/NasPhotoTVApp.swift` 施加主题

```swift
import SwiftUI

@main
struct NasPhotoTVApp: App {
    @ObservedObject private var settings = AppSettings.shared

    var body: some Scene {
        WindowGroup {
            RootView()
                .preferredColorScheme(settings.appearance.colorScheme)
        }
    }
}
```

## 5. `UI/HomeView.swift` 的 `SettingsTab` 增加外观分区

在 `VStack` 的最前面（`section("屏保与待机")` 之前）插入：

```swift
                section("外观") {
                    row("外观模式", value: settings.appearance.label) { settings.cycleAppearance() }
                    row("固定暗场", value: "看图 / 播放 / 屏保恒为深色", action: {})
                }
```

## 6. 验证清单

- [ ] tvOS 17+ 模拟器：在「设置 → 外观」循环三种模式，浏览界面底色在 `#06131E` / `#E9EEF4` 间切换。
- [ ] 焦点环：深色为白描边 + 冰蓝辉光；浅色为墨蓝 `#0B3A5C` 描边 + 淡墨投影，三米外清晰可见。
- [ ] 主色按钮上的文字：深色深墨字 `#002232` / 浅色白字，均清晰。
- [ ] 进入 `PhotoViewer` / `ScreensaverView`：无论外观如何都保持深色舞台，无白闪。
- [ ] 重启 App 后外观偏好保留；「跟随系统」随 tvOS 系统主题变化。
- [ ] 对照 [README.md](README.md) §7 的对比度表，抽测主要文字。

---

## 与 Android 端的差异

| 项 | Android | tvOS |
|---|---|---|
| 两套调色板载体 | `TvPalette` data class + `CompositionLocal` | `UIColor` dynamic provider（无 CompositionLocal） |
| 偏好存储 | `SharedPreferences["appearance"]` | `UserDefaults["appearance"]` |
| 「跟随系统」 | `Configuration.uiMode & UI_MODE_NIGHT_MASK` | `preferredColorScheme(nil)` + 系统 `userInterfaceStyle` |
| 固定暗场 | `ProvideTvStage { }` 子树强制深色板 | 无子树覆盖机制；需给看图 / 播放 / 屏保的根视图加 `.preferredColorScheme(.dark)`，或把这些页面的通用 token 换成深色常量 |

> tvOS 的固定暗场没有 Android 的「子树覆盖」机制，落地时请：
> 1. 给 `PhotoViewer.swift` / `VideoPlayerView.swift` / `ScreensaverView.swift` 的根视图加
>    `.preferredColorScheme(.dark)`（注意这会让动态 `UIColor` 解析为深色分支）；
> 2. 或把这些页面里的 `Tv.Color.bg` / `textPrimary` / `stage` 换成写死的深色常量
>    （`#06131E` / `#F5F7FA` / `#0B202F`），否则浅色模式下舞台会变白。
