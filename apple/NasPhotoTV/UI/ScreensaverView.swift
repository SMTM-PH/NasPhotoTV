import SwiftUI
import Combine

/// 屏保 / 待机（对标 Android `ScreensaverScreen.kt` / `ScreensaverClock.kt`）：
/// 大号时钟 + 照片氛围 + 5 种时钟样式（方向键左右切换）+ 夜间调暖 +
/// Ken Burns 防烧屏 + 背景音乐；其他任意键只退出。
struct ScreensaverView: View {

    let photos: [MediaItem]
    @ObservedObject var settings: AppSettings
    var onExit: () -> Void

    @State private var photoIndex = 0
    @State private var now = Date()

    private let clockTimer: Publishers.Autoconnect<Timer.TimerPublisher>
    private let photoTimer: Publishers.Autoconnect<Timer.TimerPublisher>

    init(photos: [MediaItem], settings: AppSettings, onExit: @escaping () -> Void) {
        self.photos = photos
        self.settings = settings
        self.onExit = onExit
        self.clockTimer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()
        self.photoTimer = Timer.publish(
            every: TimeInterval(max(5, settings.screensaver.photoIntervalSec)),
            on: .main,
            in: .common,
        ).autoconnect()
    }

    private var style: ClockStyle { ClockStyle(raw: settings.screensaver.clockStyle) }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if !photos.isEmpty {
                let item = photos[photoIndex % photos.count]
                RemoteImage(url: item.thumbnailUrl)
                    .ignoresSafeArea()
                    .id(item.id)
                    .scaleEffect(settings.screensaver.kenBurns && !photoIndex.isMultiple(of: 2) ? 1.06 : 1.0)
                    .animation(.easeInOut(duration: 0.8), value: item.id)
                LinearGradient(
                    colors: [.black.opacity(0.55), .clear, .black.opacity(0.55)],
                    startPoint: .top, endPoint: .bottom,
                )
                .ignoresSafeArea()
                if settings.screensaver.nightDim {
                    Color(hex: 0xFF8A3D, alpha: 0.2).ignoresSafeArea()
                }
            }

            clockLayer

            VStack {
                Spacer()
                HStack {
                    Spacer()
                    Text("← → 换样式 · 其他键返回")
                        .font(.system(size: Tv.Font.tiny))
                        .foregroundColor(.white.opacity(0.4))
                        .padding(Tv.Dimen.spaceM)
                }
            }

            MusicEffect(active: true)
        }
        .onReceive(clockTimer) { date in now = date }
        .onReceive(photoTimer) { _ in
            guard photos.count > 1 else { return }
            photoIndex = (photoIndex + 1) % photos.count
        }
        .onMoveCommand { direction in
            switch direction {
            case .left: settings.screensaver.clockStyle = style.previous.rawValue
            case .right: settings.screensaver.clockStyle = style.next.rawValue
            default: onExit()
            }
        }
        .onPlayPauseCommand { onExit() }
        .onExitCommand { onExit() }
    }

    @ViewBuilder
    private var clockLayer: some View {
        switch style {
        case .cinema:
            VStack(spacing: Tv.Dimen.spaceS) {
                Spacer()
                Text(timeText)
                    .font(.system(size: Tv.Font.clockHuge, weight: .bold, design: .rounded))
                    .foregroundColor(.white)
                    .monospacedDigit()
                    .shadow(color: .black.opacity(0.4), radius: 24)
                if settings.screensaver.showDate {
                    Text(dateText)
                        .font(.system(size: Tv.Font.section))
                        .foregroundColor(.white.opacity(0.85))
                }
                Spacer()
            }
        case .flip:
            VStack(spacing: Tv.Dimen.spaceS) {
                Spacer()
                if settings.screensaver.showDate {
                    Text("星期\(weekdayText) · \(shortDateText)")
                        .font(.system(size: Tv.Font.section))
                        .foregroundColor(.white.opacity(0.7))
                }
                Text(timeText)
                    .font(.system(size: 160, weight: .ultraLight, design: .rounded))
                    .foregroundColor(.white)
                    .monospacedDigit()
                Spacer()
            }
        case .thin:
            VStack(spacing: Tv.Dimen.spaceS) {
                Spacer()
                if settings.screensaver.showDate {
                    Text(dateText)
                        .font(.system(size: Tv.Font.cardTitle))
                        .foregroundColor(.white.opacity(0.7))
                }
                Text(timeText)
                    .font(.system(size: 120, weight: .thin))
                    .foregroundColor(.white)
                    .monospacedDigit()
                Spacer()
            }
        case .minimal:
            ZStack(alignment: .topTrailing) {
                VStack(spacing: Tv.Dimen.spaceS) {
                    Spacer()
                    Text("星期\(weekdayText)")
                        .font(.system(size: 72, weight: .semibold))
                        .foregroundColor(.white)
                    if settings.screensaver.showDate {
                        Text(shortDateText)
                            .font(.system(size: 64, weight: .light))
                            .foregroundColor(.white.opacity(0.85))
                    }
                    Spacer()
                }
                Text(timeText)
                    .font(.system(size: Tv.Font.section, weight: .medium))
                    .foregroundColor(.white.opacity(0.7))
                    .monospacedDigit()
                    .padding(Tv.Dimen.spaceXl)
            }
        case .dateFocus:
            VStack(spacing: Tv.Dimen.spaceM) {
                Spacer()
                Text("星期\(weekdayText)")
                    .font(.system(size: 84, weight: .bold))
                    .foregroundColor(.white)
                if settings.screensaver.showDate {
                    Text(shortDateText)
                        .font(.system(size: 56, weight: .light))
                        .foregroundColor(.white.opacity(0.9))
                }
                Text(timeText)
                    .font(.system(size: Tv.Font.section))
                    .foregroundColor(.white.opacity(0.6))
                    .monospacedDigit()
                miniMonth
                Spacer()
            }
        }
    }

    private var miniMonth: some View {
        let calendar = Calendar.current
        let component = calendar.dateComponents([.year, .month], from: now)
        let first = calendar.date(from: component) ?? now
        let range = calendar.range(of: .day, in: .month, for: first) ?? 1..<31
        let weekday = calendar.component(.weekday, from: first)
        let leading = (weekday + 5) % 7
        let today = calendar.component(.day, from: now)
        return VStack(spacing: 4) {
            HStack(spacing: 10) {
                ForEach(["一", "二", "三", "四", "五", "六", "日"], id: \.self) { label in
                    Text(label).font(.system(size: Tv.Font.tiny)).foregroundColor(.white.opacity(0.45))
                        .frame(width: 28)
                }
            }
            let cells = Array(repeating: "", count: leading)
                + range.map { String($0) }
            ForEach(0..<((cells.count + 6) / 7), id: \.self) { row in
                HStack(spacing: 10) {
                    ForEach(0..<7, id: \.self) { col in
                        let idx = row * 7 + col
                        let value = idx < cells.count ? cells[idx] : ""
                        Text(value)
                            .font(.system(size: Tv.Font.tiny))
                            .foregroundColor(value == String(today) ? Tv.Color.accent : .white.opacity(0.7))
                            .frame(width: 28)
                    }
                }
            }
        }
        .padding(Tv.Dimen.spaceM)
        .background(RoundedRectangle(cornerRadius: Tv.Dimen.radiusM).fill(.black.opacity(0.3)))
    }

    private var timeText: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = "HH:mm"
        return formatter.string(from: now)
    }

    private var dateText: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = "M月d日 EEEE"
        return formatter.string(from: now)
    }

    private var shortDateText: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = "M月d日"
        return formatter.string(from: now)
    }

    private var weekdayText: String {
        let weekday = Calendar.current.component(.weekday, from: now)
        return ["日", "一", "二", "三", "四", "五", "六"][weekday - 1]
    }
}

/// 时钟样式（对齐 Android `ClockStyle`）。
private enum ClockStyle: String, CaseIterable {
    case cinema, flip, thin, minimal, dateFocus

    init(raw: String) { self = ClockStyle(rawValue: raw) ?? .cinema }

    var next: ClockStyle {
        let all = ClockStyle.allCases
        return all[(all.firstIndex(of: self)! + 1) % all.count]
    }

    var previous: ClockStyle {
        let all = ClockStyle.allCases
        return all[(all.firstIndex(of: self)! - 1 + all.count) % all.count]
    }
}
