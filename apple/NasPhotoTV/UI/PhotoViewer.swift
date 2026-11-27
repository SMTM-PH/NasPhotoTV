import SwiftUI
import AVFoundation

/// 全屏看图（对标 Android `PhotoViewerScreen.kt` / `ViewerState.kt`）：
///  - 普通照片 2× 放大 + 方向键平移，全景按 0.33 步长左右巡览；
///  - 实况照片按需播放一次（静音、播完回静态）；
///  - 视频封面「确认 / 播放键」进入 AVPlayer；
///  - 5 秒幻灯片（跳过视频）、收藏、信息面板、工具条、胶片条。
struct PhotoViewer: View {

    let items: [MediaItem]
    let initialIndex: Int
    var contextTitle: String = ""
    var onClose: () -> Void

    private enum Chip: Int, Hashable {
        case star = 0, play = 1, info = 2, zoom = 3, close = 4
    }

    struct VideoSession: Identifiable {
        let id = UUID()
        let items: [MediaItem]
        let index: Int
        let title: String
    }

    @ObservedObject private var favorites = FavoritesStore.shared
    @ObservedObject private var settings = AppSettings.shared
    @StateObject private var liveModel = LiveVideoModel()

    @State private var index = 0
    @State private var infoExpanded = false
    @State private var chromeVisible = true
    @State private var hudVisible = true
    @State private var zoomed = false
    @State private var panX: CGFloat = 0
    @State private var panY: CGFloat = 0
    @State private var panoramaOffset: CGFloat = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var slideshowPlaying = false
    @State private var preparedSlide: (index: Int, image: UIImage)?
    @State private var liveRequest = 0
    @State private var videoRequest: VideoSession?
    @State private var chromeNonce = 0
    @State private var hudNonce = 0
    @FocusState private var focusedChip: Chip?

    private var current: MediaItem? { items.indices.contains(index) ? items[index] : nil }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            stage
            MusicEffect(active: slideshowPlaying)

            if hudVisible || infoExpanded {
                hud
                    .transition(.opacity)
            }
            if slideshowPlaying {
                slideshowIndicator
            }
            if chromeVisible {
                chrome
                    .transition(.opacity)
            }
        }
        .onAppear {
            index = min(max(0, initialIndex), max(0, items.count - 1))
            FavoritesStore.shared.load(accountId: settings.username)
            showChrome()
        }
        .onChange(of: index) { _ in
            resetTransforms()
            if !slideshowPlaying {
                preparedSlide = nil
                showChrome()
            }
        }
        .onMoveCommand { direction in
            slideshowPlaying = false
            switch direction {
            case .left: moveHorizontal(-1)
            case .right: moveHorizontal(1)
            case .up:
                if zoomed, !(current?.isPanorama ?? false) {
                    panY = max(-0.5, panY - 0.5)
                } else {
                    infoExpanded.toggle()
                    showChrome()
                }
            case .down:
                if zoomed, !(current?.isPanorama ?? false) {
                    panY = min(0.5, panY + 0.5)
                } else {
                    chromeVisible = true
                    focusedChip = .star
                }
            @unknown default: break
            }
            showChrome()
        }
        .onPlayPauseCommand {
            guard let item = current else { return }
            if item.isVideo {
                openVideo()
            } else if item.isLive && !item.liveUrl.isEmpty {
                liveRequest += 1
                showChrome()
            } else {
                toggleSlideshow()
            }
        }
        .onExitCommand { onClose() }
        .onChange(of: liveRequest) { req in
            guard req > 0, let item = current, item.isLive, !item.liveUrl.isEmpty else { return }
            liveModel.playOnce(item.liveUrl)
        }
        .onChange(of: chromeNonce) { token in
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 3_000_000_000)
                if chromeNonce == token, !infoExpanded { chromeVisible = false }
            }
        }
        .onChange(of: hudNonce) { token in
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 1_500_000_000)
                if hudNonce == token, !infoExpanded { hudVisible = false }
            }
        }
        .task(id: slideshowPlaying) {
            guard slideshowPlaying else { return }
            await playSlideshow()
        }
        .fullScreenCover(item: $videoRequest) { session in
            VideoPlayerView(items: session.items, initialIndex: session.index, contextTitle: session.title) {
                videoRequest = nil
            }
        }
        .onDisappear { liveModel.stop() }
    }

    // MARK: - 舞台

    private var stage: some View {
        GeometryReader { geo in
            ZStack {
                if let item = current {
                    if item.isVideo {
                        RemoteImage(url: item.thumbnailUrl, contentMode: .fit)
                            .ignoresSafeArea()
                        videoOverlay(item)
                    } else {
                        photoStage(item, geo: geo)
                            .id(slideshowPlaying ? "slide-" + item.id : "photo")
                            .transition(slideshowTransition)
                    }
                } else {
                    Text("没有可显示的照片")
                        .font(.system(size: Tv.Font.body))
                        .foregroundColor(Tv.Color.textSecondary)
                }
            }
            .frame(width: geo.size.width, height: geo.size.height)
            .clipped()
        }
        .ignoresSafeArea()
    }

    private var slideshowTransition: AnyTransition {
        guard slideshowPlaying, !reduceMotion else { return .identity }
        switch settings.playback.photoMotion {
        case .dissolve: return .opacity
        case .slide: return .asymmetric(insertion: .move(edge: .trailing), removal: .move(edge: .leading))
        case .cut: return .identity
        }
    }

    private func photoStage(_ item: MediaItem, geo: GeometryProxy) -> some View {
        let panoramic = item.isPanorama
        return ZStack {
            if panoramic && !zoomed {
                panoramaImage(item, geo: geo)
            } else {
                photoImage(item, contentMode: .fit)
                    .ignoresSafeArea()
                    .scaleEffect(zoomed ? 2 : 1)
                    .offset(x: panX * geo.size.width, y: panY * geo.size.height)
            }
            if item.isLive && !item.liveUrl.isEmpty {
                PlayerLayerView(player: liveModel.player)
                    .opacity(liveModel.playing ? 1 : 0)
                    .allowsHitTesting(false)
            }
            if zoomed && !panoramic {
                VStack {
                    HStack {
                        Spacer()
                        VStack(alignment: .trailing, spacing: 2) {
                            Text("2× 放大")
                                .font(.system(size: Tv.Font.caption, weight: .semibold))
                            Text("方向键平移 · 确认退出放大")
                                .font(.system(size: Tv.Font.tiny))
                        }
                        .foregroundColor(.white.opacity(0.9))
                        .padding(Tv.Dimen.spaceM)
                        .background(Capsule().fill(.black.opacity(0.55)))
                    }
                    Spacer()
                }
            }
        }
    }

    private func panoramaImage(_ item: MediaItem, geo: GeometryProxy) -> some View {
        let aspect = item.pixelSize.map { CGFloat($0.0) / CGFloat($0.1) } ?? 2
        let imageWidth = geo.size.height * aspect
        let travel = max(0, imageWidth - geo.size.width)
        return photoImage(item, contentMode: .fill)
            .frame(width: imageWidth, height: geo.size.height)
            .offset(x: -travel * panoramaOffset)
    }

    @ViewBuilder
    private func photoImage(_ item: MediaItem, contentMode: ContentMode) -> some View {
        if let preparedSlide, preparedSlide.index == index {
            Image(uiImage: preparedSlide.image)
                .resizable()
                .aspectRatio(contentMode: contentMode)
        } else {
            RemoteImage(url: item.url, contentMode: contentMode)
        }
    }

    private func videoOverlay(_ item: MediaItem) -> some View {
        Button {
            openVideo()
        } label: {
            VStack(spacing: Tv.Dimen.spaceM) {
                Image(systemName: "play.fill")
                    .font(.system(size: 56))
                Text("按「确认」键播放视频")
                    .font(.system(size: Tv.Font.body, weight: .medium))
                if item.durationMs > 0 {
                    Text(Self.formatDuration(item.durationMs))
                        .font(.system(size: Tv.Font.caption))
                }
            }
            .foregroundColor(.white)
            .frame(width: 112 * 2.2, height: 112 * 2.2)
            .background(Circle().fill(.black.opacity(0.55)))
        }
        .buttonStyle(.plain)
        .focused($focusedChip, equals: .play)
    }

    // MARK: - 信息 HUD

    private var hud: some View {
        VStack {
            HStack {
                if let item = current {
                    VStack(alignment: .leading, spacing: Tv.Dimen.spaceXs) {
                        Text(item.title.isEmpty ? "未命名的照片" : item.title)
                            .font(.system(size: Tv.Font.cardTitle, weight: .medium))
                            .foregroundColor(Tv.Color.textPrimary)
                            .lineLimit(1)
                        HStack {
                            Text(Self.dateText(item.dateTaken))
                                .font(.system(size: Tv.Font.caption))
                                .foregroundColor(Tv.Color.textSecondary)
                            Spacer()
                            Text("\(index + 1) / \(items.count)")
                                .font(.system(size: Tv.Font.caption))
                                .foregroundColor(Tv.Color.textSecondary)
                        }
                        if infoExpanded {
                            Divider().background(Tv.Color.divider)
                            if !contextTitle.isEmpty { meta("相册", contextTitle) }
                            meta("尺寸", item.resolution.isEmpty ? "—" : item.resolution)
                            meta("文件", Self.fileLine(item))
                            badges(item)
                        }
                    }
                    .padding(.horizontal, Tv.Dimen.spaceM)
                    .padding(.vertical, Tv.Dimen.spaceS)
                    .frame(maxWidth: 420, alignment: .leading)
                    .background(
                        RoundedRectangle(cornerRadius: Tv.Dimen.radiusM)
                            .fill(.black.opacity(0.58))
                            .background(
                                LinearGradient(colors: [.black.opacity(0.58), .clear], startPoint: .top, endPoint: .bottom),
                            ),
                    )
                }
                Spacer()
            }
            .padding(.leading, Tv.Dimen.screenPadding)
            .padding(.top, Tv.Dimen.spaceL)
            Spacer()
        }
    }

    private func meta(_ label: String, _ value: String) -> some View {
        HStack(alignment: .top) {
            Text("\(label)：")
                .font(.system(size: Tv.Font.tiny))
                .foregroundColor(Tv.Color.textTertiary)
            Text(value)
                .font(.system(size: Tv.Font.tiny))
                .foregroundColor(Tv.Color.textSecondary)
                .lineLimit(1)
            Spacer()
        }
    }

    private func badges(_ item: MediaItem) -> some View {
        HStack(spacing: Tv.Dimen.spaceM) {
            if item.isLive { badge("LIVE · 实况", Tv.Color.accent) }
            if item.isPanorama { badge("全景 · 左右键平移", Tv.Color.accentAlt) }
            if Self.rawExtensions.contains(item.ext.uppercased()) { badge("RAW · 高清预览", Tv.Color.danger) }
        }
        .padding(.top, 2)
    }

    private func badge(_ text: String, _ color: Color) -> some View {
        HStack(spacing: 6) {
            Circle().fill(color).frame(width: 6, height: 6)
            Text(text)
                .font(.system(size: Tv.Font.tiny))
                .foregroundColor(.white.opacity(0.88))
        }
    }

    private var slideshowIndicator: some View {
        VStack {
            HStack {
                Spacer()
                HStack(spacing: Tv.Dimen.spaceS) {
                    Image(systemName: "pause.fill")
                    Text("幻灯片播放中 · 按方向键暂停")
                        .font(.system(size: Tv.Font.caption))
                }
                .foregroundColor(Tv.Color.textSecondary)
                .padding(Tv.Dimen.spaceM)
                .background(Capsule().fill(.black.opacity(0.55)))
            }
            .padding(.trailing, Tv.Dimen.screenPadding)
            .padding(.top, Tv.Dimen.spaceL)
            Spacer()
            if let item = current, item.isLive, liveModel.playing {
                Text("实况播放中 · 播完自动回到静态")
                    .font(.system(size: Tv.Font.caption))
                    .foregroundColor(Tv.Color.accent)
                    .padding(Tv.Dimen.spaceM)
            }
        }
    }

    // MARK: - 底部浮层（工具条 + 胶片条）

    private var chrome: some View {
        VStack {
            Spacer()
            VStack(spacing: Tv.Dimen.spaceXs) {
                HStack(spacing: Tv.Dimen.spaceS) {
                    chip(.star, system: starred ? "star.fill" : "star", title: starred ? "已收藏" : "收藏") {
                        if let item = current { favorites.toggle(item.id) }
                    }
                    chip(.play, system: itemIcon, title: itemPlayTitle) {
                        guard let item = current else { return }
                        if item.isVideo { openVideo() }
                        else if item.isLive { liveRequest += 1 }
                        else { toggleSlideshow() }
                    }
                    chip(.info, system: "info.circle", title: "信息") {
                        infoExpanded.toggle(); showChrome()
                    }
                    chip(.zoom, system: "arrow.up.left.and.arrow.down.right", title: zoomed ? "适应屏幕" : "放大") {
                        zoomed.toggle(); resetPan(); showChrome()
                    }
                    chip(.close, system: "xmark", title: "关闭") { onClose() }
                }
                .padding(.horizontal, Tv.Dimen.screenPadding)

                filmStrip
                    .frame(height: 80)
            }
            .padding(.top, Tv.Dimen.spaceL)
            .padding(.bottom, Tv.Dimen.spaceM)
            .background(
                LinearGradient(
                    colors: [.clear, .black.opacity(0.55), .black.opacity(0.88), Tv.Color.bg],
                    startPoint: .top, endPoint: .bottom,
                )
                .ignoresSafeArea(),
            )
        }
    }

    private func chip(_ tag: Chip, system: String, title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: system)
                    .font(.system(size: 22))
                Text(title)
                    .font(.system(size: Tv.Font.caption, weight: .medium))
                    .lineLimit(1)
            }
            .padding(.horizontal, Tv.Dimen.spaceM)
            .frame(height: 44)
            .background(Capsule().fill(focusedChip == tag ? Tv.Color.accentDim : Tv.Color.surface))
            .foregroundColor(focusedChip == tag ? Tv.Color.accent : Tv.Color.textPrimary)
        }
        .buttonStyle(.plain)
        .focused($focusedChip, equals: tag)
        .scaleEffect(focusedChip == tag ? Tv.Dimen.focusScale : 1)
        .overlay(Capsule().strokeBorder(focusedChip == tag ? .white : .clear, lineWidth: 3))
    }

    private var filmStrip: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Tv.Dimen.spaceS) {
                ForEach(Array(items.enumerated()), id: \.offset) { i, item in
                    Button {
                        index = i
                        infoExpanded = false
                    } label: {
                        ZStack(alignment: .bottomLeading) {
                            RemoteImage(url: item.thumbnailUrl)
                                .frame(width: 112, height: 64)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 8)
                                        .strokeBorder(i == index ? Tv.Color.accent : .clear, lineWidth: 3),
                                )
                            if item.isVideo {
                                Image(systemName: "play.fill")
                                    .font(.system(size: 26))
                                    .foregroundColor(.white)
                            }
                            if i == index {
                                Text("\(i + 1)")
                                    .font(.system(size: Tv.Font.tiny))
                                    .foregroundColor(.white)
                                    .padding(2)
                            }
                            if favorites.isFavorite(item.id) {
                                Image(systemName: "star.fill")
                                    .font(.system(size: 14))
                                    .foregroundColor(Tv.Color.danger)
                                    .padding(2)
                                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                            }
                        }
                        .opacity(i == index ? 1 : 0.62)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, Tv.Dimen.screenPadding)
        }
    }

    private var starred: Bool {
        guard let item = current else { return false }
        return item.starred || favorites.isFavorite(item.id)
    }

    private var itemIcon: String {
        guard let item = current else { return "play.fill" }
        if item.isVideo { return "play.fill" }
        return slideshowPlaying ? "pause.fill" : "play.fill"
    }

    private var itemPlayTitle: String {
        guard let item = current else { return "播放照片" }
        if item.isVideo { return "播放视频" }
        if item.isLive { return "播放实况" }
        return slideshowPlaying ? "暂停" : "播放照片"
    }

    // MARK: - 动作

    private func moveHorizontal(_ delta: Int) {
        guard let item = current else { return }
        if zoomed && !item.isPanorama {
            panX = min(0.5, max(-0.5, panX + CGFloat(delta) * 0.5))
            return
        }
        if item.isPanorama && !zoomed {
            let next = min(1, max(0, panoramaOffset + CGFloat(delta) * 0.33))
            if next != panoramaOffset {
                panoramaOffset = next
                return
            }
        }
        let next = index + delta
        if next < 0 { index = max(0, items.count - 1) }
        else if next >= items.count { index = 0 }
        else { index = next }
    }

    private func resetTransforms() {
        zoomed = false
        panX = 0
        panY = 0
        panoramaOffset = 0
        infoExpanded = false
        liveModel.stop()
    }

    private func resetPan() {
        panX = 0
        panY = 0
    }

    private func showChrome() {
        chromeVisible = true
        hudVisible = true
        chromeNonce += 1
        hudNonce += 1
    }

    private func toggleSlideshow() {
        guard items.contains(where: { !$0.isVideo }) else { return }
        slideshowPlaying.toggle()
        chromeVisible = true
        showChrome()
    }

    private func playSlideshow() async {
        let photos = items.indices.filter { !items[$0].isVideo }
        guard !photos.isEmpty else { return }
        var candidate = photos.firstIndex { $0 > index } ?? photos.startIndex
        while !Task.isCancelled, slideshowPlaying {
            let target = photos[candidate]
            if photos.count == 1, target == index { return }
            let item = items[target]
            async let interval: Void = Task.sleep(for: .seconds(settings.playback.photoIntervalSec))
            var decoded = await MediaImageLoader.image(at: item.url)
            if decoded == nil, item.thumbnailUrl != item.url {
                decoded = await MediaImageLoader.image(at: item.thumbnailUrl)
            }
            guard (try? await interval) != nil, !Task.isCancelled, slideshowPlaying else { return }
            if let decoded {
                let animation: Animation? = reduceMotion || settings.playback.photoMotion == .cut
                    ? nil : .easeInOut(duration: 0.45)
                withAnimation(animation) {
                    preparedSlide = (target, decoded)
                    index = target
                }
            }
            candidate = photos.indices.contains(candidate + 1) ? candidate + 1 : photos.startIndex
        }
    }

    private func openVideo() {
        guard let current else { return }
        let videos = items.filter(\.isVideo)
        guard let start = videos.firstIndex(where: { $0.id == current.id }) else { return }
        videoRequest = VideoSession(items: videos, index: start, title: contextTitle)
    }

    // MARK: - 格式化

    private static let rawExtensions: Set<String> = [
        "CR2", "CR3", "NEF", "ARW", "DNG", "RAF", "ORF", "RW2", "PEF", "SRW", "3FR", "ERF", "KDC", "MOS", "MRW", "X3F",
    ]

    private static func fileLine(_ item: MediaItem) -> String {
        let size = item.sizeBytes > 0
            ? ByteCountFormatter.string(fromByteCount: item.sizeBytes, countStyle: .file)
            : "—"
        let duration = item.isVideo && item.durationMs > 0 ? formatDuration(item.durationMs) : ""
        return [size, item.ext.uppercased(), duration].filter { !$0.isEmpty }.joined(separator: " · ")
    }

    private static func formatDuration(_ ms: Int64) -> String {
        let total = Int(max(0, ms) / 1000)
        return String(format: "%d:%02d", total / 60, total % 60)
    }

    private static func dateText(_ date: Date?) -> String {
        guard let date else { return "" }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = "yyyy年M月d日"
        return formatter.string(from: date)
    }
}

// MARK: - 实况照片动态片段（静音、播一次）

final class LiveVideoModel: ObservableObject {

    @Published var playing = false
    let player = AVPlayer()

    private var endObserver: NSObjectProtocol?
    private var preparedUrl = ""
    private var currentItem: AVPlayerItem?

    func playOnce(_ urlString: String) {
        guard let url = URL(string: urlString), !urlString.isEmpty else { return }
        if preparedUrl != urlString || currentItem == nil {
            tearDownItem()
            preparedUrl = urlString
            var options: [String: Any] = [:]
            if let token = FnOsTransport.shared.token {
                options["AVURLAssetHTTPHeaderFieldsKey"] = ["AccessToken": token]
            }
            let asset = AVURLAsset(url: url, options: options)
            let item = AVPlayerItem(asset: asset)
            currentItem = item
            player.replaceCurrentItem(with: item)
            endObserver = NotificationCenter.default.addObserver(
                forName: .AVPlayerItemDidPlayToEndTime,
                object: item,
                queue: .main,
            ) { [weak self] _ in
                self?.playing = false
            }
        }
        player.isMuted = true
        player.seek(to: .zero)
        player.play()
        playing = true
    }

    func stop() {
        player.pause()
        playing = false
        tearDownItem()
        preparedUrl = ""
    }

    private func tearDownItem() {
        if let endObserver {
            NotificationCenter.default.removeObserver(endObserver)
            self.endObserver = nil
        }
        currentItem = nil
        player.replaceCurrentItem(with: nil)
    }
}
