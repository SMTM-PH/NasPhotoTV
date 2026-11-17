import SwiftUI

/// 首页：对标 Android 媒体库的信息架构 —— 图库（按年份）/ 相册 / 文件夹 /
/// 人物 / 智能分类 / 收藏 / 设置，静置自动进入屏保。
struct HomeView: View {

    let repository: FnOsRepository
    var onDisconnect: () -> Void

    enum Tab: String, CaseIterable, Identifiable {
        case gallery = "图库"
        case albums = "相册"
        case folders = "文件夹"
        case people = "人物"
        case smart = "智能分类"
        case favorites = "收藏"
        case settings = "设置"
        var id: String { rawValue }
    }

    /// 打开看图的会话（items + 起点）
    struct ViewerSession: Identifiable {
        let id = UUID()
        let items: [MediaItem]
        var index: Int
    }

    /// 统一的集合卡片（相册 / 文件夹 / 人物 / 智能分类）
    struct Collection: Identifiable, Hashable {
        enum Kind: String { case album, folder, person, smart }
        let kind: Kind
        let rawId: String
        let name: String
        let coverUrl: String
        let itemCount: Int
        let subtitle: String
        let isPerson: Bool
        var id: String { "\(kind.rawValue)-\(rawId)" }
    }

    @ObservedObject private var settings = AppSettings.shared
    @ObservedObject private var favorites = FavoritesStore.shared

    @State private var tab: Tab = .gallery
    @State private var items: [MediaItem] = []
    @State private var yearGroups: [YearGroup] = []
    @State private var albums: [Album] = []
    @State private var folders: [NasFolder] = []
    @State private var people: [Person] = []
    @State private var smartAlbums: [Album] = []
    @State private var favoriteItems: [MediaItem] = []
    @State private var screensaverPhotos: [MediaItem] = []
    @State private var searchText = ""
    @State private var searchResults: [MediaItem] = []
    @State private var loading = true
    @State private var collectionLoading = false
    @State private var loadError: String?
    @State private var collectionError: String?
    @State private var viewer: ViewerSession?
    @State private var openCollection: Collection?
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
        .fullScreenCover(item: $openCollection) { collection in
            CollectionDetailView(repository: repository, collection: collection) { media, index, title in
                openCollection = nil
                viewer = ViewerSession(items: media, index: index)
                _ = title
            }
        }
        .fullScreenCover(isPresented: $screensaverActive) {
            ScreensaverView(photos: screensaverPhotos, settings: settings) {
                screensaverActive = false
                touch()
            }
        }
        .onAppear { favorites.load(accountId: settings.username) }
        .onChange(of: focusedId) { _ in touch() }
        .onChange(of: tab) { _ in touch() }
        .task { await load() }
        .task(id: tab) { await loadTab(tab) }
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
        return "\(settings.host) · \(items.count) 项"
    }

    private var navRow: some View {
        HStack(spacing: Tv.Dimen.spaceS) {
            ForEach(Tab.allCases) { candidate in
                let isActive = tab == candidate
                Button { tab = candidate } label: {
                    Text(candidate.rawValue)
                        .font(.system(size: Tv.Font.caption, weight: .semibold))
                        .padding(.horizontal, Tv.Dimen.spaceM)
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
            centered(Text("正在从 NAS 加载照片…").font(.system(size: Tv.Font.body)).foregroundColor(Tv.Color.textSecondary))
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
            case .gallery:
                GalleryTab(
                    groups: yearGroups,
                    searchText: $searchText,
                    results: searchResults,
                    focusedId: $focusedId,
                ) { media, index in
                    viewer = ViewerSession(items: media, index: index)
                }
                .onChange(of: searchText) { query in
                    Task { searchResults = (try? await repository.search(query)) ?? [] }
                }
            case .albums:
                CollectionsTab(collections: albumCollections, focusedId: $focusedId, emptyText: "还没有相册") { openCollection = $0 }
            case .folders:
                collectionContent(collections: folderCollections, emptyText: "还没有文件夹")
            case .people:
                collectionContent(collections: personCollections, emptyText: "还没有人物分组")
            case .smart:
                collectionContent(collections: smartCollections, emptyText: "还没有智能分类")
            case .favorites:
                FavoritesTab(items: favoriteItems, focusedId: $focusedId) { media, index in
                    viewer = ViewerSession(items: media, index: index)
                }
            case .settings:
                SettingsTab(settings: settings, focusedId: $focusedId, onDisconnect: onDisconnect)
            }
        }
    }

    @ViewBuilder
    private func collectionContent(collections: [Collection], emptyText: String) -> some View {
        if collectionLoading {
            centered(Text("加载中…").foregroundColor(Tv.Color.textSecondary))
        } else if let collectionError {
            centered(Text(collectionError).foregroundColor(Tv.Color.danger))
        } else {
            CollectionsTab(collections: collections, focusedId: $focusedId, emptyText: emptyText) { openCollection = $0 }
        }
    }

    private func centered<Content: View>(_ content: Content) -> some View {
        VStack { Spacer(); content; Spacer() }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - 集合映射

    private var albumCollections: [Collection] {
        albums.map {
            Collection(kind: .album, rawId: $0.id, name: $0.name, coverUrl: $0.coverUrl,
                       itemCount: $0.itemCount, subtitle: $0.subtitle, isPerson: false)
        }
    }

    private var folderCollections: [Collection] {
        folders.map {
            Collection(kind: .folder, rawId: $0.id, name: $0.name, coverUrl: "",
                       itemCount: $0.photoCount + $0.videoCount,
                       subtitle: $0.path, isPerson: false)
        }
    }

    private var personCollections: [Collection] {
        people.map {
            Collection(kind: .person, rawId: $0.id, name: $0.displayName, coverUrl: $0.coverUrl,
                       itemCount: $0.itemCount, subtitle: "", isPerson: true)
        }
    }

    private var smartCollections: [Collection] {
        smartAlbums.map {
            Collection(kind: .smart, rawId: $0.id, name: $0.name, coverUrl: $0.coverUrl,
                       itemCount: $0.itemCount, subtitle: $0.subtitle, isPerson: false)
        }
    }

    // MARK: - 屏保待机

    private var idleTick: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            Color.clear
                .frame(width: 0, height: 0)
                .onChange(of: context.date) { date in
                    guard !screensaverActive, viewer == nil, openCollection == nil else { return }
                    guard settings.screensaver.enabled else { return }
                    let timeout = max(15, settings.screensaver.idleTimeoutSec)
                    if date.timeIntervalSince(lastInteraction) >= Double(timeout) {
                        screensaverActive = true
                    }
                }
        }
    }

    private func touch() { lastInteraction = Date() }

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
            // 屏保优先用人物照片池；失败回退最近照片
            screensaverPhotos = (try? await repository.getPeoplePhotos()) ?? []
            if screensaverPhotos.isEmpty { screensaverPhotos = repository.screensaverPhotos }
        } catch {
            loadError = FnOsApi.describe(error, access: repository.access)
            let pending = await repository.hasPendingTwoFactor()
            if pending { loadError = "两步验证会话已过期，请断开后重新连接" }
        }
        loading = false
    }

    /// 按需加载集合（对齐 Android「点开才拉」的策略，避免启动拉全部 AI 分类）。
    private func loadTab(_ tab: Tab) async {
        collectionError = nil
        collectionLoading = true
        defer { collectionLoading = false }
        do {
            switch tab {
            case .folders: folders = (try? await repository.getFolders()) ?? []
            case .people: people = (try? await repository.getPeople()) ?? []
            case .smart: smartAlbums = (try? await repository.getSmartAlbums()) ?? []
            case .favorites: favoriteItems = (try? await repository.getFavorites(favorites)) ?? []
            default: break
            }
        }
    }
}

// MARK: - 图库（按年份分组 + 搜索）

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
    return order.sorted(by: >).map { YearGroup(year: $0, items: buckets[$0] ?? []) }
}

private struct GalleryTab: View {

    let groups: [YearGroup]
    @Binding var searchText: String
    let results: [MediaItem]
    var focusedId: FocusState<String?>.Binding
    var onOpen: ([MediaItem], Int) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Tv.Dimen.spaceM) {
            TextField("搜索文件名 / 日期 / 格式", text: $searchText)
                .font(.system(size: Tv.Font.body))
                .frame(width: 420)
                .padding(.horizontal, Tv.Dimen.spaceM)
                .padding(.vertical, 10)
                .background(RoundedRectangle(cornerRadius: Tv.Dimen.radiusM).fill(Tv.Color.surface))
                .foregroundColor(Tv.Color.textPrimary)

            if !searchText.isEmpty {
                Text("搜索结果 · \(results.count) 项")
                    .font(.system(size: Tv.Font.section, weight: .bold))
                    .foregroundColor(Tv.Color.textPrimary)
                MediaGrid(items: results, focusedId: focusedId) { items, index in onOpen(items, index) }
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: Tv.Dimen.spaceL) {
                        ForEach(groups) { group in
                            VStack(alignment: .leading, spacing: Tv.Dimen.spaceM) {
                                Text(group.year == 0 ? "未知年份" : "\(String(group.year)) 年")
                                    .font(.system(size: Tv.Font.section, weight: .bold))
                                    .foregroundColor(Tv.Color.textPrimary)
                                MediaGrid(items: group.items, focusedId: focusedId) { items, index in onOpen(items, index) }
                            }
                        }
                    }
                    .padding(.bottom, Tv.Dimen.spaceXl)
                }
            }
        }
    }
}

private struct MediaGrid: View {
    let items: [MediaItem]
    var focusedId: FocusState<String?>.Binding
    var onOpen: ([MediaItem], Int) -> Void

    var body: some View {
        LazyVGrid(
            columns: [GridItem(.adaptive(minimum: 240, maximum: 320), spacing: Tv.Dimen.spaceM)],
            spacing: Tv.Dimen.spaceM,
        ) {
            ForEach(items) { item in
                MediaCard(item: item, focusedId: focusedId) {
                    onOpen(items, items.firstIndex(where: { $0.id == item.id }) ?? 0)
                }
            }
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
                        Text("视频").font(.system(size: Tv.Font.tiny)).foregroundColor(Tv.Color.accentAlt)
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

// MARK: - 集合网格（相册 / 文件夹 / 人物 / 智能分类）

private struct CollectionsTab: View {

    let collections: [HomeView.Collection]
    var focusedId: FocusState<String?>.Binding
    let emptyText: String
    var onOpen: (HomeView.Collection) -> Void

    var body: some View {
        if collections.isEmpty {
            VStack { Spacer(); Text(emptyText).font(.system(size: Tv.Font.body)).foregroundColor(Tv.Color.textSecondary); Spacer() }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollView {
                LazyVGrid(
                    columns: [GridItem(.adaptive(minimum: 320, maximum: 420), spacing: Tv.Dimen.spaceM)],
                    spacing: Tv.Dimen.spaceM,
                ) {
                    ForEach(collections) { collection in
                        CollectionCard(collection: collection, focusedId: focusedId) { onOpen(collection) }
                    }
                }
                .padding(.bottom, Tv.Dimen.spaceXl)
            }
        }
    }
}

private struct CollectionCard: View {

    let collection: HomeView.Collection
    var focusedId: FocusState<String?>.Binding
    var onSelect: () -> Void

    private var focusKey: String { "collection-\(collection.id)" }

    var body: some View {
        Button(action: onSelect) {
            VStack(alignment: .leading, spacing: Tv.Dimen.spaceXs) {
                if collection.isPerson {
                    HStack {
                        Spacer()
                        ZStack {
                            Circle().fill(Tv.Color.surfaceHigh)
                            if collection.coverUrl.isEmpty {
                                Text(String(collection.name.prefix(1)))
                                    .font(.system(size: 64, weight: .semibold))
                                    .foregroundColor(Tv.Color.accent)
                            } else {
                                RemoteImage(url: collection.coverUrl)
                                    .clipShape(Circle())
                            }
                        }
                        .frame(width: 180, height: 180)
                        Spacer()
                    }
                } else {
                    RemoteImage(url: collection.coverUrl)
                        .frame(height: 180)
                        .frame(maxWidth: .infinity)
                        .clipShape(RoundedRectangle(cornerRadius: Tv.Dimen.radiusM))
                }
                HStack {
                    Text(collection.name)
                        .font(.system(size: Tv.Font.cardTitle))
                        .foregroundColor(Tv.Color.textPrimary)
                        .lineLimit(1)
                    Spacer()
                    Text("\(collection.itemCount) 项")
                        .font(.system(size: Tv.Font.tiny))
                        .foregroundColor(Tv.Color.textTertiary)
                }
                if !collection.subtitle.isEmpty {
                    Text(collection.subtitle)
                        .font(.system(size: Tv.Font.tiny))
                        .foregroundColor(Tv.Color.textTertiary)
                        .lineLimit(1)
                }
            }
            .padding(6)
        }
        .buttonStyle(.plain)
        .focused(focusedId, equals: focusKey)
        .tvFocus(focusedId.wrappedValue == focusKey)
    }
}

/// 集合详情：进入时按需拉取媒体（分页），按年份分组，可进看图。
struct CollectionDetailView: View {

    let repository: FnOsRepository
    let collection: HomeView.Collection
    var onOpenViewer: ([MediaItem], Int, String) -> Void

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
                    Text("\(collection.name) · \(media.count) 项")
                        .font(.system(size: Tv.Font.section, weight: .bold))
                        .foregroundColor(Tv.Color.textPrimary)
                    Spacer()
                }
                .padding(.horizontal, Tv.Dimen.screenPadding)
                .padding(.top, Tv.Dimen.spaceL)

                if loading {
                    Spacer()
                    Text("加载媒体…").font(.system(size: Tv.Font.body)).foregroundColor(Tv.Color.textSecondary)
                        .frame(maxWidth: .infinity)
                    Spacer()
                } else if let error {
                    Spacer()
                    Text(error).font(.system(size: Tv.Font.body)).foregroundColor(Tv.Color.danger)
                        .frame(maxWidth: .infinity)
                    Spacer()
                } else {
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: Tv.Dimen.spaceL) {
                            ForEach(makeYearGroups(media)) { group in
                                VStack(alignment: .leading, spacing: Tv.Dimen.spaceM) {
                                    Text(group.year == 0 ? "未知年份" : "\(String(group.year)) 年")
                                        .font(.system(size: Tv.Font.section, weight: .bold))
                                        .foregroundColor(Tv.Color.textPrimary)
                                    MediaGrid(items: group.items, focusedId: $focusedId) { items, index in
                                        onOpenViewer(items, index, collection.name)
                                    }
                                }
                            }
                        }
                        .padding(.horizontal, Tv.Dimen.screenPadding)
                        .padding(.bottom, Tv.Dimen.spaceXl)
                    }
                }
            }
        }
        .onExitCommand { dismiss() }
        .task {
            do {
                switch collection.kind {
                case .album: media = try await repository.getAlbumMedia(albumId: collection.rawId)
                case .folder: media = try await repository.getFolderMedia(folderId: collection.rawId)
                case .person: media = try await repository.getPersonPhotos(personId: collection.rawId)
                case .smart: media = try await repository.getSmartAlbumMedia(albumId: collection.rawId)
                }
            } catch {
                self.error = FnOsApi.describe(error, access: repository.access)
            }
            loading = false
        }
    }
}

// MARK: - 收藏

private struct FavoritesTab: View {
    let items: [MediaItem]
    var focusedId: FocusState<String?>.Binding
    var onOpen: ([MediaItem], Int) -> Void

    var body: some View {
        if items.isEmpty {
            VStack { Spacer(); Text("还没有收藏 · 在看图页按「收藏」添加").font(.system(size: Tv.Font.body)).foregroundColor(Tv.Color.textSecondary); Spacer() }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollView { MediaGrid(items: items, focusedId: focusedId) { media, index in onOpen(media, index) }
                .padding(.bottom, Tv.Dimen.spaceXl) }
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
                    row("自动进入屏保", value: settings.screensaver.enabled ? "开" : "关") { settings.screensaver.enabled.toggle() }
                    row("静止多久进入", value: settings.formatIdleTimeout()) { settings.cycleIdleTimeout() }
                    row("单张照片停留", value: "\(settings.screensaver.photoIntervalSec) 秒") { settings.cyclePhotoInterval() }
                    row("显示日期", value: settings.screensaver.showDate ? "开" : "关") { settings.screensaver.showDate.toggle() }
                    row("夜间调暗", value: settings.screensaver.nightDim ? "开" : "关") { settings.screensaver.nightDim.toggle() }
                    row("Ken Burns 防烧屏", value: settings.screensaver.kenBurns ? "开" : "关") { settings.screensaver.kenBurns.toggle() }
                    row("时钟样式", value: Self.clockLabel(settings.screensaver.clockStyle)) { settings.screensaver.clockStyle = Self.nextClock(settings.screensaver.clockStyle) }
                }

                section("播放设置") {
                    row("播完自动播下一个", value: settings.playback.autoPlayNext ? "开" : "关") { settings.playback.autoPlayNext.toggle() }
                    row("单条循环", value: settings.playback.loopMode ? "开" : "关") { settings.playback.loopMode.toggle() }
                }

                section("背景音乐") {
                    row("幻灯片 / 屏保时播放", value: settings.screensaver.musicEnabled ? "开" : "关") { settings.screensaver.musicEnabled.toggle() }
                    row("音量", value: "\(Int(settings.screensaver.musicVolume * 100))%") {
                        settings.screensaver.musicVolume = settings.screensaver.musicVolume >= 0.9 ? 0.3 : settings.screensaver.musicVolume + 0.2
                    }
                    row("上传背景音乐", value: MusicStore.shared.latestName ?? "未上传") { openMusicPanel = true }
                }

                section("NAS 连接") {
                    row("地址", value: "https://\(settings.host):\(settings.port)", action: {})
                    row("账号", value: settings.username, action: {})
                    row("断开连接", value: "") { onDisconnect() }
                }

                section("关于") {
                    row("版本", value: "1.0.0 (Apple TV)", action: {})
                    row("数据来源", value: "飞牛 fnOS · 局域网直连原片", action: {})
                }
            }
            .padding(.bottom, Tv.Dimen.spaceXl)
        }
        .sheet(isPresented: $openMusicPanel) { MusicPanel() }
    }

    @State private var openMusicPanel = false

    private func section(_ title: String, @ViewBuilder content: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: Tv.Dimen.spaceM) {
            Text(title).font(.system(size: Tv.Font.section, weight: .bold)).foregroundColor(Tv.Color.textPrimary)
            content()
        }
    }

    private func row(_ title: String, value: String, action: @escaping () -> Void) -> some View {
        let key = "setting-\(title)"
        return Button(action: action) {
            HStack {
                Text(title).font(.system(size: Tv.Font.body)).foregroundColor(Tv.Color.textPrimary)
                Spacer()
                if !value.isEmpty {
                    Text(value).font(.system(size: Tv.Font.caption)).foregroundColor(Tv.Color.textSecondary)
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

    private static let clockStyles = ["cinema", "flip", "thin", "minimal", "dateFocus"]
    private static let clockLabels = ["影院", "翻页", "纤细", "极简", "日期"]

    private static func clockLabel(_ style: String) -> String {
        guard let idx = clockStyles.firstIndex(of: style) else { return clockLabels[0] }
        return clockLabels[idx]
    }

    private static func nextClock(_ style: String) -> String {
        guard let idx = clockStyles.firstIndex(of: style) else { return clockStyles[0] }
        return clockStyles[(idx + 1) % clockStyles.count]
    }
}
