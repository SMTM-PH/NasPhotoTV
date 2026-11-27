import SwiftUI

/// 顶层路由：未连接 → 连接页；已连接 → 首页。
/// 与 Android 端 `NasPhotoTvApp.kt` 的 when(route) 思路一致，保持最朴素的状态机。
struct RootView: View {

    @ObservedObject private var settings = AppSettings.shared

    @State private var repository: FnOsRepository?
    @State private var connecting = false

    var body: some View {
        Group {
            if let repository {
                HomeView(repository: repository) {
                    repository.close()
                    self.repository = nil
                }
            } else {
                ConnectView(settings: settings) { repo in
                    self.repository = repo
                }
            }
        }
        .background(Tv.Color.bg.ignoresSafeArea())
        .preferredColorScheme(settings.appearance.colorScheme)
    }
}
