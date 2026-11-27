import SwiftUI
import UIKit

/// NAS Photo TV 设计规范（Design Tokens）—— 与 Android 端 `Tokens.kt` 对齐。
/// 10-foot UI：按 1920×1080 逻辑画布设计，Apple TV 上 1pt ≈ 2px（1080p）。
enum Tv {
    enum Color {
        static let bg = adaptive(dark: 0x06131E, light: 0xE9EEF4)
        static let bgElevated = adaptive(dark: 0x12141B, light: 0xF3F6FA)
        static let surface = adaptive(dark: 0x1A1D26, light: 0xFFFFFF)
        static let surfaceHigh = adaptive(dark: 0x252935, light: 0xDDE5EE)
        static let stage = adaptive(dark: 0x0B202F, light: 0xDDE7F0)
        static let surfaceSoft = adaptive(dark: 0x122A3A, light: 0xD7E6F1)
        static let cinemaPanel = SwiftUI.Color(hex: 0x081D2B, alpha: 0.8)
        static let textPrimary = adaptive(dark: 0xF5F7FA, light: 0x0E1A28)
        static let textSecondary = adaptive(dark: 0xA9B0BE, light: 0x44566B)
        static let textTertiary = adaptive(dark: 0x8C97A8, light: 0x546579)
        static let accent = adaptive(dark: 0x31B6ED, light: 0x096494)
        static let accentDim = adaptive(dark: 0x31B6ED, light: 0x096494, alpha: 0.2)
        static let accentAlt = adaptive(dark: 0x5B8CFF, light: 0x3557B8)
        static let danger = adaptive(dark: 0xFF6B6B, light: 0xAF2E26)
        static let divider = adaptive(dark: 0xFFFFFF, light: 0x12263A, alpha: 0.12)
        static let focusRing = adaptive(dark: 0xFFFFFF, light: 0x0B3A5C)
        static let focusGlow = adaptive(dark: 0x31B6ED, light: 0x0B3A5C, alpha: 0.30)
        static let paper = SwiftUI.Color(hex: 0xF1E7D7)
        static let paperInk = SwiftUI.Color(hex: 0x291F18)

        private static func adaptive(dark: UInt32, light: UInt32, alpha: Double = 1) -> SwiftUI.Color {
            SwiftUI.Color(uiColor: UIColor { traits in
                let hex = traits.userInterfaceStyle == .light ? light : dark
                return UIColor(
                    red: CGFloat((hex >> 16) & 0xff) / 255,
                    green: CGFloat((hex >> 8) & 0xff) / 255,
                    blue: CGFloat(hex & 0xff) / 255,
                    alpha: alpha
                )
            })
        }
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

struct TvRemoteButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.opacity(configuration.isPressed ? 0.82 : 1)
    }
}

extension View {
    func tvFocus(_ isFocused: Bool) -> some View {
        focusEffectDisabled()
            .modifier(TvFocusStyle(isFocused: isFocused))
    }
}


extension TvAppearance {
    var colorScheme: ColorScheme? {
        switch self {
        case .dark: return .dark
        case .light: return .light
        case .system: return nil
        }
    }
}
