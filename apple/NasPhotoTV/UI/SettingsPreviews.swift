import SwiftUI

struct SettingsAppearancePreview: View {
    let current: TvAppearance

    var body: some View {
        SettingsSummary(title: "外观预览", text: "看图、视频、艺术画册与屏保使用深色背景。") {
            HStack(spacing: Tv.Dimen.spaceM) {
                sample(.light)
                sample(.dark)
            }
        }
    }

    private func sample(_ mode: TvAppearance) -> some View {
        VStack(alignment: .leading, spacing: Tv.Dimen.spaceM) {
            Text(mode.title).font(.system(size: Tv.Font.cardTitle, weight: .bold))
            HStack(spacing: Tv.Dimen.spaceS) {
                ForEach([Tv.Color.accent, Tv.Color.accentAlt, Tv.Color.stage], id: \.self) { swatch in
                    RoundedRectangle(cornerRadius: Tv.Dimen.radiusM).fill(swatch).frame(height: 80)
                }
            }
            Text(current == mode ? "当前使用" : " ").font(.system(size: Tv.Font.caption))
        }
        .padding(Tv.Dimen.spaceM)
        .foregroundStyle(Tv.Color.textPrimary)
        .background(Tv.Color.bg, in: RoundedRectangle(cornerRadius: Tv.Dimen.radiusM))
        .overlay {
            RoundedRectangle(cornerRadius: Tv.Dimen.radiusM)
                .strokeBorder(current == mode ? Tv.Color.focusRing : Tv.Color.divider,
                              lineWidth: current == mode ? Tv.Dimen.focusRingWidth : 1)
        }
        .colorScheme(mode == .light ? .light : .dark)
    }
}

struct SettingsMotionPreview: View {
    let photos: [MediaItem]
    let motion: SlideshowMotion
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var progress = 0

    var body: some View {
        SettingsSummary(title: "转场预览", text: motion.title) {
            ZStack {
                if let photo = photos.isEmpty ? nil : photos[progress % min(photos.count, 2)] {
                    RemoteImage(url: photo.thumbnailUrl)
                        .overlay(alignment: .bottomLeading) {
                            Text(progress.isMultiple(of: 2) ? "照片 A" : "照片 B")
                                .padding(Tv.Dimen.spaceS)
                                .background(.black.opacity(0.7), in: Capsule())
                                .foregroundStyle(.white)
                                .padding(Tv.Dimen.spaceM)
                        }
                } else {
                    LinearGradient(colors: progress.isMultiple(of: 2)
                                   ? [Tv.Color.accentAlt, Tv.Color.stage] : [Tv.Color.accent, Tv.Color.surface],
                                   startPoint: .topLeading, endPoint: .bottomTrailing)
                    Image(systemName: progress.isMultiple(of: 2) ? "mountain.2" : "sun.horizon")
                        .font(.system(size: Tv.Font.display))
                        .foregroundStyle(.white)
                }
            }
            .id(progress)
            .transition(transition)
            .frame(height: 265)
            .frame(maxWidth: .infinity)
            .clipShape(RoundedRectangle(cornerRadius: Tv.Dimen.radiusM))
        }
        .task(id: motion) {
            progress = 0
            while !Task.isCancelled {
                do { try await Task.sleep(for: .seconds(2)) } catch { return }
                withAnimation(reduceMotion || motion == .cut ? nil : .easeInOut(duration: 0.45)) { progress += 1 }
            }
        }
    }

    private var transition: AnyTransition {
        guard !reduceMotion else { return .identity }
        switch motion {
        case .dissolve: return .opacity
        case .slide: return .asymmetric(insertion: .move(edge: .trailing), removal: .move(edge: .leading))
        case .cut: return .identity
        }
    }
}

struct SettingsSaverPreview: View {
    let photos: [MediaItem]
    let settings: ScreensaverSettings

    var body: some View {
        SettingsSummary(title: "屏保预览", text: settings.display.title) {
            ZStack {
                if settings.display == .wall && !photos.isEmpty {
                    HStack(spacing: Tv.Dimen.spaceXs) {
                        ForEach(photos.prefix(3)) { item in
                            RemoteImage(url: item.thumbnailUrl).frame(maxWidth: .infinity).clipped()
                        }
                    }
                } else if let first = photos.first {
                    RemoteImage(url: first.thumbnailUrl)
                } else {
                    LinearGradient(colors: [Tv.Color.stage, Tv.Color.accentAlt.opacity(0.5)],
                                   startPoint: .topLeading, endPoint: .bottomTrailing)
                }
                if settings.display == .clock {
                    TimelineView(.periodic(from: .now, by: 60)) { context in
                        VStack {
                            Text(context.date, style: .time)
                                .font(.system(size: 60, weight: .semibold, design: .rounded))
                            if settings.showDate { Text(context.date, style: .date) }
                        }
                        .foregroundStyle(.white)
                        .padding(Tv.Dimen.spaceM)
                        .background(.black.opacity(0.5), in: RoundedRectangle(cornerRadius: Tv.Dimen.radiusL))
                    }
                }
            }
            .frame(height: 265)
            .frame(maxWidth: .infinity)
            .clipShape(RoundedRectangle(cornerRadius: Tv.Dimen.radiusM))
            .colorScheme(.dark)
        }
    }
}

struct SettingsSummary<Content: View>: View {
    let title: String
    let text: String
    @ViewBuilder var content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: Tv.Dimen.spaceM) {
            Text(title).font(.system(size: Tv.Font.section, weight: .bold))
            content()
            Text(text).font(.system(size: Tv.Font.body)).foregroundStyle(Tv.Color.textSecondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(Tv.Dimen.spaceL)
        .foregroundStyle(Tv.Color.textPrimary)
        .background(Tv.Color.stage, in: RoundedRectangle(cornerRadius: Tv.Dimen.radiusL))
    }
}

extension SettingsSummary where Content == EmptyView {
    init(title: String, text: String) { self.init(title: title, text: text) { EmptyView() } }
}
