import SwiftUI
import CoreImage.CIFilterBuiltins
import UIKit

struct PhonePhotoPanel: View {
    @Environment(\.dismiss) private var dismiss
    @FocusState private var doneFocused: Bool
    @StateObject private var model: PhotoTransferModel
    var onImported: () -> Void

    init(repository: FnOsRepository, onImported: @escaping () -> Void) {
        _model = StateObject(wrappedValue: PhotoTransferModel(repository: repository))
        self.onImported = onImported
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Tv.Dimen.spaceL) {
            Text("手机扫码传图")
                .font(.system(size: Tv.Font.display, weight: .bold))
            Text("手机和电视连接同一局域网。扫码选照片，原图保存到飞牛相册。")
                .font(.system(size: Tv.Font.body))
                .foregroundStyle(Tv.Color.textSecondary)
            HStack(spacing: Tv.Dimen.spaceXl) {
                VStack(alignment: .leading, spacing: Tv.Dimen.spaceM) {
                    if let url = model.urls.first, let image = qrImage(url) {
                        Image(uiImage: image).interpolation(.none)
                            .resizable().scaledToFit().frame(width: 280, height: 280)
                            .padding(Tv.Dimen.spaceM).background(.white)
                            .accessibilityLabel("手机上传二维码")
                        Text(url).font(.system(size: Tv.Font.caption)).lineLimit(2)
                            .foregroundStyle(Tv.Color.accent)
                    } else {
                        Text("未找到局域网地址，请检查电视的网络连接。")
                            .foregroundStyle(Tv.Color.danger)
                    }
                }
                VStack(alignment: .leading, spacing: Tv.Dimen.spaceM) {
                    if let photo = model.latest {
                        Image(uiImage: photo).resizable().scaledToFit()
                            .frame(maxWidth: 720, maxHeight: 410)
                            .clipShape(RoundedRectangle(cornerRadius: Tv.Dimen.radiusL))
                            .accessibilityLabel("最近传到电视的照片")
                    }
                    Text(model.status)
                        .font(.system(size: Tv.Font.section, weight: .semibold))
                }
                Spacer()
            }
            Button("完成") { dismiss() }
                .focused($doneFocused)
                .tvFocus(doneFocused)
                .disabled(model.saving)
            Spacer()
        }
        .padding(Tv.Dimen.screenPadding)
        .foregroundStyle(Tv.Color.textPrimary)
        .background(Tv.Color.bg.ignoresSafeArea())
        .onAppear { model.start() }
        .onDisappear {
            model.stop()
            if model.saved > 0 { onImported() }
        }
        .onExitCommand { if !model.saving { dismiss() } }
    }

    private func qrImage(_ content: String) -> UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(content.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage,
              let cg = CIContext().createCGImage(output, from: output.extent) else { return nil }
        return UIImage(cgImage: cg)
    }
}

@MainActor
private final class PhotoTransferModel: ObservableObject {
    @Published private(set) var urls: [String] = []
    @Published private(set) var status = "等待手机上传"
    @Published private(set) var latest: UIImage?
    @Published private(set) var saving = false
    private(set) var saved = 0
    private var active = false
    private let repository: FnOsRepository
    private lazy var server = LocalUploadServer { [weak self] data in
        guard let self, self.active else { return (410, "电视上的传图页面已关闭") }
        return await self.receive(data)
    }

    init(repository: FnOsRepository) { self.repository = repository }

    func start() {
        active = true
        if server.start() { urls = server.urls }
    }

    func stop() {
        active = false
        server.stop()
    }

    private func receive(_ data: Data) async -> (Int, String) {
        saving = true
        defer { saving = false }
        let photo: PhonePhoto
        do {
            photo = try await Task.detached(priority: .userInitiated) { try PhonePhoto(data: data) }.value
        } catch {
            return (415, error.localizedDescription)
        }
        guard active else { return (410, "电视上的传图页面已关闭") }
        status = "正在保存到飞牛 NAS…"
        do {
            try await repository.savePhonePhoto(photo)
            saved += 1
            latest = UIImage(cgImage: photo.thumbnail)
            status = "已保存到飞牛 · \(saved) 张"
            return (200, status)
        } catch {
            status = FnOsApi.describe(error, access: repository.access)
            return (error is FnOsPhotoImportUncertain ? 409 : 502, status)
        }
    }
}
