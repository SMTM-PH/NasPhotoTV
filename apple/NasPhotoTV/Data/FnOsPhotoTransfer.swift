import Foundation
import ImageIO
import UniformTypeIdentifiers

struct PhonePhoto {
    let name: String
    let mime: String
    let data: Data
    let thumbnail: CGImage

    init(data: Data) throws {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              let type = CGImageSourceGetType(source),
              let ext = UTType(type as String)?.preferredFilenameExtension?.lowercased(),
              ["jpg", "jpeg", "png", "webp", "heic", "heif"].contains(ext),
              let mime = UTType(type as String)?.preferredMIMEType,
              let thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                  kCGImageSourceCreateThumbnailFromImageAlways: true,
                  kCGImageSourceCreateThumbnailWithTransform: true,
                  kCGImageSourceThumbnailMaxPixelSize: 900,
              ] as CFDictionary) else {
            throw FnOsError("请选择有效的 JPG、PNG、WebP 或 HEIC 照片")
        }
        self.data = data
        self.mime = mime
        name = "NASPhotoTV_\(UUID().uuidString.lowercased()).\(ext)"
        self.thumbnail = thumbnail
    }
}

struct FnOsPhotoImportUncertain: LocalizedError {
    var errorDescription: String? { "原图已上传，飞牛入库结果待确认，请先检查相册再重试" }
}
