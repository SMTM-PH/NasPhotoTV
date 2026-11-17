import SwiftUI

/// 首页：图库（全库按年份）/ 相册 / 设置 三项主导航，静置自动进入屏保。
/// 对齐 Android 端沉浸式首页的信息层级，首期不包含人物与智能分类（见 apple/README.md 路线图）。
struct HomeView: View {

    let repository: FnOsRepository
    var onDisconnect: () -> Void

    enum Tab: String, CaseIterable, Identifiable {
        case gallery = "图库"
        case albums = "相册"
        case settings = "设置"
        var id: String { rawValue }
    }

    /// 打开看图的会话（items + 起点）
    struct ViewerSession: Identifiable {
        let id = UUID()
        let items: [MediaItem]
        var index: Int
    }

    @ObservedObject private var settings = AppSettings.shared

    @State private var tab: Tab = .gallery
    @State private var items: [MediaItem] = []
    @State private var yearGroups: [YearGroup] = []
    @State private var albums: [Album] = []
    @State private var loading = true
    @State private var loadError: String?
    @State private var viewer: ViewerSession?
    @State private var openAlbum: Album?
    @State private var screensaverActive = false
    @FocusState private var focusedId: String?
    @State private var lastInteraction = Date()

    var body: some View {
        ZStack(alignment: .topLeading) {
            Tv.Color.bg.ignoresSafeArea()
            VStack(alignment: .leading, spacing: Tv.Dimen.spaceM) {
                header
                navRow
                content
            }
            .padding(Tv.Dimen.screenPadding)

            idleTick
        }
        .fullScreenCover(item: $viewer) { session in
            PhotoViewer(items: session.items, initialIndex: session.index) {
                viewer = nil
                touch()
            }
        }
        .fullScreenCover(item: $openAlbum) { album in
            AlbumDetailView(repository: repository, album: album) { media, index in
                openAlbum = nil
                viewer = ViewerSession(items: media, index: index)
            }
        }
        .fullScreenCover(isPresented: $screensaverActive) {
            ScreensaverView(photos: repository.screensaverPhotos, settings: settings.screensaver) {
                screensaverActive = false
                touch()
            }
        }
        .onChange(of: focusedId) { _ in touch() }
        .onChange(of: tab) { _ in touch() }
        .task { await load() }
    }

    // MARK: - 头部与导航

    private var header: some View {
        HStack(alignment: .firstTextBaseline) {
            Text("NAS Photo TV")
                .font(.system(size: Tv.Font.section, weight: .bold))
                .foregroundColor(Tv.Color.textPrimary)
            Spacer()
            Text(statusText)
                .font(.system(size: Tv.Font.caption))
                .foregroundColor(Tv.Color.textTertiary)
        }
    }

    private var statusText: String {
        if loading { return "\(settings.host) · 加载中…" }
        if let loadError { return "\(settings.host) · \(loadError)" }
        let photos = items.count
        return "\(settings.host) · \(photos) 项"
    }

    private var navRow: some View {
        HStack(spacing: Tv.Dimen.spaceS) {
            ForEach(Tab.allCases) { candidate in
                let isActive = tab == candidate
                Button {
                    tab = candidate
                } label: {
                    Text(candidate.rawValue)
                        .font(.system(size: Tv.Font.cardTitle, weight: .semibold))
                        .padding(.horizontal, Tv.Dimen.spaceL)
                        .padding(.vertical, 10)
                        .background(Capsule().fill(isActive ? Tv.Color.accent : Tv.Color.surface))
                        .foregroundColor(isActive ? Tv.Color.bg : Tv.Color.textSecondary)
                }
                .buttonStyle(.plain)
                .focused($focusedId, equals: "tab-\(candidate.rawValue)")
                .tvFocus(focusedId == "tab-\(candidate.rawValue)")
            }
            Spacer()
        }
    }

    @ViewBuilder
    private var content: some View {
        if loading {
            VStack(spacing: Tv.Dimen.spaceM) {
                Spacer()
                Text("正在从 NAS 加载照片…")
                    .font(.system(size: Tv.Font.body))
                    .foregroundColor(Tv.Color.textSecondary)
                if let loadError {
                    Text(loadError)
                        .font(.system(size: Tv.Font.caption))
                        .foregroundColor(Tv.Color.danger)
                }
                Spacer()
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let loadError, items.isEmpty {
            VStack(spacing: Tv.Dimen.spaceL) {
                Spacer()
                Text("加载失败")
                    .font(.system(size: Tv.Font.section, weight: .semibold))
                    .foregroundColor(Tv.Color.textPrimary)
                Text(loadError)
                    .font(.system(size: Tv.Font.body))
                    .foregroundColor(Tv.Color.textSecondary)
                    .multilineTextAlignment(.center)
                Button("重试") { Task { await load() } }
                    .buttonStyle(.plain)
                    .focused($focusedId, equals: "retry")
                    .tvFocus(focusedId == "retry")
                Spacer()
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            switch tab {
            case .gallery: GalleryTab(groups: yearGroups, focusedId: $focusedId) { items, index in
                    viewer = ViewerSession(items: items, index: index)
                }
            case .albums: AlbumsTab(albums: albums, focusedId: $focusedId) { album in
                    openAlbum = album
                }
            case .settings: SettingsTab(settings: settings, focusedId: $focusedId) {
                    onDisconnect()
                }
            }
        }
    }

    // MARK: - 屏保待机

    /// 每秒检查一次静置时长；到点弹屏保（看图/看相册时不打扰）。
    private var idleTick: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            Color.clear
                .frame(width: 0, height: 0)
                .onChange(of: context.date) { date in
                    guard !screensaverActive, viewer == nil, openAlbum == nil else { return }
                    guard settings.screensaver.enabled else { return }
                    let timeout = max(15, settings.screensaver.idleTimeoutSec)
                    if date.timeIntervalSince(lastInteraction) >= Double(timeout) {
                        screensaverActive = true
                    }
                }
        }
    }

    private func touch() {
        lastInteraction = Date()
    }

    // MARK: - 数据

    private func load() async {
        loading = true
        loadError = nil
        do {
            async let recent = repository.getRecent()
            async let albumList = repository.getAlbums()
            items = try await recent
            yearGroups = makeYearGroups(items)
            albums = (try? await albumList) ?? []
        } catch {
            loadError = FnOsApi.describe(error, access: repository.access)
            // 两步验证中途失效等场景：给出可读提示
            let pending = await repository.hasPendingTwoFactor()
            if pending {
                loadError = "两步验证会话已过期，请断开后重新连接"
            }
        }
        loading = false
    }
}

// MARK: - 图库（按年份分组）

/// 按年份分组的结果（在数据加载时算一次，避免焦点移动时反复重排全库）。
private struct YearGroup: Identifiable {
    let year: Int
    let items: [MediaItem]
    var id: Int { year }
}

private func makeYearGroups(_ items: [MediaItem]) -> [YearGroup] {
    var order: [Int] = []
    var buckets: [Int: [MediaItem]] = [:]
    let calendar = Calendar.current
    for item in items {
        let year = item.dateTaken.map { calendar.component(.year, from: $0) } ?? 0
        if buckets[year] == nil { order.append(year) }
        buckets[year, default: []].append(item)
    }
    return order
        .sorted(by: >)
        .map { YearGroup(year: $0, items: buckets[$0] ?? []) }
}

private struct GalleryTab: View {

    let groups: [YearGroup]
    var focusedId: FocusState<String?>.Binding
    var onOpen: ([MediaItem], Int) -> Void

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: Tv.Dimen.spaceL) {
                ForEach(groups) { group in
                    VStack(alignment: .leading, spacing: Tv.Dimen.spaceM) {
                        Text(group.year == 0 ? "未知年份" : "\(String(group.year)) 年")
                            .font(.system(size: Tv.Font.section, weight: .bold))
                            .foregroundColor(Tv.Color.textPrimary)
                        LazyVGrid(
                            columns: [GridItem(.adaptive(minimum: 240, maximum: 320), spacing: Tv.Dimen.spaceM)],
                            spacing: Tv.Dimen.spaceM,
                        ) {
                            ForEach(group.items) { item in
                                MediaCard(item: item, focusedId: focusedId) {
                                    onOpen(group.items, group.items.firstIndex(where: { $0.id == item.id }) ?? 0)
                                }
                            }
                        }
                    }
                }
            }
            .padding(.bottom, Tv.Dimen.spaceXl)
        }
    }
}

/// 媒体卡片：方图封面 + 标题行；聚焦放大 + 白描边 + 冰蓝辉光。
struct MediaCard: View {

    let item: MediaItem
    var focusedId: FocusState<String?>.Binding
    var onSelect: () -> Void

    private var focusKey: String { "media-\(item.id)" }

    var body: some View {
        Button(action: onSelect) {
            VStack(alignment: .leading, spacing: Tv.Dimen.spaceXs) {
                RemoteImage(url: item.thumbnailUrl)
                    .aspectRatio(1, contentMode: .fill)
                    .frame(maxWidth: .infinity)
                    .frame(height: 240)
                    .clipShape(RoundedRectangle(cornerRadius: Tv.Dimen.radiusM))
                HStack {
                    Text(item.title)
                        .font(.system(size: Tv.Font.cardTitle))
                        .foregroundColor(Tv.Color.textPrimary)
                        .lineLimit(1)
                    Spacer()
                    if item.isVideo {
                        Text("视频")
                            .font(.system(size: Tv.Font.tiny))
                            .foregroundColor(Tv.Color.accentAlt)
                    }
                }
            }
            .padding(6)
        }
        .buttonStyle(.plain)
        .focused(focusedId, equals: focusKey)
        .tvFocus(focusedId.wrappedValue == focusKey)
    }
}

// MARK: - 相册

private struct AlbumsTab: View {

    let albums: [Album]
    var focusedId: FocusState<String?>.Binding
    var onOpen: (Album) -> Void

    var body: some View {
        if albums.isEmpty {
            VStack {
                Spacer()
                Text("还没有相册")
                    .font(.system(size: Tv.Font.body))
                    .foregroundColor(Tv.Color.textSecondary)
                Spacer()
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollView {
                LazyVGrid(
                    columns: [GridItem(.adaptive(minimum: 320, maximum: 420), spacing: Tv.Dimen.spaceM)],
                    spacing: Tv.Dimen.spaceM,
                ) {
                    ForEach(albums) { album in
                        AlbumCard(album: album, focusedId: focusedId) {
                            onOpen(album)
                        }
                    }
                }
                .padding(.bottom, Tv.Dimen.spaceXl)
            }
        }
    }
}

private struct AlbumCard: View {

    let album: Album
    var focusedId: FocusState<String?>.Binding
    var onSelect: () -> Void

    private var focusKey: String { "album-\(album.id)" }

    var body: some View {
        Button(action: onSelect) {
            VStack(alignment: .leading, spacing: Tv.Dimen.spaceXs) {
                RemoteImage(url: album.coverUrl)
                    .frame(height: 180)
                    .frame(maxWidth: .infinity)
                    .clipShape(RoundedRectangle(cornerRadius: Tv.Dimen.radiusM))
                HStack {
                    Text(album.name)
                        .font(.system(size: Tv.Font.cardTitle))
                        .foregroundColor(Tv.Color.textPrimary)
                        .lineLimit(1)
                    Spacer()
                    Text("\(album.itemCount) 项")
                        .font(.system(size: Tv.Font.tiny))
                        .foregroundColor(Tv.Color.textTertiary)
                }
                if !album.subtitle.isEmpty {
                    Text(album.subtitle)
                        .font(.system(size: Tv.Font.tiny))
                        .foregroundColor(Tv.Color.textTertiary)
                }
            }
            .padding(6)
        }
        .buttonStyle(.plain)
        .focused(focusedId, equals: focusKey)
        .tvFocus(focusedId.wrappedValue == focusKey)
    }
}

/// 相册详情：进入时拉取该相册全部媒体（分页），网格展示，可进看图。
struct AlbumDetailView: View {

    let repository: FnOsRepository
    let album: Album
    var onOpenViewer: ([MediaItem], Int) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var media: [MediaItem] = []
    @State private var loading = true
    @State private var error: String?
    @FocusState private var focusedId: String?

    var body: some View {
        ZStack {
            Tv.Color.bg.ignoresSafeArea()
            VStack(alignment: .leading, spacing: Tv.Dimen.spaceM) {
                HStack {
                    Text("\(album.name) · \(media.count) 项")
                        .font(.system(size: Tv.Font.section, weight: .bold))
                        .foregroundColor(Tv.Color.textPrimary)
                    Spacer()
                }
                .padding(.horizontal, Tv.Dimen.screenPadding)
                .padding(.top, Tv.Dimen.spaceL)

                if loading {
                    Spacer()
                    Text("加载相册媒体…")
                        .font(.system(size: Tv.Font.body))
                        .foregroundColor(Tv.Color.textSecondary)
                        .frame(maxWidth: .infinity)
                    Spacer()
                } else if let error {
                    Spacer()
                    Text(error)
                        .font(.system(size: Tv.Font.body))
                        .foregroundColor(Tv.Color.danger)
                        .frame(maxWidth: .infinity)
                    Spacer()
                } else {
                    ScrollView {
                        LazyVGrid(
                            columns: [GridItem(.adaptive(minimum: 240, maximum: 320), spacing: Tv.Dimen.spaceM)],
                            spacing: Tv.Dimen.spaceM,
                        ) {
                            ForEach(Array(media.enumerated()), id: \.element.id) { index, item in
                                MediaCard(item: item, focusedId: $focusedId) {
                                    onOpenViewer(media, index)
                                }
                            }
                        }                        .padding(.horizontal, Tv.Dimen.screenPadding)
                        .padding(.bottom, Tv.Dimen.spaceXl)
                    }
                }
            }
        }
        .onExitCommand { dismiss() }
        .task {
            do {
                media = try await repository.getAlbumMedia(albumId: album.id)
            } catch {
                self.error = FnOsApi.describe(error, access: repository.access)
            }
            loading = false
        }
    }
}

// MARK: - 设置

private struct SettingsTab: View {

    @ObservedObject var settings: AppSettings
    var focusedId: FocusState<String?>.Binding
    var onDisconnect: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Tv.Dimen.spaceL) {
                section("屏保与待机") {
                    row("自动进入屏保", value: settings.screensaver.enabled ? "开" : "关") {
                        settings.screensaver.enabled.toggle()
                    }
                    row("静止多久进入", value: "\(settings.screensaver.idleTimeoutSec / 60) 分钟") {
                        settings.screensaver.idleTimeoutSec = nextIdle(settings.screensaver.idleTimeoutSec)
                    }
                    row("单张照片停留", value: "\(settings.screensaver.photoIntervalSec) 秒") {
                        settings.screensaver.photoIntervalSec = nextInterval(settings.screensaver.photoIntervalSec)
                    }
                }

                section("NAS 连接") {
                    row("地址", value: "https://\(settings.host):\(settings.port)", action: {})
                    row("账号", value: settings.username, action: {})
                    row("断开连接", value: "") {
                        onDisconnect()
                    }
                }
            }
            .padding(.bottom, Tv.Dimen.spaceXl)
        }
    }

    private func section(_ title: String, @ViewBuilder content: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: Tv.Dimen.spaceM) {
            Text(title)
                .font(.system(size: Tv.Font.section, weight: .bold))
                .foregroundColor(Tv.Color.textPrimary)
            content()
        }
    }

    private func row(_ title: String, value: String, action: @escaping () -> Void) -> some View {
        let key = "setting-\(title)"
        return Button {
            action()
        } label: {
            HStack {
                Text(title)
                    .font(.system(size: Tv.Font.body))
                    .foregroundColor(Tv.Color.textPrimary)
                Spacer()
                if !value.isEmpty {
                    Text(value)
                        .font(.system(size: Tv.Font.caption))
                        .foregroundColor(Tv.Color.textSecondary)
                }
            }
            .padding(.horizontal, Tv.Dimen.spaceM)
            .padding(.vertical, 16)
            .background(RoundedRectangle(cornerRadius: Tv.Dimen.radiusM).fill(Tv.Color.surface))
        }
        .buttonStyle(.plain)
        .focused(focusedId, equals: key)
        .tvFocus(focusedId.wrappedValue == key)
    }

    private func nextIdle(_ current: Int) -> Int {
        let options = [60, 300, 900]
        guard let idx = options.firstIndex(of: current) else { return 60 }
        return options[(idx + 1) % options.count]
    }

    private func nextInterval(_ current: Int) -> Int {
        let options = [5, 10, 20]
        guard let idx = options.firstIndex(of: current) else { return 10 }
        return options[(idx + 1) % options.count]
    }
}
