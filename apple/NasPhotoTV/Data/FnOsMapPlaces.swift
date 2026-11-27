import Foundation

@MainActor
final class FnOsMapPlaces {
    private static let path = "/p/api/v1/map"
    private let api: FnOsApi
    private let parseMedia: (Json) -> MediaItem?
    private let cover: (Json) -> String
    private var groups: [Group]?
    private var media: [String: [MediaItem]] = [:]
    private var loading: Task<[Group], Error>?

    init(api: FnOsApi, parseMedia: @escaping (Json) -> MediaItem?, cover: @escaping (Json) -> String) {
        self.api = api
        self.parseMedia = parseMedia
        self.cover = cover
    }

    func albums() async throws -> [Album] {
        try await loadGroups().map(\.album)
    }

    private func loadGroups() async throws -> [Group] {
        if let groups { return groups }
        if let loading { return try await loading.value }
        let task = Task { try await readAlbums() }
        loading = task
        defer { loading = nil }
        let value = try await task.value
        groups = value
        return value
    }

    func photos(in id: String) async throws -> [MediaItem] {
        if let saved = media[id] { return saved }
        let current = try await loadGroups()
        guard let group = current.first(where: { $0.album.id == id }) else {
            throw FnOsError("这个地点已更新，请重新打开")
        }
        var result: [String: MediaItem] = [:]
        for area in group.areas {
            var offset = 0
            var read = Set<String>()
            while read.count < area.count {
                try Task.checkCancellation()
                let params = area.bounds.params + [
                    ("startTime", "1970:01:01 00:00:00"), ("endTime", "2099:12:31 23:59:59"),
                    ("offset", String(offset)), ("limit", "500")
                ]
                let json = try await api.getJson(Self.path + "/photoList", params: params)
                let list = json.array("list")
                let before = read.count
                for item in list.compactMap(parseMedia) where read.insert(item.id).inserted {
                    result[item.id] = item
                }
                guard read.count > before else { throw FnOsError("飞牛地点媒体未读完整，请重试") }
                offset += list.count
            }
        }
        guard result.count >= group.album.itemCount else { throw FnOsError("飞牛地点媒体未读完整，请重试") }
        let sorted = result.values.sorted { ($0.dateTaken ?? .distantPast) > ($1.dateTaken ?? .distantPast) }
        media[id] = sorted
        return sorted
    }

    private func readAlbums() async throws -> [Group] {
        let bounds = try await api.getJson(Self.path + "/preview/mapBoundary")
        let south = max(-90, Double(bounds.string("minLat")) ?? -90)
        let north = min(90, Double(bounds.string("maxLat")) ?? 90)
        let west = max(-180, Double(bounds.string("minLon")) ?? -180)
        let east = min(180, Double(bounds.string("maxLon")) ?? 180)
        guard south <= north, west <= east else { throw FnOsError("飞牛地图范围无效") }
        let index = await PlaceIndex.shared.value
        var points: [PlacePoint: Point] = [:]
        for lat in ranges(south, north, step: 45) {
            for lon in ranges(west, east, step: 60) {
                try Task.checkCancellation()
                let rect = Bounds(south: lat.0, north: lat.1, west: lon.0, east: lon.1)
                let result = try await api.getJson(Self.path + "/previewAllPoint", params: rect.params)
                guard result.raw["list"] is [[String: Any]] else { throw FnOsError("飞牛地图未返回地点") }
                for point in result.array("list") {
                    guard let coordinates = PlacePoint(point), points[coordinates] == nil, point.int("count") > 0 else { continue }
                    let image = point.dict("poster")
                    let fields = [point, image].flatMap(addressFields)
                    let city = value(fields, keys: ["city", "locality", "cityName"])
                    let region = value(fields, keys: ["province", "region", "provinceName"])
                    let country = value(fields, keys: ["countryCode", "country_code"]).uppercased()
                    let place = index.name(point: coordinates, country: country.count == 2 ? country : "",
                                           region: region, city: city)
                    points[coordinates] = Point(coordinates: coordinates, count: point.int("count"), name: place,
                                                coverURL: cover(point), date: image.string("dateTime"))
                }
            }
        }
        let grouped = Dictionary(grouping: points.values, by: { $0.name.id })
        return grouped.compactMap { id, members -> Group? in
            guard let latest = members.max(by: { $0.date < $1.date }) else { return nil }
            let album = Album(id: id, name: latest.name.title, coverUrl: latest.coverURL,
                              itemCount: members.reduce(0) { $0 + $1.count }, subtitle: latest.name.subtitle,
                              type: .smartPlace, isSmart: true)
            let combined = Bounds(members)
            let outside = members.count > 1 ? points.values.filter {
                $0.name.id != id && combined.contains($0.coordinates)
            } : []
            let areas: [Area]
            if outside.isEmpty && !id.hasPrefix("unidentified:") {
                areas = [Area(bounds: combined, count: album.itemCount)]
            } else {
                let cells = Dictionary(grouping: members, by: \.cell)
                areas = cells.values.flatMap { cell in
                    let bounds = Bounds(cell)
                    if outside.contains(where: { bounds.contains($0.coordinates) }) {
                        return cell.map { Area(bounds: Bounds([$0]), count: $0.count) }
                    }
                    return [Area(bounds: bounds, count: cell.reduce(0) { $0 + $1.count })]
                }
            }
            return Group(album: album, areas: areas, isCity: latest.name.isCity, date: latest.date)
        }.sorted {
            if $0.isCity != $1.isCity { return $0.isCity }
            if $0.date != $1.date { return $0.date > $1.date }
            return $0.album.id < $1.album.id
        }
    }

    private func addressFields(_ source: Json) -> [Json] {
        let location = source.dict("location")
        let additional = source.dict("additional").dict("location")
        return [source, location, source.dict("address"), location.dict("address"), additional, additional.dict("address")]
    }

    private func value(_ sources: [Json], keys: [String]) -> String {
        sources.lazy.flatMap { json in
            keys.map { json.string($0).trimmingCharacters(in: .whitespacesAndNewlines) }
        }.first { !$0.isEmpty } ?? ""
    }

    private func ranges(_ minValue: Double, _ maxValue: Double, step: Double) -> [(Double, Double)] {
        var result: [(Double, Double)] = []
        var start = minValue
        repeat {
            let end = min(start + step, maxValue)
            result.append((start, end))
            start = end
        } while start < maxValue
        return result
    }

    private struct Point {
        let coordinates: PlacePoint
        let count: Int
        let name: PlaceName
        let coverURL: String
        let date: String
        var cell: String {
            "\(Int((coordinates.latitude * 10).rounded())):\(Int((coordinates.longitude * 10).rounded()))"
        }
    }

    private struct Group {
        let album: Album
        let areas: [Area]
        let isCity: Bool
        let date: String
    }

    private struct Area { let bounds: Bounds; let count: Int }

    private struct Bounds {
        let south: Double, north: Double, west: Double, east: Double

        init(south: Double, north: Double, west: Double, east: Double) {
            self.south = south; self.north = north; self.west = west; self.east = east
        }

        init(_ points: [Point]) {
            let lat = points.map(\.coordinates.latitude)
            let lon = points.map(\.coordinates.longitude)
            self.init(south: lat.min()!, north: lat.max()!, west: lon.min()!, east: lon.max()!)
        }

        var params: [KeyValue] {
            [("north", String(north)), ("east", String(east)), ("south", String(south)),
             ("west", String(west)), ("lonVert", "0")]
        }

        func contains(_ point: PlacePoint) -> Bool {
            (south...north).contains(point.latitude) && (west...east).contains(point.longitude)
        }
    }
}
