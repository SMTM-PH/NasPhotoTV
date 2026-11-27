import SwiftUI

/// 幻灯片 / 屏保背景音乐：只在 active 且开启时播放最新上传曲目（单曲循环）。
/// 对标 Android `SlideshowMusicEffect`：active 变 false / 关闭 / 音量变化都立即重建播放器。
struct MusicEffect: View {

    let active: Bool

    @ObservedObject private var settings = AppSettings.shared
    @ObservedObject private var store = MusicStore.shared
    @StateObject private var player = MusicPlayer()

    var body: some View {
        Color.clear
            .frame(width: 0, height: 0)
            .onAppear { update() }
            .onChange(of: active) { _ in update() }
            .onChange(of: settings.screensaver.musicEnabled) { _ in update() }
            .onChange(of: settings.screensaver.musicVolume) { _ in update() }
            .onChange(of: store.latestName) { _ in update() }
            .onDisappear { player.stop() }
    }

    private func update() {
        guard active, settings.screensaver.musicEnabled, let url = store.latestURL else {
            player.stop()
            return
        }
        player.play(url: url, volume: settings.screensaver.musicVolume)
    }
}

/// 上传面板（对标 Android `MusicOverlay`）：面板打开期间启动 HTTP 上传服务，
/// 关闭立即停止 —— 平时不占端口。
struct MusicPanel: View {

    @Environment(\.dismiss) private var dismiss
    @StateObject private var server = LocalUploadServer()
    @ObservedObject private var store = MusicStore.shared
    @ObservedObject private var settings = AppSettings.shared

    var body: some View {
        VStack(alignment: .leading, spacing: Tv.Dimen.spaceL) {
            Text("幻灯片背景音乐")
                .font(.system(size: Tv.Font.display, weight: .bold))
                .foregroundColor(Tv.Color.textPrimary)
            Text("手机连同一个 Wi‑Fi，用浏览器打开下面的地址，选一个音频文件上传即可；电视播放幻灯片/屏保时循环播放。")
                .font(.system(size: Tv.Font.body))
                .foregroundColor(Tv.Color.textSecondary)
                .fixedSize(horizontal: false, vertical: true)

            HStack {
                Text(settings.screensaver.musicEnabled ? "背景音乐：开" : "背景音乐：关")
                    .font(.system(size: Tv.Font.body, weight: .semibold))
                    .foregroundColor(settings.screensaver.musicEnabled ? Tv.Color.accent : Tv.Color.textSecondary)
                Spacer()
            }

            VStack(alignment: .leading, spacing: Tv.Dimen.spaceXs) {
                Text("用手机浏览器打开（局域网）：")
                    .font(.system(size: Tv.Font.caption))
                    .foregroundColor(Tv.Color.textTertiary)
                if server.urls.isEmpty {
                    Text("未检测到局域网地址：请确认电视已联网后重新打开本面板")
                        .font(.system(size: Tv.Font.caption))
                        .foregroundColor(Tv.Color.danger)
                } else {
                    ForEach(server.urls, id: \.self) { url in
                        Text(url)
                            .font(.system(size: Tv.Font.cardTitle, weight: .medium))
                            .foregroundColor(Tv.Color.accent)
                    }
                }
            }

            VStack(alignment: .leading, spacing: 4) {
                Text("已上传（最新一个会被使用）")
                    .font(.system(size: Tv.Font.caption))
                    .foregroundColor(Tv.Color.textTertiary)
                if store.trackNames.isEmpty {
                    Text("还没有上传音乐")
                        .font(.system(size: Tv.Font.body))
                        .foregroundColor(Tv.Color.textSecondary)
                } else {
                    ForEach(Array(store.trackNames.prefix(6).enumerated()), id: \.offset) { i, name in
                        Text("\(i == 0 ? "▶ " : "   ")\(name)")
                            .font(.system(size: Tv.Font.body))
                            .foregroundColor(i == 0 ? Tv.Color.textPrimary : Tv.Color.textSecondary)
                            .lineLimit(1)
                    }
                }
            }

            HStack(spacing: Tv.Dimen.spaceM) {
                Button(settings.screensaver.musicEnabled ? "关闭音乐" : "开启音乐") {
                    settings.screensaver.musicEnabled.toggle()
                }
                .buttonStyle(.plain)
                .padding(.horizontal, Tv.Dimen.spaceL)
                .padding(.vertical, 12)
                .background(Capsule().fill(Tv.Color.surfaceHigh))
                .foregroundColor(Tv.Color.textPrimary)

                Button("刷新列表") { store.refresh() }
                    .buttonStyle(.plain)
                    .padding(.horizontal, Tv.Dimen.spaceL)
                    .padding(.vertical, 12)
                    .background(Capsule().fill(Tv.Color.surfaceHigh))
                    .foregroundColor(Tv.Color.textPrimary)

                Button("完成") { dismiss() }
                    .buttonStyle(.plain)
                    .padding(.horizontal, Tv.Dimen.spaceL)
                    .padding(.vertical, 12)
                    .background(Capsule().fill(Tv.Color.accent))
                    .foregroundColor(Tv.Color.bg)

                Text("BACK 也可关闭")
                    .font(.system(size: Tv.Font.tiny))
                    .foregroundColor(Tv.Color.textTertiary)
            }
            Spacer()
        }
        .padding(Tv.Dimen.screenPadding)
        .background(Tv.Color.bg.ignoresSafeArea())
        .onAppear { _ = server.start() }
        .onDisappear { server.stop() }
        .onExitCommand { dismiss() }
    }
}
