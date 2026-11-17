import Foundation

/// 飞牛相册数据源（Swift 移植自 Android 端 `FnOsMediaRepository.kt`，首期核心闭环）：
///  - 时间线（gallery/getList 按 offset 逐页拉全）；
///  - 相册列表与相册详情（album/photos 分页）；
///  - 屏保照片池；
///  - 服务端缩略图（m）与原图（o）。
///
/// 所有图片/视频地址都是绝对 URL，鉴权由 `FnOsTransport` 统一注入 AccessToken。
final class FnOsRepository {

    let access: FnOsAccess
    private let api: FnOsApi
    private var recentCache: [MediaItem] = []
    private var albumCache: [String: [MediaItem]] = [:]
    private var folderCache: [NasFolder] = []
    private var personCache: [Person] = []
    private var smartAlbumCache: [Album] = []
    /// 非相册类集合（人物/文件夹/智能分类/收藏）的媒体缓存，key 为集合 id
    private var collectionCache: [String: [MediaItem]] = [:]

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

    /// 全库时间线（按拍摄时间倒序）。飞牛服务端单页最多 500 条：以前一次性传
    /// limit=20000 只会拿到最新 500 张，所以按 offset 逐页拉全（分批回填给 UI）。
    func getRecent(limit: Int = 8000) async throws -> [MediaItem] {
        var out: [MediaItem] = []
        out.reserveCapacity(min(limit, 8000))
        var offset = 0
        while out.count < limit && offset < Self.maxGalleryItems {
            let data = try await api.getJson(
                Self.galleryList,
                params: [
                    KeyValue("start_time", "1970:01:01 00:00:00"),
                    KeyValue("end_time", "2099:12:31 23:59:59"),
                    KeyValue("limit", String(Self.galleryPageSize)),
                    KeyValue("offset", String(offset)),
                    KeyValue("mode", "index"),
                ],
            )
            let page = Self.parseMediaList(data, baseUrl: access.baseUrl)
            if page.isEmpty { break }
            out.append(contentsOf: page)
            offset += page.count
            let hasNext = data.bool("hasNext") || page.count >= Self.galleryPageSize
            if !hasNext { break }
        }
        recentCache = out
        return out
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

        let thumb = json.dict("additional").dict("thumbnail")
        let fileName = json.string("fileName")
        let isVideo = json.string("category").lowercased() == "video"

        let mUrl = thumb.string("mUrl")
        let sUrl = thumb.string("sUrl")
        let xsUrl = thumb.string("xsUrl")
        let originalUrl = thumb.string("originalUrl")
        let videoUrl = thumb.string("videoUrl")

        let ext = (fileName.split(separator: ".").last.map(String.init) ?? "").uppercased()
        let isRaw = rawExtensions.contains(ext)

        let thumbUrl = absolute(mUrl.isEmpty ? (sUrl.isEmpty ? xsUrl : sUrl) : mUrl, baseUrl: baseUrl)
        let fullUrl: String
        if isVideo {
            fullUrl = absolute(videoUrl.isEmpty ? "/p/api/v1/stream/v/\(id)" : videoUrl, baseUrl: baseUrl)
        } else if isRaw {
            // RAW 端侧没有解码器：直接看 NAS 生成的高清 JPG 预览（m = 1280px）
            fullUrl = absolute(mUrl.isEmpty ? (sUrl.isEmpty ? originalUrl : sUrl) : mUrl, baseUrl: baseUrl)
        } else {
            fullUrl = absolute(originalUrl.isEmpty ? mUrl : originalUrl, baseUrl: baseUrl)
        }

        let width = json.int("width")
        let height = json.int("height")
        let isLive = json.int("isLive") == 1 && !videoUrl.isEmpty
        // 对齐 substringBeforeLast('.')：没有扩展名的文件名原样保留
        let title = fileName.lastIndex(of: ".").map { String(fileName[..<$0]) } ?? fileName

        return MediaItem(
            id: String(id),
            title: title.isEmpty ? (isVideo ? "未命名视频" : "未命名照片") : title,
            type: isVideo ? .video : .photo,
            url: fullUrl.isEmpty ? thumbUrl : fullUrl,
            thumbnailUrl: thumbUrl.isEmpty ? fullUrl : thumbUrl,
            isLive: isLive,
            liveUrl: isLive ? absolute(videoUrl, baseUrl: baseUrl) : "",
            dateTaken: parseDate(json.string("dateTime").isEmpty ? json.string("photoDateTime") : json.string("dateTime")),
            durationMs: json.int64("durationMs", json.int64("duration")),
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
    private static let maxGalleryItems = 50_000

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
    private static let folderView = "/p/api/v1/photo/folder/view"
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

    /// 磁盘文件夹列表：优先 folder/list（带真实计数），失败兜底 folder/view 的 myFile。
    func getFolders() async throws -> [NasFolder] {
        var arr = (try? await api.getJson(Self.folderList)).map(Self.responseList) ?? []
        if arr.isEmpty {
            arr = (try? await api.getJson(Self.folderView))?.array("myFile") ?? []
        }
        let folders = arr.compactMap { json -> NasFolder? in
            let id = json.string("folderId")
            let path = json.string("folderPath")
            guard !id.isEmpty else { return nil }
            let noTrailing = path.replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
            let last = noTrailing.split(separator: "/").last.map(String.init) ?? ""
            return NasFolder(
                id: id,
                path: path,
                name: last.isEmpty ? path : last,
                photoCount: json.int("photoCount"),
                videoCount: json.int("videoCount"),
            )
        }
        folderCache = folders
        return folders
    }

    func getFolderMedia(folderId: String) async throws -> [MediaItem] {
        let key = "folder:\(folderId)"
        if let cached = collectionCache[key] { return cached }
        let folders = folderCache.isEmpty ? (try await getFolders()) : folderCache
        guard let folder = folders.first(where: { $0.id == folderId }) else { return [] }
        var out: [MediaItem] = []
        var offset = 0
        while offset < Self.maxGalleryItems {
            let data = try await api.getJson(
                Self.folderFiles,
                params: [
                    KeyValue("folderId", folderId),
                    KeyValue("folderPath", folder.path),
                    KeyValue("offset", String(offset)),
                    KeyValue("limit", String(Self.folderPageSize)),
                ],
            )
            let page = Self.parseMediaList(data, baseUrl: access.baseUrl)
            if page.isEmpty { break }
            out.append(contentsOf: page)
            offset += page.count
            let hasNext = data.bool("hasNext") || page.count >= Self.folderPageSize
            if !hasNext { break }
        }
        let result = Self.dedup(out).sorted { Self.newer($0, $1) }
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
