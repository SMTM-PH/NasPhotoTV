import SwiftUI

/// 全屏看图：单张渲染（内存友好），方向键/遥控滑动切上一张下一张，
/// 确认键弹出/收起信息条，BACK（Menu）退出 —— 对齐 Android 端 `PhotoViewerScreen.kt`。
struct PhotoViewer: View {

    let items: [MediaItem]
    let initialIndex: Int
    var onClose: () -> Void

    @State private var index: Int = 0
    @State private var showInfo = true

    private var current: MediaItem? {
        items.indices.contains(index) ? items[index] : nil
    }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            if let item = current {
                RemoteImage(url: item.url, contentMode: .fit)
                    .ignoresSafeArea()
                    .id(item.id)
                    .animation(.easeInOut(duration: 0.2), value: item.id)
            } else {
                Text("没有可显示的照片")
                    .font(.system(size: Tv.Font.body))
                    .foregroundColor(Tv.Color.textSecondary)
            }

            if showInfo, let item = current {
                VStack {
                    infoBar(item)
                    Spacer()
                }
                .transition(.opacity)
            }

            if items.count > 1 {
                VStack {
                    Spacer()
                    Text("\(index + 1) / \(items.count)")
                        .font(.system(size: Tv.Font.caption))
                        .foregroundColor(Tv.Color.textSecondary)
                        .padding(.bottom, Tv.Dimen.spaceL)
                }
            }
        }
        .onAppear {
            index = min(max(0, initialIndex), max(0, items.count - 1))
        }
        .onMoveCommand { direction in
            switch direction {
            case .left: step(-1)
            case .right: step(1)
            default: showInfo.toggle()
            }
        }
        .onPlayPauseCommand {
            showInfo.toggle()
        }
        .onExitCommand {
            onClose()
        }
    }

    private func step(_ delta: Int) {
        let next = index + delta
        guard items.indices.contains(next) else { return }
        index = next
    }

    private func infoBar(_ item: MediaItem) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: Tv.Dimen.spaceM) {
            Text(item.title)
                .font(.system(size: Tv.Font.cardTitle, weight: .semibold))
                .foregroundColor(Tv.Color.textPrimary)
                .lineLimit(1)
            if let date = item.dateTaken {
                Text(Self.dateText(date))
                    .font(.system(size: Tv.Font.caption))
                    .foregroundColor(Tv.Color.textSecondary)
            }
            if !item.resolution.isEmpty {
                Text(item.resolution)
                    .font(.system(size: Tv.Font.caption))
                    .foregroundColor(Tv.Color.textSecondary)
            }
            if item.sizeBytes > 0 {
                Text(ByteCountFormatter.string(fromByteCount: item.sizeBytes, countStyle: .file))
                    .font(.system(size: Tv.Font.caption))
                    .foregroundColor(Tv.Color.textSecondary)
            }
            if item.isLive {
                Text("实况")
                    .font(.system(size: Tv.Font.tiny, weight: .semibold))
                    .foregroundColor(Tv.Color.accent)
            }
            Spacer()
            Text("确认键收起信息 · 返回键退出")
                .font(.system(size: Tv.Font.tiny))
                .foregroundColor(Tv.Color.textTertiary)
        }
        .padding(.horizontal, Tv.Dimen.screenPadding)
        .padding(.vertical, Tv.Dimen.spaceM)
        .background(
            LinearGradient(
                colors: [Tv.Color.scrim, .clear],
                startPoint: .top, endPoint: .bottom,
            )
            .ignoresSafeArea(),
        )
    }

    private static func dateText(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "zh_CN")
        formatter.dateFormat = "yyyy年M月d日 EEEE"
        return formatter.string(from: date)
    }
}

private extension Tv.Color {
    static let scrim = SwiftUI.Color.black.opacity(0.7)
}
