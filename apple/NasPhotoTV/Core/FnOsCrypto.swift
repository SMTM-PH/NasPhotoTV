import Foundation
import CryptoKit
import Security
import CommonCrypto

/// 飞牛登录/签名用到的加密原语（Swift 移植自 Android 端 `FnOsCrypto.kt`）。
///
/// 登录：32 字符 ASCII 作为 AES-256 key，随机 16 字节 IV，PKCS7 填充；
/// AES key 再用服务端下发的 X.509 RSA 公钥做 PKCS#1 v1.5 加密。
enum FnOsCrypto {

    /// 与 Android 端一致的随机 ASCII 字符表（AES key 从这里取）
    private static let keyChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    // MARK: - 哈希

    static func md5Hex(_ input: String) -> String {
        hex(Insecure.MD5.hash(data: Data(input.utf8)))
    }

    static func sha256Hex(_ data: Data) -> String {
        hex(SHA256.hash(data: data))
    }

    private static func hex(_ digest: some Sequence<UInt8>) -> String {
        digest.map { String(format: "%02x", $0) }.joined()
    }

    // MARK: - 随机

    static func randomAscii(_ length: Int) -> String {
        let chars = Array(keyChars)
        return String(randomBytes(length).map { chars[Int($0) % chars.count] })
    }

    static func randomBytes(_ length: Int) -> Data {
        var data = Data(count: length)
        let status = data.withUnsafeMutableBytes {
            SecRandomCopyBytes(kSecRandomDefault, length, $0.baseAddress!)
        }
        precondition(status == errSecSuccess, "SecRandomCopyBytes failed: \(status)")
        return data
    }

    static func randomHex(_ byteCount: Int) -> String {
        randomBytes(byteCount).map { String(format: "%02x", $0) }.joined()
    }

    // MARK: - AES-256-CBC / PKCS7

    static func aesCbcEncrypt(_ plain: Data, asciiKey: String, iv: Data) -> Data {
        let keyData = Data(asciiKey.utf8)
        var out = Data(count: plain.count + kCCBlockSizeAES128)
        var moved = 0
        let status = out.withUnsafeMutableBytes { outPtr in
            plain.withUnsafeBytes { plainPtr in
                keyData.withUnsafeBytes { keyPtr in
                    iv.withUnsafeBytes { ivPtr in
                        CCCrypt(
                            CCOperation(kCCEncrypt),
                            CCAlgorithm(kCCAlgorithmAES128),
                            CCOptions(kCCOptionPKCS7Padding),
                            keyPtr.baseAddress, kCCKeySizeAES256,
                            ivPtr.baseAddress,
                            plainPtr.baseAddress, plain.count,
                            outPtr.baseAddress, outPtr.count,
                            &moved,
                        )
                    }
                }
            }
        }
        precondition(status == kCCSuccess, "CCCrypt failed: \(status)")
        return Data(out.prefix(moved))
    }

    // MARK: - RSA / PKCS#1 v1.5

    /// 对 fnOS 下发的 `-----BEGIN PUBLIC KEY-----`（X.509 SubjectPublicKeyInfo）
    /// 公钥做 RSA/ECB/PKCS1Padding 加密。
    static func rsaEncrypt(spkiPem: String, data: Data) throws -> Data {
        let body = spkiPem
            .replacingOccurrences(of: "-----BEGIN PUBLIC KEY-----", with: "")
            .replacingOccurrences(of: "-----END PUBLIC KEY-----", with: "")
            .filter { !$0.isWhitespace }
        guard let spki = Data(base64Encoded: body) else {
            throw FnOsError("RSA 公钥解析失败（base64）")
        }
        let rsaDer = try subjectPublicKeyInfoToRSADer(spki)
        var error: Unmanaged<CFError>?
        let attrs: [String: Any] = [
            kSecAttrKeyType as String: kSecAttrKeyTypeRSA,
            kSecAttrKeyClass as String: kSecAttrKeyClassPublic,
        ]
        guard let key = SecKeyCreateWithData(rsaDer as CFData, attrs as CFDictionary, &error) else {
            throw FnOsError("RSA 公钥导入失败：\(error?.takeRetainedValue().localizedDescription ?? "?")")
        }
        guard let encrypted = SecKeyCreateEncryptedData(key, .rsaEncryptionPKCS1, data as CFData, &error) else {
            throw FnOsError("RSA 加密失败：\(error?.takeRetainedValue().localizedDescription ?? "?")")
        }
        return encrypted as Data
    }

    /// SPKI ::= SEQUENCE { AlgorithmIdentifier SEQUENCE, subjectPublicKey BIT STRING }
    /// SecKeyCreateWithData 要求 PKCS#1 RSAPublicKey（即 BIT STRING 里去掉未用位字节的部分）。
    private static func subjectPublicKeyInfoToRSADer(_ spki: Data) throws -> Data {
        var outer = Asn1Reader(data: spki)
        let content = try outer.read(expected: 0x30)
        var inner = Asn1Reader(data: content)
        _ = try inner.read(expected: 0x30) // AlgorithmIdentifier
        let bitString = try inner.read(expected: 0x03)
        guard !bitString.isEmpty else { throw FnOsError("RSA 公钥格式异常") }
        return bitString.dropFirst() // 第一个字节是 unused-bits 计数（恒为 0）
    }
}

/// 最小 ASN.1 TLV 读取器：只需要顶层 SEQUENCE / BIT STRING 剥壳。
private struct Asn1Reader {
    let data: Data
    var offset: Int = 0

    /// 读取一个 TLV，返回 value 部分；`expected` 为期望的 tag。
    mutating func read(expected: UInt8) throws -> Data {
        guard offset < data.count else { throw FnOsError("ASN.1 提前结束") }
        var tag = data[data.startIndex + offset]
        offset += 1
        // 高 tag 号（31+）按多字节编码，SPKI 用不到，直接拒绝。
        guard tag & 0x1F != 0x1F else { throw FnOsError("ASN.1 不支持的高 tag") }
        guard offset < data.count else { throw FnOsError("ASN.1 长度缺失") }
        var first = data[data.startIndex + offset]
        offset += 1
        var length = 0
        if first & 0x80 == 0 {
            length = Int(first)
        } else {
            let count = Int(first & 0x7F)
            guard count > 0, count <= 4 else { throw FnOsError("ASN.1 长度非法") }
            length = 0
            for _ in 0..<count {
                guard offset < data.count else { throw FnOsError("ASN.1 长度截断") }
                first = data[data.startIndex + offset]
                offset += 1
                length = (length << 8) | Int(first)
            }
        }
        guard offset + length <= data.count else { throw FnOsError("ASN.1 value 越界") }
        _ = tag
        let value = data.subdata(in: (data.startIndex + offset)..<(data.startIndex + offset + length))
        offset += length
        return value
    }
}

/// 统一的飞牛协议错误（文案直接给 UI 用）。
struct FnOsError: Error, LocalizedError {
    let message: String
    init(_ message: String) { self.message = message }
    var errorDescription: String? { message }
}
