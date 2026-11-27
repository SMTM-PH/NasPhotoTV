import SwiftUI

struct ScreensaverPhotoWall: View {
    let photos: [MediaItem]
    let interval: Int
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var page = 0
    @State private var images: [UIImage] = []

    var body: some View {
        GeometryReader { screen in
            if !images.isEmpty {
                let columns = images.count == 1 ? 1 : images.count <= 4 ? 2 : 3
                let rows = (images.count + columns - 1) / columns
                let gap = Tv.Dimen.spaceXs
                let width = (screen.size.width - CGFloat(columns - 1) * gap) / CGFloat(columns)
                let height = (screen.size.height - CGFloat(rows - 1) * gap) / CGFloat(rows)
                LazyVGrid(columns: Array(repeating: GridItem(.fixed(width), spacing: gap), count: columns), spacing: gap) {
                    ForEach(images.indices, id: \.self) { i in
                        Image(uiImage: images[i])
                            .resizable().scaledToFill().frame(width: width, height: height).clipped()
                    }
                }
                .id(page)
                .transition(.opacity)
            }
        }
        .ignoresSafeArea()
        .task(id: interval) {
            var start = 0
            while !photos.isEmpty, !Task.isCancelled {
                let count = min(6, photos.count)
                var next: [UIImage] = []
                for index in 0..<count {
                    let photo = photos[(start + index) % photos.count]
                    if let image = await MediaImageLoader.image(at: photo.thumbnailUrl) { next.append(image) }
                }
                guard !Task.isCancelled else { return }
                if !next.isEmpty {
                    withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.6)) {
                        images = next
                        page = start
                    }
                }
                if photos.count <= count && !images.isEmpty { return }
                do { try await Task.sleep(for: .seconds(max(5, interval))) } catch { return }
                start = (start + count) % photos.count
            }
        }
    }
}
