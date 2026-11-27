import Foundation

/// 飞牛相册数据源（Swift 移植自 Android 端 `FnOsMediaRepository.kt`，首期核心闭环）：
///  - 时间线（按年分页，必要时按天补齐）；
///  - 相册列表与相册详情（album/photos 分页）；
///  - 屏保照片池；
///  - 服务端缩略图（m）与原图（o）。
///
/// 所有图片/视频地址都是绝对 URL，鉴权由 `FnOsTransport` 统一注入 AccessToken。
@MainActor
final class FnOsRepository {

    let access: FnOsAccess
    private let api: FnOsApi
    private var recentCache: [MediaItem] = []
    private var albumCache: [String: [MediaItem]] = [:]
    private var personCache: [Person] = []
    private var smartAlbumCache: [Album] = []
    /// 非相册类集合（人物/文件夹/智能分类/收藏）的媒体缓存，key 为集合 id
    private var collectionCache: [String: [MediaItem]] = [:]

    private lazy var mapPlaces = makeMapPlaces()

    private func makeMapPlaces() -> FnOsMapPlaces {
        let base = access.baseUrl
        return FnOsMapPlaces(api: api, parseMedia: { Self.parseMedia($0, baseUrl: base) },
                            cover: {
            let photo = Self.parseMedia($0.dict("poster"), baseUrl: base)
            return photo.flatMap { $0.thumbnailUrl.isEmpty ? nil : $0.thumbnailUrl }
                ?? Self.categoryCover($0, baseUrl: base)
        })
    }

    func savePhonePhoto(_ photo: PhonePhoto) async throws {
        try await api.savePhonePhoto(photo)
        mapPlaces = makeMapPlaces()
        albumCache.removeAll()
        collectionCache.removeAll()
    }

    func getPlaces() async throws -> [Album] { try await mapPlaces.albums() }
    func getPlaceMedia(id: String) async throws -> [MediaItem] { try await mapPlaces.photos(in: id) }

    init(access: FnOsAccess) {
        self.access = access
        self.api = FnOsApi(access: access)
    }

    /// 屏保照片池：最近的照片（不含视频），实况照片会带动态片段
    var screensaverPhotos: [MediaItem] {
        recentCache.filter { $0.type == .photo }
    }

    func close() {
        // FnOsApi 是 actor：fire-and-forget 关闭（取消心跳、断开 WebSocket）
        Task { await api.close() }
    }

    // MARK: - 连接

    func probe() async -> FnOsProbeResult {
        await api.testConnection()
    }

    /// 账号开启两步验证时，密码登录只算「半程」：调用这里提交 6 位动态码，
    /// 同一个会话（含未关闭的 WebSocket）随即转为已登录。
    func verifyTwoFactor(_ code: String) async throws {
        try await api.verifyTwoFactor(code: code)
    }

    func hasPendingTwoFactor() async -> Bool {
        await api.hasPendingTwoFactor()
    }

    func cancelTwoFactor() async {
        await api.cancelTwoFactor()
    }

    func stats() async throws -> FnOsStats {
        try await api.stat()
    }

    // MARK: - 时间线 / 相册

    /// 先按年读取，跨日分页不完整时按时间线逐日补齐。
    func getRecent(limit: Int = .max) async throws -> [MediaItem] {
        guard limit > 0 else { return [] }
        if limit != .max {
            return try await galleryRange("1970:01:01", "2099:12:31", limit: limit)
        }
        let timeline = try await api.getJson("/p/api/v1/gallery/timeline")
        var days: [String: Int] = [:]
        for day in try Self.requiredList(timeline, context: "图库时间线") {
            try Task.checkCancellation()
            let year = day.int("year")
            let month = day.int("month")
            let date = day.int("day")
            let count = day.int("itemCount", -1)
            let calendar = Calendar(identifier: .gregorian)
            let components = DateComponents(year: year, month: month, day: date)
            guard (1...9999).contains(year), components.isValidDate(in: calendar), count >= 0 else {
                throw FnOsError("图库时间线包含无效日期或数量")
            }
            if count > 0 {
                let key = String(format: "%04d:%02d:%02d", year, month, date)
                days[key] = max(days[key, default: 0], count)
            }
        }

        let years = Dictionary(grouping: days.keys.sorted(by: >)) { String($0.prefix(4)) }
        var library = LinkedHashMap()
        for year in years.keys.sorted(by: >) {
            guard let dates = years[year] else { continue }
            let expected = dates.reduce(0) { $0 + days[$1, default: 0] }
            do {
                let items = try await galleryRange("\(year):01:01", "\(year):12:31", expected: expected)
                items.forEach { library.putIfAbsent($0) }
            } catch is IncompleteRange {
                for date in dates {
                    do {
                        let items = try await galleryRange(date, date, expected: days[date])
                        items.forEach { library.putIfAbsent($0) }
                    } catch is IncompleteRange {
                        throw FnOsError("图库未读取完整（\(date)），请刷新后重试")
                    }
                }
            }
        }
        guard library.count >= days.values.reduce(0, +) else {
            throw FnOsError("图库未读取完整，请刷新后重试")
        }
        let result = library.values.sorted(by: Self.newer)
        recentCache = result
        return result
    }

    private struct IncompleteRange: Error {}

    private func galleryRange(
        _ start: String, _ end: String, expected: Int? = nil, limit: Int = .max
    ) async throws -> [MediaItem] {
        var collected = LinkedHashMap()
        var offset = 0
        while collected.count < limit {
            try Task.checkCancellation()
            let data = try await api.getJson(Self.galleryList, params: [
                KeyValue("start_time", "\(start) 00:00:00"),
                KeyValue("end_time", "\(end) 23:59:59"),
                KeyValue("limit", String(Self.galleryPageSize)),
                KeyValue("offset", String(offset)),
                KeyValue("mode", "index"),
            ])
            let page = try Self.requiredList(data, context: "图库")
            if page.isEmpty { break }
            let before = collected.count
            for json in page {
                guard let item = Self.parseMedia(json, baseUrl: access.baseUrl) else { continue }
                if expected != nil {
                    let date = Self.isoDate(item.dateTaken).replacingOccurrences(of: "-", with: ":")
                    guard date >= start, date <= end else { throw IncompleteRange() }
                }
                collected.putIfAbsent(item)
            }
            offset += page.count
            if let expected, collected.count >= expected { break }
            if before == collected.count {
                if expected != nil { throw IncompleteRange() }
                throw FnOsError("图库未正确翻页，请刷新后重试")
            }
        }
        if let expected, collected.count < expected { throw IncompleteRange() }
        return Array(collected.values.prefix(limit))
    }

    private static func requiredList(_ data: Json, context: String) throws -> [Json] {
        guard let list = data.raw["list"] as? [[String: Any]] else {
            throw FnOsError("\(context)响应缺少有效列表")
        }
        return list.map(Json.init)
    }

    /// 自己的 + 收到分享的相册列表。
    func getAlbums() async throws -> [Album] {
        let own = try await api.getJson(
            Self.albumList,
            params: [
                KeyValue("sort_direction", "desc"),
                KeyValue("sort_by", "date_time"),
                KeyValue("offset", "0"),
                KeyValue("limit", "300"),
            ],
        )
        // 共享相册是独立路由；失败不影响自己的相册
        let shared = try? await api.getJson(
            Self.sharedAlbums,
            params: [
                KeyValue("offset", "0"),
                KeyValue("limit", "300"),
                KeyValue("sort_by", "share_mod_time"),
                KeyValue("sort_direction", "desc"),
            ],
        )
        var seen = Set<String>()
        var out: [Album] = []
        for album in Self.parseAlbums(own, baseUrl: access.baseUrl) + Self.parseAlbums(shared, baseUrl: access.baseUrl) {
            if seen.insert(album.id).inserted { out.append(album) }
        }
        return out
    }

    /// 某个相册下的全部媒体（按拍摄时间倒序，逐页拉全）。
    func getAlbumMedia(albumId: String) async throws -> [MediaItem] {
        if let cached = albumCache[albumId] { return cached }
        var collected = LinkedHashMap()
        var offset = 0
        var total = -1
        while true {
            let data = try await api.getJson(
                Self.albumPhotos,
                params: [
                    KeyValue("album_id", albumId),
                    KeyValue("sort_by", "date_time"),
                    KeyValue("sort_direction", "desc"),
                    KeyValue("offset", String(offset)),
                    KeyValue("limit", String(Self.albumPageSize)),
                ],
            )
            // 只信任「> 0」的 total：部分版本对 album/photos 不返回 total 或默认 0，
            // 若把 0 当成终点会在第一页就误停。
            if total <= 0 {
                let reported = data.int("total", -1)
                if reported > 0 { total = reported }
            }
            let page = Self.parseMediaList(data, baseUrl: access.baseUrl).map { item in
                var copy = item
                copy.albumId = albumId
                return copy
            }
            let before = collected.count
            for item in page { collected.putIfAbsent(item) }
            offset += page.count
            if page.isEmpty { break }
            if total > 0 && collected.count >= total { break }
            if collected.count == before { break } // 整页都是重复项：边界异常
            if offset >= Self.maxAlbumItems { break }
        }
        let result = collected.values
        albumCache[albumId] = result
        return result
    }

    // MARK: - 解析

    private struct LinkedHashMap {
        private(set) var values: [MediaItem] = []
        private var ids = Set<String>()
        var count: Int { values.count }

        mutating func putIfAbsent(_ item: MediaItem) {
            if ids.insert(item.id).inserted { values.append(item) }
        }
    }

    private static func parseMediaList(_ data: Json, baseUrl: String) -> [MediaItem] {
        data.array("list").compactMap { parseMedia($0, baseUrl: baseUrl) }
    }

    private static let rawExtensions: Set<String> = [
        "CR2", "CR3", "NEF", "ARW", "DNG", "RAF", "ORF", "RW2", "PEF", "SRW", "3FR", "ERF", "KDC", "MOS", "MRW", "X3F",
    ]

    private static func parseMedia(_ json: Json, baseUrl: String) -> MediaItem? {
        let id = json.int64("id")
        guard id > 0 else { return nil }

        let fileName = firstString(json, "fileName", "name")
        let ext = fileName.lastIndex(of: ".").map { fileName[fileName.index(after: $0)...].uppercased() } ?? ""
        let isVideo = ["category", "fileType"].contains { json.string($0).lowercased() == "video" }
        let sources = [json.dict("additional").dict("thumbnail"), json.dict("thumbnail"), json.dict("thumb"), json]
        func field(_ keys: String...) -> String {
            for key in keys {
                for source in sources {
                    let value = firstString(source, key)
                    if !value.isEmpty { return value }
                }
            }
            return ""
        }

        let uuid = firstString(json, "photoUUID", "uuid", "session")
        let medium = field("mUrl", "m")
        let small = field("sUrl", "s")
        let original = field("originalUrl", "o", "originUrl")
        let video = field("videoUrl", "video")
        let videoStream = video.isEmpty ? "/p/api/v1/stream/v/\(id)" : video
        let thumb = [medium, small, field("xsUrl", "xs"), field("xxsUrl", "xxs"),
                     photoStream(id: id, size: "s", uuid: uuid)].first { !$0.isEmpty } ?? ""
        let full = isVideo ? videoStream : (
            rawExtensions.contains(ext)
                ? [medium, small, original, photoStream(id: id, size: "m", uuid: uuid)]
                : [original, medium, photoStream(id: id, size: "o", uuid: uuid)]
        ).first { !$0.isEmpty } ?? ""

        let width = json.int("width")
        let height = json.int("height")
        let isLive = !isVideo && json.bool("isLive")
        // 对齐 substringBeforeLast('.')：没有扩展名的文件名原样保留
        let title = fileName.lastIndex(of: ".").map { String(fileName[..<$0]) } ?? fileName

        return MediaItem(
            id: String(id),
            title: title.isEmpty ? (isVideo ? "未命名视频" : "未命名照片") : title,
            type: isVideo ? .video : .photo,
            url: absolute(full.isEmpty ? thumb : full, baseUrl: baseUrl),
            thumbnailUrl: absolute(thumb.isEmpty ? full : thumb, baseUrl: baseUrl),
            isLive: isLive,
            liveUrl: isLive ? absolute(videoStream, baseUrl: baseUrl) : "",
            dateTaken: parseDate(firstString(json, "dateTime", "photoDateTime", "createTime", "mtime")),
            durationMs: isVideo ? Self.durationMs(json) : 0,
            sizeBytes: json.int64("fileSize"),
            resolution: width > 0 && height > 0 ? "\(width)x\(height)" : "",
            albumId: "",
            starred: json.int("isCollect") == 1,
            ext: ext,
        )
    }

    private static func parseAlbums(_ data: Json?, baseUrl: String) -> [Album] {
        guard let data else { return [] }
        return data.array("list").compactMap { json in
            let id = json.int64("albumId")
            guard id > 0 else { return nil }
            let owner = json.string("ownerName")
            return Album(
                id: String(id),
                name: json.string("albumName").isEmpty ? "未命名相册" : json.string("albumName"),
                coverUrl: absolute(
                    json.string("posterImgUrl").isEmpty ? json.string("posterUrl") : json.string("posterImgUrl"),
                    baseUrl: baseUrl,
                ),
                itemCount: json.int("photoCount") + json.int("videoCount"),
                subtitle: owner.isEmpty ? "" : "来自 \(owner)",
            )
        }
    }

    private static func parseDate(_ raw: String) -> Date? {
        let trimmed = raw.trimmingCharacters(in: .whitespaces)
        guard trimmed.count >= 10 else { return nil }
        let head = String(trimmed.prefix(10)).replacingOccurrences(of: ":", with: "-")
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.date(from: head)
    }

    private static func durationMs(_ json: Json) -> Int64 {
        let millis = json.int64("durationMs")
        if millis > 0 { return millis }
        let seconds = json.int64("mediaDuration")
        let raw = seconds > 0 ? seconds : max(0, json.int64("duration"))
        return raw > 86_400 ? raw : raw * 1_000
    }

    private static func photoStream(id: Int64, size: String, uuid: String) -> String {
        guard id > 0, !uuid.isEmpty else { return "" }
        return "/p/api/v1/stream/p/t/\(id)/\(size)/\(uuid)"
    }

    private static func absolute(_ relative: String, baseUrl: String) -> String {
        if relative.isEmpty { return "" }
        if relative.hasPrefix("http://") || relative.hasPrefix("https://") { return relative }
        return baseUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/")) + "/" + relative.dropPrefix("/")
    }

    // MARK: - 常量

    /// 相册分页的单页大小：实测服务端单页最多 500，沿用该值即可少走往返。
    private static let albumPageSize = 500
    /// 单个相册的分页兜底上限，防止服务端异常（如 total 缺失且永不返回空页）时无限拉取。
    private static let maxAlbumItems = 20_000
    private static let galleryPageSize = 500

    private static let galleryList = "/p/api/v1/gallery/getList"
    private static let albumList = "/p/api/v1/album/list"
    private static let albumPhotos = "/p/api/v1/album/photos"
    private static let sharedAlbums = "/p/api/v1/album_grant/list_to_me"
}

private extension String {
    func dropPrefix(_ prefix: String) -> Substring {
        hasPrefix(prefix) ? dropFirst(prefix.count) : self[...]
    }
}

// MARK: - 人物 / 文件夹 / 智能分类 / 搜索（对标 Android FnOsMediaRepository）

extension FnOsRepository {

    // MARK: 常量

    private static let personList = "/p/api/v1/ai-person/list"
    private static let personTimeline = "/p/api/v1/ai-person/photoLibrary/timeLine"
    private static let personLibraryList = "/p/api/v1/ai-person/photoLibrary/list"
    private static let folderList = "/p/api/v1/photo/folder/list"
    private static let folderChildren = "/p/api/v1/folder_view/getFolderList"
    private static let folderFiles = "/p/api/v1/folder_view/getFileList"
    private static let smartCategories = "/p/api/v1/ai-smart-rec/categories"
    private static let magicSearch = "/p/api/v1/magic-search/do"
    private static let galleryGetOne = "/p/api/v1/gallery/getOne"

    private static let personPageSize = 200
    private static let folderPageSize = 200
    private static let maxPersonItems = 20_000

    private static let listKeys = ["list", "items", "records", "rows", "data", "result"]

    // MARK: 人物

    /// 人物列表（一次全量；过滤隐藏项，按照片数降序）。
    func getPeople() async throws -> [Person] {
        let data = try await api.getJson(
            Self.personList,
            params: [
                KeyValue("getAll", "true"),
                KeyValue("limit", "-1"),
                KeyValue("orderBy", "0"),
            ],
        )
        let people = Self.responseList(data).compactMap { json -> Person? in
            let id = json.int64("id")
            guard id > 0, json.int("isHide") != 1 else { return nil }
            let name = Self.firstString(json, "name", "personName", "tagName")
            let faceId = json.int64("faceId")
            var cover = Self.firstString(json, "coverUrl", "cover", "avatarUrl", "posterUrl")
            if cover.isEmpty && faceId > 0 {
                cover = "/p/api/v1/stream/face/\(faceId)"
            }
            return Person(
                id: String(id),
                name: name,
                coverUrl: Self.absolute(cover, baseUrl: access.baseUrl),
                itemCount: json.int("itemCount", json.int("photoCount")),
            )
        }.sorted { $0.itemCount > $1.itemCount }
        personCache = people
        return people
    }

    /// 人物详情：遍历服务端按天时间线，逐日翻页拉全（对齐 Android 1.2.3 修复）。
    func getPersonPhotos(personId: String, limit: Int = 20_000) async throws -> [MediaItem] {
        let key = "person:\(personId)"
        if let cached = collectionCache[key] { return cached }
        let timeline = (try? await api.getJson(Self.personTimeline, params: [KeyValue("id", personId)])) ?? Json([:])
        var out: [MediaItem] = []
        for day in Self.responseList(timeline) {
            let y = day.int("year"), m = day.int("month"), d = day.int("day")
            guard y > 0, m > 0, d > 0 else { continue }
            let ymd = String(format: "%04d:%02d:%02d", y, m, d)
            let dayCount = day.int("itemCount")
            var offset = 0
            var gotInDay = 0
            while out.count < limit && (dayCount <= 0 || gotInDay < dayCount) {
                guard let data = try? await api.getJson(Self.personLibraryList, params: Self.personDayParams(personId, ymd, offset: offset)) else { break }
                let page = Self.parseMediaList(data, baseUrl: access.baseUrl).filter { $0.type == .photo }
                if page.isEmpty { break }
                out.append(contentsOf: page)
                offset += page.count
                gotInDay += page.count
                if page.count < Self.personPageSize { break }
            }
            if out.count >= limit { break }
        }
        let result = Array(Self.dedup(out).sorted { Self.newer($0, $1) }.prefix(limit))
        collectionCache[key] = result
        return result
    }

    /// 屏保人物照片池：取前 8 个人物、每人最近 3 天、每天 60 张（轻量，不日内翻页）。
    func getPeoplePhotos(limit: Int = 120) async throws -> [MediaItem] {
        let people = personCache.isEmpty ? (try await getPeople()) : personCache
        guard !people.isEmpty else { return [] }
        var out: [MediaItem] = []
        for person in people.prefix(8) {
            let shots = (try? await fetchPersonCoverPhotos(personId: person.id, maxDays: 3, perDay: 60)) ?? []
            out.append(contentsOf: shots)
        }
        return Array(Self.dedup(out).sorted { Self.newer($0, $1) }.prefix(limit))
    }

    private func fetchPersonCoverPhotos(personId: String, maxDays: Int, perDay: Int) async throws -> [MediaItem] {
        let timeline = try await api.getJson(Self.personTimeline, params: [KeyValue("id", personId)])
        var out: [MediaItem] = []
        for day in Self.responseList(timeline).prefix(maxDays) {
            let y = day.int("year"), m = day.int("month"), d = day.int("day")
            guard y > 0, m > 0, d > 0 else { continue }
            let ymd = String(format: "%04d:%02d:%02d", y, m, d)
            guard !ymd.hasPrefix("0000") else { continue }
            var params = Self.personDayParams(personId, ymd, offset: 0)
            params = params.filter { $0.key != "limit" } + [KeyValue("limit", String(perDay))]
            guard let data = try? await api.getJson(Self.personLibraryList, params: params) else { continue }
            out.append(contentsOf: Self.parseMediaList(data, baseUrl: access.baseUrl).filter { $0.type == .photo })
        }
        return out
    }

    /// 人物单日查询：camelCase 与 snake_case 时间参数都要带，日期必须冒号格式。
    private static func personDayParams(_ personId: String, _ ymd: String, offset: Int) -> [KeyValue] {
        [
            KeyValue("album_id", personId),
            KeyValue("personId", personId),
            KeyValue("startTime", "\(ymd) 00:00:00"),
            KeyValue("start_time", "\(ymd) 00:00:00"),
            KeyValue("endTime", "\(ymd) 23:59:59"),
            KeyValue("end_time", "\(ymd) 23:59:59"),
            KeyValue("limit", String(personPageSize)),
            KeyValue("offset", String(offset)),
        ]
    }

    // MARK: 文件夹

    /// 只加载当前层级。媒体列表与子目录列表由 fnOS 的独立接口提供。
    func getFolders(parentPath: String? = nil) async throws -> [NasFolder] {
        var params = [KeyValue("desc", "false"), KeyValue("orderBy", "2")]
        if let parentPath { params.append(KeyValue("folderPath", parentPath)) }
        let endpoint = parentPath == nil ? Self.folderList : Self.folderChildren
        let data = try await api.getJson(endpoint, params: params)
        let list = try Self.requiredList(data, context: "文件夹")
        guard data.int("total") <= list.count else { throw FnOsError("子目录列表未读取完整") }

        var seen = Set<String>()
        let folders: [NasFolder] = try list.compactMap { json in
            let rawPath = json.string(parentPath == nil ? "folderPath" : "path")
            let path = rawPath == "/" ? rawPath : rawPath.replacingOccurrences(
                of: "/+$", with: "", options: .regularExpression
            )
            guard path.hasPrefix("/") else { throw FnOsError("文件夹缺少完整路径") }
            if let parentPath {
                let prefix = parentPath == "/" ? "/" : parentPath + "/"
                guard path.hasPrefix(prefix), path != parentPath else {
                    throw FnOsError("NAS 返回了无效子目录")
                }
            }
            guard seen.insert(path).inserted else { return nil }
            let name = json.string("name")
            return NasFolder(
                id: path,
                path: path,
                name: name.isEmpty ? (path.split(separator: "/").last.map(String.init) ?? path) : name,
                photoCount: json.int("photoCount"),
                videoCount: json.int("videoCount")
            )
        }
        return try await attachFolderCovers(folders, parentPath: parentPath)
    }

    private func attachFolderCovers(_ folders: [NasFolder], parentPath: String?) async throws -> [NasFolder] {
        let groups = Dictionary(grouping: folders) { folder in
            parentPath ?? (folder.path as NSString).deletingLastPathComponent
        }
        var covers: [String: String] = [:]
        for (parent, children) in groups {
            for offset in stride(from: 0, to: children.count, by: 50) {
                try Task.checkCancellation()
                let batch = Array(children.dropFirst(offset).prefix(50))
                let names = batch.map { ($0.path as NSString).lastPathComponent }
                let data: Json
                do {
                    data = try await api.postJson("/p/api/v1/folder_view/cover", body: Json([
                        "currentPath": parent, "dirNames": names,
                    ]))
                } catch {
                    try Task.checkCancellation()
                    continue
                }
                let entries = data.raw["list"] == nil ? data.array("covers") : data.array("list")
                let knownPaths = Set(batch.map(\.path))
                for (index, entry) in entries.enumerated() {
                    let raw = Self.firstString(entry, "dirPath", "folderPath", "path", "dirName")
                    let path: String
                    if raw.isEmpty, entries.count == batch.count {
                        path = batch[index].path
                    } else if raw.hasPrefix("/") {
                        path = raw.replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
                    } else {
                        path = (parent as NSString).appendingPathComponent(raw)
                    }
                    guard knownPaths.contains(path) else { continue }
                    let posters = ["cover", "defaultCover", "poster", "posters"].flatMap { entry.array($0) }
                    for poster in posters {
                        let id = Int64(Self.firstString(poster, "id", "photoId")) ?? 0
                        let uuid = Self.firstString(poster, "uuid", "photoUUID", "session")
                        let stream = Self.photoStream(id: id, size: "xs", uuid: uuid)
                        if !stream.isEmpty {
                            covers[path] = Self.absolute(stream, baseUrl: access.baseUrl)
                            break
                        }
                    }
                }
            }
        }
        try Task.checkCancellation()
        return folders.map { folder in
            var result = folder
            result.coverUrl = covers[folder.path] ?? ""
            return result
        }
    }

    func getFolderMedia(folderId path: String) async throws -> [MediaItem] {
        guard path.hasPrefix("/") else { throw FnOsError("文件夹路径无效") }
        let key = "folder:\(path)"
        if let cached = collectionCache[key] { return cached }

        var collected = LinkedHashMap()
        var rawIDs = Set<String>()
        var offset = 0
        var total = 0
        while true {
            try Task.checkCancellation()
            let data = try await api.getJson(Self.folderFiles, params: [
                KeyValue("folderPath", path),
                KeyValue("desc", "true"),
                KeyValue("orderBy", "0"),
                KeyValue("offset", String(offset)),
                KeyValue("limit", String(Self.folderPageSize)),
            ])
            let page = try Self.requiredList(data, context: "目录媒体")
            total = max(total, data.int("total"))
            if page.isEmpty {
                guard offset >= total else { throw FnOsError("目录媒体未读取完整") }
                break
            }
            let before = rawIDs.count
            for json in page {
                let id = json.string("id")
                rawIDs.insert(id.isEmpty ? String(describing: json.raw) : id)
                if let item = Self.parseMedia(json, baseUrl: access.baseUrl) {
                    collected.putIfAbsent(item)
                }
            }
            guard rawIDs.count > before else { throw FnOsError("目录接口重复返回同一页") }
            offset += page.count
            if total > 0, offset >= total { break }
        }
        let result = collected.values.sorted(by: Self.newer)
        collectionCache[key] = result
        return result
    }

    // MARK: 智能分类（场景事物）

    func getSmartAlbums() async throws -> [Album] {
        let data = try await api.getJson(
            Self.smartCategories,
            params: [KeyValue("limit", "-1"), KeyValue("mainCategory", "scene")],
        )
        var seen = Set<String>()
        var albums: [Album] = []
        for json in Self.responseList(data) {
            let name = Self.firstString(json, "name")
            guard !name.isEmpty else { continue }
            let id = "smart:scene:" + FnOsSigner.encode(name)
            guard seen.insert(id).inserted else { continue }
            albums.append(Album(
                id: id,
                name: name,
                coverUrl: Self.categoryCover(json, baseUrl: access.baseUrl),
                itemCount: Self.categoryCount(json),
                subtitle: "场景事物",
                type: .smartCategory,
                isSmart: true,
            ))
        }
        smartAlbumCache = albums
        return albums
    }

    /// 语义搜索结果：旧版 fnOS 只回 id + session，需要补全媒体元数据。
    func getSmartAlbumMedia(albumId: String) async throws -> [MediaItem] {
        if let cached = collectionCache[albumId] { return cached }
        let prefix = "smart:scene:"
        guard albumId.hasPrefix(prefix) else { return [] }
        let name = Self.javaURLDecode(String(albumId.dropFirst(prefix.count)))
        guard !name.isEmpty else { return [] }
        let body: [String: Any] = ["keyword": name, "antiFilters": []]
        let data = try await api.postJson(Self.magicSearch, body: Json(body))
        var seen = Set<String>()
        var out: [MediaItem] = []
        for json in Self.responseList(data) {
            let id = Self.firstString(json, "id", "photoId")
            guard let numeric = Int64(id), numeric > 0, seen.insert(id).inserted else { continue }
            let normalized = try await normalizeSearchItem(json, id: id)
            guard var item = Self.parseMedia(Json(normalized), baseUrl: access.baseUrl) else { continue }
            item.albumId = albumId
            out.append(item)
        }
        collectionCache[albumId] = out
        return out
    }

    private func normalizeSearchItem(_ json: Json, id: String) async throws -> [String: Any] {
        var obj = json.raw
        obj["id"] = id
        obj = Self.ensureThumbnail(obj)
        if !Self.hasPlaybackMetadata(obj) {
            if let detail = try? await api.getJson(Self.galleryGetOne, params: [KeyValue("id", id)]) {
                obj = Self.ensureThumbnail(Self.mergeMissing(target: obj, fallback: detail.raw))
            }
        }
        guard Self.hasPlaybackMetadata(obj) else {
            throw FnOsError("飞牛未返回完整媒体信息（id \(id)）")
        }
        let uuid = Self.firstString(Json(obj), "uuid", "photoUUID", "session")
        if !uuid.isEmpty {
            var additional = (obj["additional"] as? [String: Any]) ?? [:]
            var thumb = (additional["thumbnail"] as? [String: Any]) ?? [:]
            for (key, size) in [("mUrl", "m"), ("sUrl", "s"), ("xsUrl", "s")] {
                if Self.firstString(Json(thumb), key).isEmpty {
                    thumb[key] = "/p/api/v1/stream/p/t/\(id)/\(size)/\(uuid)"
                }
            }
            additional["thumbnail"] = thumb
            obj["additional"] = additional
        }
        return obj
    }

    private static func ensureThumbnail(_ obj: [String: Any]) -> [String: Any] {
        var out = obj
        var additional = (out["additional"] as? [String: Any]) ?? [:]
        var thumb = (additional["thumbnail"] as? [String: Any]) ?? (out["thumbnail"] as? [String: Any]) ?? [:]
        additional["thumbnail"] = thumb
        out["additional"] = additional
        return out
    }

    private static func hasPlaybackMetadata(_ obj: [String: Any]) -> Bool {
        let json = Json(obj)
        let fileName = json.string("fileName")
        guard !fileName.isEmpty else { return false }
        let thumb = json.dict("additional").dict("thumbnail")
        let category = json.string("category").lowercased()
        let videoUrl = thumb.string("videoUrl")
        let ext = (fileName.split(separator: ".").last.map(String.init) ?? "").uppercased()
        switch category {
        case "video":
            return !videoUrl.isEmpty
        case "photo":
            let hasImage = !thumb.string("originalUrl").isEmpty
                || (rawExtensions.contains(ext) && !thumb.string("mUrl").isEmpty)
            let liveOk = json.int("isLive") != 1 || !videoUrl.isEmpty
            return hasImage && liveOk
        default:
            return false
        }
    }

    /// 递归合并：仅当 target 缺键 / NSNull / 空字符串时写入，已有值（含签名 URL）不动。
    private static func mergeMissing(target: [String: Any], fallback: [String: Any]) -> [String: Any] {
        var out = target
        for (key, value) in fallback {
            guard let existing = out[key] else { out[key] = value; continue }
            if existing is NSNull { out[key] = value; continue }
            if let s = existing as? String, s.isEmpty { out[key] = value; continue }
            if let d = existing as? [String: Any], let f = value as? [String: Any] {
                out[key] = mergeMissing(target: d, fallback: f)
            }
        }
        return out
    }

    private static func categoryCover(_ json: Json, baseUrl: String) -> String {
        let direct = firstString(json, "cover", "thumb", "posterUrl", "imageUrl")
        if !direct.isEmpty { return absolute(direct, baseUrl: baseUrl) }
        let poster = json.dict("poster")
        let pid = firstString(poster, "photoId", "id")
        let uuid = firstString(poster, "uuid", "photoUUID", "session")
        if !pid.isEmpty && !uuid.isEmpty {
            return absolute("/p/api/v1/stream/p/t/\(pid)/m/\(uuid)", baseUrl: baseUrl)
        }
        return ""
    }

    private static func categoryCount(_ json: Json) -> Int {
        for key in ["itemCount", "item_count", "count", "total", "photoCount", "photo_count"] {
            if let n = Int(json.string(key)), n >= 0 { return n }
        }
        return 0
    }

    /// 收藏页聚合：NAS isCollect ∪ 本地星标，按 id 去重（对齐 Android LibraryData）。
    func getFavorites(_ favorites: FavoritesStore) async throws -> [MediaItem] {
        let pool = recentCache.isEmpty ? (try await getRecent()) : recentCache
        var seen = Set<String>()
        return (pool + albumCache.values.flatMap { $0 } + collectionCache.values.flatMap { $0 })
            .favoriteItems(favorites)
            .filter { seen.insert($0.id).inserted }
            .sorted { Self.newer($0, $1) }
    }

    // MARK: 搜索（纯客户端过滤）

    func search(_ query: String) async throws -> [MediaItem] {
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        guard !q.isEmpty else { return [] }
        let pool = recentCache.isEmpty ? (try await getRecent(limit: 300)) : recentCache
        return pool.filter { item in
            item.title.lowercased().contains(q)
                || Self.isoDate(item.dateTaken).contains(q)
                || item.ext.lowercased() == q
        }
    }

    // MARK: 小工具

    private static func responseList(_ data: Json) -> [Json] {
        for key in listKeys {
            let arr = data.array(key)
            if !arr.isEmpty { return arr }
        }
        return []
    }

    private static func firstString(_ json: Json, _ keys: String...) -> String {
        for key in keys {
            let value = json.string(key).trimmingCharacters(in: .whitespaces)
            if !value.isEmpty && value != "null" { return value }
        }
        return ""
    }

    private static func javaURLDecode(_ value: String) -> String {
        var out = ""
        var bytes: [UInt8] = []
        func flush() {
            if !bytes.isEmpty { out += String(decoding: bytes, as: UTF8.self); bytes = [] }
        }
        let chars = Array(value)
        var i = 0
        while i < chars.count {
            let c = chars[i]
            if c == "+" {
                flush(); out += " "; i += 1
            } else if c == "%" && i + 2 < chars.count,
                      let hex = UInt8(String(chars[(i + 1)...(i + 2)]), radix: 16) {
                bytes.append(hex); i += 3
            } else {
                flush(); out.append(c); i += 1
            }
        }
        flush()
        return out
    }

    private static func dedup(_ items: [MediaItem]) -> [MediaItem] {
        var seen = Set<String>()
        return items.filter { seen.insert($0.id).inserted }
    }

    private static func newer(_ a: MediaItem, _ b: MediaItem) -> Bool {
        (a.dateTaken ?? .distantPast) > (b.dateTaken ?? .distantPast)
    }

    private static func isoDate(_ date: Date?) -> String {
        guard let date else { return "" }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: date)
    }
}
