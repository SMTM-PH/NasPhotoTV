import SwiftUI
import AVFoundation
import UIKit

/// 全屏视频播放（对标 Android `VideoPlayerScreen.kt`）：
/// 用 AVPlayer + AVPlayerLayer 自建控制条（不用系统播放器 UI），
/// 左右键快退/快进 10 秒、播放暂停键切换、确认键切换、BACK 退出。
struct VideoPlayerView: View {

    let items: [MediaItem]
    let initialIndex: Int
    var contextTitle: String = ""
    var onClose: () -> Void

    @StateObject private var model = VideoPlayerModel()
    @ObservedObject private var settings = AppSettings.shared

    @State private var index = 0
    @State private var controlsVisible = true
    @State private var controlsNonce = 0

    private var current: MediaItem? { items.indices.contains(index) ? items[index] : nil }

    private var isPortrait: Bool {
        let sides = current?.resolution.split(separator: "x").compactMap { Int($0) } ?? []
        if sides.count == 2, sides[0] > 0, sides[1] > 0 { return sides[1] > sides[0] }
        return model.presentationSize.height > model.presentationSize.width
    }

    private var videoGravity: AVLayerVideoGravity {
        isPortrait && settings.playback.videoFit == .fill ? .resizeAspectFill : .resizeAspect
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if isPortrait, settings.playback.videoFit == .automatic,
               let cover = current?.thumbnailUrl, !cover.isEmpty {
                GeometryReader { geometry in
                    RemoteImage(url: cover, contentMode: .fill)
                        .frame(width: geometry.size.width, height: geometry.size.height)
                        .clipped()
                        .overlay(.black.opacity(0.72))
                }
                .ignoresSafeArea()
            }
            PlayerLayerView(player: model.player, gravity: videoGravity)
                .ignoresSafeArea()

            if model.buffering {
                ProgressView()
                    .progressViewStyle(.circular)
                    .tint(Tv.Color.accent)
                    .scaleEffect(2)
            }

            if let error = model.error {
                errorView(error)
            } else if controlsVisible {
                controls
            }
        }
        .onAppear {
            index = min(max(0, initialIndex), max(0, items.count - 1))
            model.onEnded = {
                Task { @MainActor in
                    handleEnded(settings: AppSettings.shared)
                }
            }
            loadCurrent()
        }
        .onChange(of: index) { _ in loadCurrent() }
        .onMoveCommand { direction in
            switch direction {
            case .left:
                model.seek(-10)
                showControls()
            case .right:
                model.seek(10)
                showControls()
            case .up, .down:
                controlsVisible.toggle()
                showControls()
            @unknown default:
                break
            }
        }
        .onPlayPauseCommand {
            model.togglePlayPause()
            showControls()
        }
        .onExitCommand { onClose() }
        .onChange(of: controlsNonce) { token in
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 3_200_000_000)
                if controlsNonce == token, controlsVisible, model.isPlaying {
                    controlsVisible = false
                }
            }
        }
        .onDisappear { model.stop() }
    }

    // MARK: - 控制条

    private var controls: some View {
        VStack {
            HStack(alignment: .firstTextBaseline) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(current?.title ?? "")
                        .font(.system(size: Tv.Font.section, weight: .semibold))
                        .foregroundColor(Tv.Color.textPrimary)
                        .lineLimit(1)
                    if !contextTitle.isEmpty {
                        Text(contextTitle)
                            .font(.system(size: Tv.Font.tiny))
                            .foregroundColor(Tv.Color.textTertiary)
                    }
                }
                Spacer()
                Text("\(index + 1) / \(items.count)")
                    .font(.system(size: Tv.Font.caption))
                    .foregroundColor(Tv.Color.textSecondary)
            }
            .padding(.horizontal, Tv.Dimen.screenPadding)
            .padding(.top, Tv.Dimen.spaceL)
            .background(
                LinearGradient(colors: [.black.opacity(0.75), .clear], startPoint: .top, endPoint: .bottom)
                    .ignoresSafeArea(),
            )

            Spacer()

            VStack(spacing: Tv.Dimen.spaceM) {
                progressTrack
                HStack(spacing: Tv.Dimen.spaceM) {
                    Button {
                        model.togglePlayPause()
                        showControls()
                    } label: {
                        Image(systemName: model.isPlaying ? "pause.fill" : "play.fill")
                    }
                    .accessibilityLabel(model.isPlaying ? "暂停" : "播放")
                    Text("\(formatTime(model.positionMs)) / \(formatTime(model.durationMs))")
                        .font(.system(size: Tv.Font.caption))
                        .foregroundColor(Tv.Color.textSecondary)
                    Spacer()
                    Button("画面 · \(settings.playback.videoFit.title)") {
                        settings.playback.videoFit = settings.playback.videoFit.next
                        showControls()
                    }
                    if items.count > 1 {
                        Button("上一个") { step(-1) }
                            .buttonStyle(.plain)
                            .foregroundColor(Tv.Color.textSecondary)
                        Button("下一个") { step(1) }
                            .buttonStyle(.plain)
                            .foregroundColor(Tv.Color.textSecondary)
                    }
                    Text("ENTER 播放/暂停 · ←→ 快进 10 秒 · BACK 返回")
                        .font(.system(size: Tv.Font.tiny))
                        .foregroundColor(Tv.Color.textTertiary)
                }
            }
            .padding(.horizontal, Tv.Dimen.screenPadding)
            .padding(.bottom, Tv.Dimen.spaceL)
            .background(
                LinearGradient(colors: [.clear, .black.opacity(0.85)], startPoint: .top, endPoint: .bottom)
                    .ignoresSafeArea(),
            )
        }
    }

    private var progressTrack: some View {
        let fraction: CGFloat = model.durationMs > 0
            ? CGFloat(model.positionMs) / CGFloat(model.durationMs)
            : 0
        return GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(Tv.Color.divider)
                Capsule().fill(Tv.Color.accent)
                    .frame(width: geo.size.width * min(max(fraction, 0), 1))
            }
        }
        .frame(height: 6)
    }

    private func errorView(_ message: String) -> some View {
        VStack(spacing: Tv.Dimen.spaceL) {
            Text("这个视频无法播放")
                .font(.system(size: Tv.Font.section, weight: .semibold))
                .foregroundColor(Tv.Color.textPrimary)
            Text(message)
                .font(.system(size: Tv.Font.body))
                .foregroundColor(Tv.Color.textSecondary)
                .multilineTextAlignment(.center)
            Text(
                index < items.count - 1
                    ? "按遥控器「下一个」跳到下一个（第 \(index + 1) / \(items.count) 个）"
                    : "已经是最后一个（第 \(index + 1) / \(items.count) 个），按 BACK 返回",
            )
            .font(.system(size: Tv.Font.caption))
            .foregroundColor(Tv.Color.textTertiary)
            if index < items.count - 1 {
                Button("下一个") { step(1) }
                    .buttonStyle(.plain)
                    .foregroundColor(Tv.Color.accent)
            }
        }
        .padding(.horizontal, 64)
    }

    // MARK: - 动作

    private func loadCurrent() {
        guard let item = current else { return }
        var headers: [String: String] = [:]
        if let token = FnOsTransport.shared.token { headers["AccessToken"] = token }
        model.load(item, headers: headers, loop: settings.playback.loopMode)
        showControls()
    }

    private func handleEnded(settings: AppSettings) {
        if settings.playback.loopMode {
            model.seekToStartAndPlay()
        } else if settings.playback.autoPlayNext && index < items.count - 1 {
            index += 1
        } else {
            model.isPlaying = false
            controlsVisible = true
        }
    }

    private func step(_ delta: Int) {
        let next = index + delta
        guard items.indices.contains(next) else { return }
        index = next
        showControls()
    }

    private func showControls() {
        controlsVisible = true
        controlsNonce += 1
    }

    private func formatTime(_ ms: Int64) -> String {
        let total = Int(max(0, ms) / 1000)
        let h = total / 3600, m = (total % 3600) / 60, s = total % 60
        return h > 0 ? String(format: "%d:%02d:%02d", h, m, s) : String(format: "%d:%02d", m, s)
    }
}

// MARK: - AVPlayer 封装

final class VideoPlayerModel: ObservableObject {

    @Published var isPlaying = false
    @Published var positionMs: Int64 = 0
    @Published var durationMs: Int64 = 0
    @Published var buffering = true
    @Published var presentationSize: CGSize = .zero
    @Published var error: String?
    let player = AVPlayer()

    /// 播放结束回调（由 View 决定自动下一个 / 循环 / 停止）
    var onEnded: (() -> Void)?

    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?
    private var statusObservation: NSKeyValueObservation?
    private var loopMode = false

    func load(_ item: MediaItem, headers: [String: String], loop: Bool) {
        cleanup()
        loopMode = loop
        error = nil
        buffering = true
        positionMs = 0
        durationMs = 0
        presentationSize = .zero
        isPlaying = false
        guard let url = URL(string: item.url), !item.url.isEmpty else {
            error = "视频地址无效"; buffering = false; return
        }
        var options: [String: Any] = [:]
        if !headers.isEmpty { options["AVURLAssetHTTPHeaderFieldsKey"] = headers }
        if let cookies = HTTPCookieStorage.shared.cookies(for: url), !cookies.isEmpty {
            options["AVURLAssetHTTPCookiesKey"] = cookies
        }
        let asset = AVURLAsset(url: url, options: options)
        let playerItem = AVPlayerItem(asset: asset)
        player.replaceCurrentItem(with: playerItem)
        player.actionAtItemEnd = .pause

        statusObservation = playerItem.observe(\.status) { [weak self] observed, _ in
            DispatchQueue.main.async {
                guard let self, self.player.currentItem === observed else { return }
                switch observed.status {
                case .readyToPlay:
                    self.presentationSize = observed.presentationSize
                    let seconds = observed.duration.seconds
                    if seconds.isFinite, seconds > 0 { self.durationMs = Int64(seconds * 1000) }
                    self.buffering = false
                    self.player.play()
                    self.isPlaying = true
                case .failed:
                    self.error = Self.describe(observed.error)
                    self.buffering = false
                    self.isPlaying = false
                default:
                    break
                }
            }
        }
        timeObserver = player.addPeriodicTimeObserver(
            forInterval: CMTime(value: 400, timescale: 1000),
            queue: .main,
        ) { [weak self] time in
            guard let self else { return }
            let seconds = time.seconds
            if seconds.isFinite { self.positionMs = Int64(max(0, seconds) * 1000) }
            self.isPlaying = self.player.timeControlStatus == .playing
        }
        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: playerItem,
            queue: .main,
        ) { [weak self] _ in
            guard let self else { return }
            self.isPlaying = false
            self.onEnded?()
        }
    }

    func togglePlayPause() {
        if player.timeControlStatus == .playing {
            player.pause(); isPlaying = false
        } else {
            player.play(); isPlaying = true
        }
    }

    func seek(_ deltaSeconds: Int64) {
        let current = CMTimeGetSeconds(player.currentTime())
        let duration = player.currentItem?.duration.seconds ?? 0
        guard current.isFinite else { return }
        var target = max(0, current + Double(deltaSeconds))
        if duration.isFinite, duration > 0 { target = min(target, duration) }
        player.seek(to: CMTime(seconds: target, preferredTimescale: 600))
        positionMs = Int64(target * 1000)
    }

    func seekToStartAndPlay() {
        player.seek(to: .zero)
        player.play()
        isPlaying = true
    }

    func stop() {
        player.pause()
        cleanup()
        player.replaceCurrentItem(with: nil)
    }

    private func cleanup() {
        if let timeObserver { player.removeTimeObserver(timeObserver); self.timeObserver = nil }
        if let endObserver { NotificationCenter.default.removeObserver(endObserver); self.endObserver = nil }
        statusObservation?.invalidate()
        statusObservation = nil
    }

    private static func describe(_ error: Error?) -> String {
        let text = (error as NSError?)?.localizedDescription ?? ""
        let lower = text.lowercased()
        if lower.contains("decod") || lower.contains("codec") {
            return "设备解码能力不足（可能是 4K / 10bit / 高帧率素材）"
        }
        if lower.contains("format") || lower.contains("unsupported") {
            return "这台电视不支持该视频格式"
        }
        if lower.contains("network") || lower.contains("timed out") || lower.contains("connect") {
            return "网络连接失败，检查电视与 NAS 是否在同一局域网"
        }
        if lower.contains("404") || lower.contains("not found") {
            return "NAS 上没有找到这个视频文件"
        }
        return text.isEmpty ? "读取视频数据失败" : text
    }
}

// MARK: - AVPlayerLayer 承载视图

struct PlayerLayerView: UIViewRepresentable {
    let player: AVPlayer
    var gravity: AVLayerVideoGravity = .resizeAspect

    func makeUIView(context: Context) -> PlayerContainerView {
        PlayerContainerView(player: player)
    }

    func updateUIView(_ uiView: PlayerContainerView, context: Context) {
        uiView.playerLayer.videoGravity = gravity
    }
}

final class PlayerContainerView: UIView {
    override class var layerClass: AnyClass { AVPlayerLayer.self }

    var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }

    init(player: AVPlayer) {
        super.init(frame: .zero)
        backgroundColor = .clear
        playerLayer.player = player
        playerLayer.videoGravity = .resizeAspect
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) 未实现") }
}
