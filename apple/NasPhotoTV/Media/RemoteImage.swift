import SwiftUI
import UIKit

/// 带鉴权的远程图片加载（对应 Android 端 Coil + FnOsTransport 注入链路）：
///  - 请求自动带 `AccessToken`（重登后新 token 对排队中的请求同样生效）；
///  - 自签证书的 TOFU 信任由 `FnOsTransport.session` 的 delegate 统一处理；
///  - 内存缓存按 URL 维度（缩略图与原图 URL 本身不同）。
struct RemoteImage: View {

    let url: String
    var contentMode: ContentMode = .fill

    @StateObject private var loader = MediaImageLoader()

    var body: some View {
        ZStack {
            // 加载中 / 失败 / 空地址：稳定的深色渐变占位，避免 10-foot UI 出现白块
            LinearGradient(
                colors: [Color(hex: 0x12283A), Color(hex: 0x0B1D2C)],
                startPoint: .topLeading, endPoint: .bottomTrailing,
            )
            if let image = loader.image {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: contentMode)
                    .transition(.opacity)
            }
        }
        .task(id: url) {
            await loader.load(url)
        }
    }
}

/// 单个 URL 的异步图片加载器。
final class MediaImageLoader: ObservableObject {

    @Published var image: UIImage?

    private static let cache: NSCache<NSString, UIImage> = {
        let cache = NSCache<NSString, UIImage>()
        cache.totalCostLimit = 256 * 1024 * 1024
        return cache
    }()

    private static let session = FnOsTransport.shared.session

    func load(_ urlString: String) async {
        if urlString.isEmpty { return }
        if let cached = Self.cache.object(forKey: urlString as NSString) {
            image = cached
            return
        }
        guard let url = URL(string: urlString) else { return }
        var request = URLRequest(url: url)
        request.timeoutInterval = 30
        if let token = FnOsTransport.shared.token {
            request.setValue(token, forHTTPHeaderField: "AccessToken")
        }
        do {
            let (data, response) = try await Self.session.data(for: request)
            guard let http = response as? HTTPURLResponse,
                  (200..<300).contains(http.statusCode),
                  let decoded = UIImage(data: data) else { return }
            let cost = decoded.size.width * decoded.size.height * decoded.scale * decoded.scale * 4
            Self.cache.setObject(decoded, forKey: urlString as NSString, cost: Int(cost))
            image = decoded
        } catch {
            // 失败保持占位图；网格刷新或重进后会重试
        }
    }
}
