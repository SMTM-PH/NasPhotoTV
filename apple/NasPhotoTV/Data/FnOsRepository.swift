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
    private var folderCache: [NasFolderInfo] = []

    struct NasFolderInfo: Identifiable, Hashable {
        let id: String
        let path: String
        let name: String
        let photoCount: Int
        let videoCount: Int
    }

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
