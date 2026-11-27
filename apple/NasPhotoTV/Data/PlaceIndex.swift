import Foundation

struct PlacePoint: Hashable, Sendable {
    let latitude: Double
    let longitude: Double

    init?(latitude: Double, longitude: Double) {
        guard (-90...90).contains(latitude), (-180...180).contains(longitude) else { return nil }
        self.latitude = latitude
        self.longitude = longitude
    }

    init?(_ json: Json) {
        self.init(latitude: Double(json.string("lat")) ?? .nan,
                  longitude: Double(json.string("lon")) ?? .nan)
    }
}

struct PlaceName: Sendable {
    let id: String
    let title: String
    let subtitle: String
    let isCity: Bool
}

struct PlaceIndex: Sendable {
    private struct City: Sendable {
        let point: PlacePoint
        let name: String
        let alias: String
        let country: String
        let radius: Double
    }

    private struct Boundary: Sendable {
        let code: String
        let name: String
        let region: String
        let bounds: [Double]
        let rings: [[Double]]

        func contains(_ point: PlacePoint) -> Bool {
            let x = point.longitude
            let y = point.latitude
            guard bounds[0] <= x, x <= bounds[2], bounds[1] <= y, y <= bounds[3] else { return false }
            func inside(_ ring: [Double]) -> Bool {
                var result = false
                var previous = ring.count - 2
                for current in stride(from: 0, to: ring.count, by: 2) {
                    let a = ring[current + 1], b = ring[previous + 1]
                    if (a > y) != (b > y), x < ring[current] + (ring[previous] - ring[current]) * (y - a) / (b - a) {
                        result.toggle()
                    }
                    previous = current
                }
                return result
            }
            return inside(rings[0]) && !rings.dropFirst().contains(where: inside)
        }
    }

    private let cities: [City]
    private let boundaries: [Boundary]

    // Shared with Android via Xcode's resource phase. Coordinate lookup stays on the device.
    static let shared = Task.detached(priority: .utility) { PlaceIndex() }

    private init() {
        cities = Self.rows("place-cities").compactMap { fields in
            guard fields.count == 6, let lat = Double(fields[0]), let lon = Double(fields[1]),
                  let point = PlacePoint(latitude: lat, longitude: lon), let population = Int(fields[5]) else { return nil }
            let radius: Double
            switch population {
            case 5_000_000...: radius = 55
            case 1_000_000...: radius = 40
            case 100_000...: radius = 29
            case 10_000...: radius = 19
            default: radius = 12
            }
            return City(point: point, name: String(fields[2]), alias: String(fields[3]), country: String(fields[4]), radius: radius)
        }.sorted { $0.point.latitude < $1.point.latitude }
        boundaries = Self.rows("place-boundaries-cn").compactMap { fields in
            guard fields.count == 5 else { return nil }
            let bounds = fields[3].split(separator: ",").compactMap { Double($0) }
            let rings = fields[4].split(separator: "|").map { $0.split(separator: ",").compactMap { Double($0) } }
            guard bounds.count == 4, !rings.isEmpty, rings.allSatisfy({ $0.count >= 6 && $0.count.isMultiple(of: 2) }) else { return nil }
            return Boundary(code: String(fields[0]), name: String(fields[1]), region: String(fields[2]), bounds: bounds, rings: rings)
        }
    }

    private static func rows(_ name: String) -> [[Substring]] {
        guard let url = Bundle.main.url(forResource: name, withExtension: "tsv"),
              let text = try? String(contentsOf: url, encoding: .utf8) else { return [] }
        return text.split(whereSeparator: \.isNewline)
            .filter { !$0.hasPrefix("#") }
            .map { $0.split(separator: "\t", omittingEmptySubsequences: false) }
    }

    func name(point: PlacePoint, country: String, region: String, city: String) -> PlaceName {
        if point.latitude != 0 || point.longitude != 0 {
            if let name = boundary(point, country: country, hint: city) { return name }
            if let candidate = nearest(point, country: country, hint: city) {
                if let name = boundary(candidate.point, country: candidate.country, hint: candidate.name) { return name }
                return PlaceName(id: "city:\(candidate.country):\(candidate.point.latitude):\(candidate.point.longitude)",
                                 title: candidate.name,
                                 subtitle: subtitle(candidate.name, region: region, country: candidate.country), isCity: true)
            }
        }
        if !city.isEmpty || !region.isEmpty {
            let title = city.isEmpty ? region : city
            return PlaceName(id: "named:\(country):\(region):\(normalize(title))", title: title,
                             subtitle: subtitle(title, region: region, country: country), isCity: !city.isEmpty)
        }
        return PlaceName(id: "unidentified:\(country)", title: "未识别城市",
                         subtitle: subtitle("", region: "", country: country), isCity: false)
    }

    private func boundary(_ point: PlacePoint, country: String, hint: String) -> PlaceName? {
        guard country.isEmpty || country == "CN",
              let match = boundaries.first(where: { $0.contains(point) }),
              hint.isEmpty || normalize(hint) == normalize(match.name) else { return nil }
        return PlaceName(id: "city:CN:\(match.code)", title: match.name,
                         subtitle: subtitle(match.name, region: match.region, country: "CN"), isCity: true)
    }

    private func nearest(_ point: PlacePoint, country: String, hint: String) -> City? {
        let search = 75.0 / 111.2
        let longitudeScale = cos(point.latitude * .pi / 180)
        let hint = normalize(hint)
        var low = 0, high = cities.count
        while low < high {
            let mid = (low + high) / 2
            if cities[mid].point.latitude < point.latitude - search { low = mid + 1 } else { high = mid }
        }
        var match: City?
        var nearest = Double.infinity
        for city in cities[low...] {
            if city.point.latitude > point.latitude + search { break }
            guard country.isEmpty || country == city.country else { continue }
            guard hint.isEmpty || normalize(city.name) == hint || normalize(city.alias) == hint else { continue }
            let north = (city.point.latitude - point.latitude) * 111.2
            let delta = abs(city.point.longitude - point.longitude)
            let east = min(delta, 360 - delta) * longitudeScale * 111.2
            let distance = hypot(north, east)
            guard distance <= (hint.isEmpty ? city.radius : 75), distance < nearest else { continue }
            nearest = distance
            match = city
        }
        return match
    }

    private func normalize(_ name: String) -> String {
        let value = name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return value.hasSuffix("市") ? String(value.dropLast()) : value
    }

    private func subtitle(_ title: String, region: String, country: String) -> String {
        let countryName = Locale(identifier: "zh-Hans").localizedString(forRegionCode: country) ?? ""
        return [region, countryName].enumerated().compactMap { index, label in
            label.isEmpty || normalize(label) == normalize(title) || (index == 1 && label == region) ? nil : label
        }.joined(separator: " · ")
    }
}
