import Foundation
import Combine

/// 本地收藏（对齐 Android `FavoritesStore`）：fnOS 没有收藏增删接口，
/// 星标来自 NAS 的 `isCollect`（只读）+ 本地 Set<媒体 id>，按账号分区持久化。
final class FavoritesStore: ObservableObject {

    static let shared = FavoritesStore()

    private let defaults = UserDefaults.standard
    private let legacyKey = "nas_photo_tv_favorites.ids"
    private var scopeKey: String

    @Published private(set) var ids: Set<String> = []

    private init() {
        scopeKey = "nas_photo_tv_favorites.ids"
        ids = Set(defaults.stringArray(forKey: legacyKey) ?? [])
    }

    /// 切到某个账号分区：目标分区键完全不存在时，把旧单账号键迁移过去（一次）。
    func load(accountId: String) {
        let key = accountId.isEmpty
            ? legacyKey
            : "nas_photo_tv_favorites.ids_\(accountId)"
        scopeKey = key
        if defaults.object(forKey: key) == nil {
            let migrated = Set(defaults.stringArray(forKey: legacyKey) ?? [])
            defaults.set(Array(migrated), forKey: key)
        }
        ids = Set(defaults.stringArray(forKey: key) ?? [])
    }

    func isFavorite(_ id: String) -> Bool { ids.contains(id) }

    func toggle(_ id: String) {
        if ids.contains(id) { ids.remove(id) } else { ids.insert(id) }
        defaults.set(Array(ids), forKey: scopeKey)
    }

    /// 换账号/断开连接时清空内存快照（隐私），持久化数据保留在各自分区。
    func clear() {
        ids = []
        scopeKey = legacyKey
    }
}

extension Array where Element == MediaItem {
    /// NAS 侧 isCollect ∪ 本地星标（对齐 Android `favoriteItems()`）。
    func favoriteItems(_ favorites: FavoritesStore) -> [MediaItem] {
        filter { $0.starred || favorites.isFavorite($0.id) }
    }
}
