import Foundation

/// 对齐 org.json 的防御式读取语义：缺字段返回空串/0 而不是抛错，
/// 数字/字符串互相兼容（`optString` 会把数字转成字符串）。
struct Json {
    let raw: [String: Any]

    init(_ raw: Any?) {
        self.raw = raw as? [String: Any] ?? [:]
    }

    var isEmpty: Bool { raw.isEmpty }
    var keys: [String] { raw.keys.sorted() }

    func string(_ key: String) -> String {
        switch raw[key] {
        case let s as String: return s
        case let n as NSNumber: return n.stringValue
        default: return ""
        }
    }

    func int(_ key: String, _ def: Int = 0) -> Int {
        (raw[key] as? NSNumber)?.intValue ?? def
    }

    func int64(_ key: String, _ def: Int64 = 0) -> Int64 {
        (raw[key] as? NSNumber)?.int64Value ?? def
    }

    /// fnOS 的布尔字段并不总是 JSON 布尔：不同版本会回 true / "true" / 1。
    func bool(_ key: String) -> Bool {
        switch raw[key] {
        case let b as Bool: return b
        case let n as NSNumber: return n.intValue != 0
        case let s as String: return s.caseInsensitiveCompare("true") == .orderedSame || s == "1"
        default: return false
        }
    }

    func dict(_ key: String) -> Json {
        Json(raw[key])
    }

    func array(_ key: String) -> [Json] {
        Json.list(raw[key])
    }

    static func list(_ value: Any?) -> [Json] {
        (value as? [[String: Any]] ?? []).map(Json.init)
    }
}
