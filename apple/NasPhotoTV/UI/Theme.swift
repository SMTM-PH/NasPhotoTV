import SwiftUI

/// NAS Photo TV 设计规范（Design Tokens）—— 与 Android 端 `Tokens.kt` 对齐。
/// 10-foot UI：按 1920×1080 逻辑画布设计，Apple TV 上 1pt ≈ 2px（1080p）。
enum Tv {
    enum Color {
        static let bg = SwiftUI.Color(hex: 0x06131E)          // 深空黑（带冷调）
        static let bgElevated = SwiftUI.Color(hex: 0x12141B)
        static let surface = SwiftUI.Color(hex: 0x1A1D26)
        static let surfaceHigh = SwiftUI.Color(hex: 0x252935)
        static let stage = SwiftUI.Color(hex: 0x0B202F)       // 客厅影廊的照片舞台
        static let surfaceSoft = SwiftUI.Color(hex: 0x122A3A)
        static let cinemaPanel = SwiftUI.Color(hex: 0x081D2B, alpha: 0.8)
        static let textPrimary = SwiftUI.Color(hex: 0xF5F7FA)
        static let textSecondary = SwiftUI.Color(hex: 0xA9B0BE)
        static let textTertiary = SwiftUI.Color(hex: 0x6F7787)
        static let accent = SwiftUI.Color(hex: 0x31B6ED)      // 冰蓝主色
        static let accentDim = SwiftUI.Color(hex: 0x31B6ED, alpha: 0.2)
        static let accentAlt = SwiftUI.Color(hex: 0x5B8CFF)   // 星蓝
        static let danger = SwiftUI.Color(hex: 0xFF6B6B)
        static let divider = SwiftUI.Color(hex: 0xFFFFFF, alpha: 0.12)
        static let focusRing = SwiftUI.Color.white
        static let focusGlow = SwiftUI.Color(hex: 0x31B6ED, alpha: 0.5)
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

extension SwiftUI.Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: alpha,
        )
    }
}

/// 聚焦态卡片样式：放大 + 白描边 + 冰蓝辉光（对应 FocusCard / TvDimen 焦点规范）。
struct TvFocusStyle: ViewModifier {
    let isFocused: Bool

    func body(content: Content) -> some View {
        content
            .scaleEffect(isFocused ? Tv.Dimen.focusScale : 1.0)
            .overlay(
                RoundedRectangle(cornerRadius: Tv.Dimen.radiusM)
                    .strokeBorder(
                        isFocused ? Tv.Color.focusRing : .clear,
                        lineWidth: Tv.Dimen.focusRingWidth,
                    ),
            )
            .shadow(color: isFocused ? Tv.Color.focusGlow : .clear, radius: isFocused ? 18 : 0)
    }
}

extension View {
    func tvFocus(_ isFocused: Bool) -> some View {
        modifier(TvFocusStyle(isFocused: isFocused))
    }
}
