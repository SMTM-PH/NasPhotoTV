import Foundation

typealias KeyValue = (key: String, value: String)

/// `/p` 业务接口的 `authx` 头（Swift 移植自 `FnOsSigner.kt`）。
///
/// sign = md5(SALT_path_nonce_timestamp_md5(参数串)_SECRET)
///  - path 必须带 `/p` 前缀；
///  - timestamp 是**毫秒**；
///  - GET 参数按 key 排序。
///
/// 参数哈希有两种历史规则：RAW（解码后的值）与 ENCODED（URLSearchParams 形态的
/// 编码串）。两种都有服务端在跑，收到业务码 5000 时自动换规则重试。
enum FnOsSigner {

    private static let salt = "NDzZTVxnRKP8Z0jXg1VAMonaG8akvh"
    private static let secret = "EAECCF25-80A6-4666-A7C2-A76904A74AB6"

    enum SignMode {
        case raw
        case encoded

        func next() -> SignMode { self == .raw ? .encoded : .raw }
    }

    /// POST 必须签名实际发送的 JSON 字节串，不能按 GET 查询参数排序 / 编码。
    static func authJsonHeader(path: String, body: String) -> String {
        authPayloadHeader(path: path, payload: body)
    }

    static func authHeader(path: String, params: [KeyValue], mode: SignMode) -> String {
        authPayloadHeader(path: path, payload: payload(params, mode: mode))
    }

    private static func authPayloadHeader(path: String, payload: String) -> String {
        let nonce = String(Int.random(in: 100_000...999_999))
        let timestamp = String(Int64(Date().timeIntervalSince1970 * 1000))
        let sign = FnOsCrypto.md5Hex(
            [salt, path, nonce, timestamp, FnOsCrypto.md5Hex(payload), secret].joined(separator: "_"),
        )
        return "nonce=\(nonce)&timestamp=\(timestamp)&sign=\(sign)"
    }

    /// GET 参数串：按 key 排序后拼接；ENCODED 模式做 Java URLEncoder 形态的编码。
    static func payload(_ params: [KeyValue], mode: SignMode) -> String {
        params
            .sorted { $0.key < $1.key }
            .map { pair in
                switch mode {
                case .raw: return "\(pair.key)=\(pair.value)"
                case .encoded: return "\(encode(pair.key))=\(encode(pair.value))"
                }
            }
            .joined(separator: "&")
    }

    /// 对齐 `java.net.URLEncoder.encode(value, "UTF-8")`：
    /// 保留 `A-Za-z0-9 . - * _`，空格变 `+`，其余按 UTF-8 百分号编码（大写）。
    static func encode(_ value: String) -> String {
        var out = ""
        out.reserveCapacity(value.utf8.count)
        for scalar in value.unicodeScalars {
            switch scalar {
            case "a"..."z", "A"..."Z", "0"..."9", ".", "-", "*", "_":
                out.unicodeScalars.append(scalar)
            case " ":
                out += "+"
            default:
                for byte in String(scalar).utf8 {
                    out += String(format: "%%%02X", byte)
                }
            }
        }
        return out
    }
}
