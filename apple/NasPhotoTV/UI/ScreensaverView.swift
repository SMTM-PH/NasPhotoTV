import SwiftUI
import Combine

/// 屏保 / 待机：大号时钟 + 照片氛围（对齐 Android 端 `ScreensaverScreen.kt`）。
/// 防烧屏：照片交替做 0%/6% 的轻微缩放；规则：任意键只退出，不同时触发页面动作。
struct ScreensaverView: View {

    let photos: [MediaItem]
    let settings: ScreensaverSettings
    var onExit: () -> Void

    @State private var photoIndex = 0

    private let clockTimer: Publishers.Autoconnect<Timer.TimerPublisher>
    private let photoTimer: Publishers.Autoconnect<Timer.TimerPublisher>

    init(photos: [MediaItem], settings: ScreensaverSettings, onExit: @escaping () -> Void) {
        self.photos = photos
        self.settings = settings
        self.onExit = onExit
        self.clockTimer = Timer.publish(every: 1, on: .main, in: .common).autoconnect()
        self.photoTimer = Timer.publish(
            every: TimeInterval(max(5, settings.photoIntervalSec)),
            on: .main,
            in: .common,
        ).autoconnect()
        _photoIndex = State(initialValue: 0)
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if !photos.isEmpty {
                let item = photos[photoIndex % photos.count]
                RemoteImage(url: item.thumbnailUrl)
                    .ignoresSafeArea()
                    .id(item.id)
                    // 防烧屏：相邻两张交替轻微缩放（0% / 6%）
                    .scaleEffect(photoIndex.isMultiple(of: 2) ? 1.0 : 1.06)
                    .animation(.easeInOut(duration: 0.6), value: item.id)
                // 上/下压暗渐变，让时钟始终可读
                LinearGradient(
                    colors: [.black.opacity(0.55), .clear, .black.opacity(0.55)],
                    startPoint: .top, endPoint: .bottom,
                )
                .ignoresSafeArea()
            }

            VStack {
                Spacer()
                clock
                if settings.showDate {
                    Text(dateText)
                        .font(.system(size: Tv.Font.section))
                        .foregroundColor(.white.opacity(0.85))
                        .padding(.top, Tv.Dimen.spaceS)
                }
                Spacer()
                HStack {
                    Spacer()
                    Text("按任意键退出屏保")
                        .font(.system(size: Tv.Font.tiny))
                        .foregroundColor(.white.opacity(0.4))
                        .padding(Tv.Dimen.spaceM)
                }
            }
        }
        .onReceive(clockTimer) { _ in
            // 每秒触发一次重渲染，驱动时钟文本刷新
        }
        .onReceive(photoTimer) { _ in
            guard photos.count > 1 else { return }
            photoIndex = (photoIndex + 1) % photos.count
        }
        // 任意键只退出：方向键 / 确认 / 播放暂停 / 返回
        .onMoveCommand { _ in onExit() }
        .onPlayPauseCommand { onExit() }
        .onExitCommand { onExit() }
    }

    private var clock: some View {
        Text(timeText)
            .font(.system(size: Tv.Font.clockHuge, weight: .bold, design: .rounded))
            .foregroundColor(.white)
            .shadow(color: .black.opacity(0.4), radius: 24)
            .monospacedDigit()
    }

    private var timeText: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = "HH:mm"
        return formatter.string(from: Date())
    }

    private var dateText: String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = "M月d日 EEEE"
        return formatter.string(from: Date())
    }
}
