import SwiftUI

/// 连接页：填 NAS 地址 + 账号，测试连接；两步验证时输入 6 位动态码。
/// 交互对齐 Android 端 `ConnectionScreen.kt`（密码登录 → 挑战动态码 → 会话转正）。
struct ConnectView: View {

    @ObservedObject var settings: AppSettings
    var onConnected: (FnOsRepository) -> Void

    @State private var testedAccess: FnOsAccess?
    @State private var repository: FnOsRepository?
    @State private var testing = false
    @State private var message = ""
    @State private var isError = false
    @State private var stats: FnOsStats?
    @State private var twoFactorActive = false
    @State private var twoFactorCode = ""
    @State private var submittingCode = false

    var body: some View {
        VStack(alignment: .leading, spacing: Tv.Dimen.spaceM) {
            VStack(alignment: .leading, spacing: Tv.Dimen.spaceXs) {
                Text("NAS Photo TV")
                    .font(.system(size: Tv.Font.display, weight: .bold))
                    .foregroundColor(Tv.Color.textPrimary)
                Text("在 Apple TV 上看飞牛 NAS 里的照片 · 局域网直连原片")
                    .font(.system(size: Tv.Font.body))
                    .foregroundColor(Tv.Color.textSecondary)
            }

            HStack(spacing: Tv.Dimen.spaceM) {
                field(label: "NAS 地址（IP 或域名）", text: $settings.host, placeholder: "192.168.1.10", width: 420)
                field(label: "端口", text: portText, placeholder: "5667", width: 160)
            }
            HStack(spacing: Tv.Dimen.spaceM) {
                field(label: "账号", text: $settings.username, placeholder: "admin", width: 300)
                secureField(label: "密码", text: $settings.password, placeholder: "••••••••", width: 300)
                field(label: "访问码（未启用留空）", text: $settings.accessCode, placeholder: "", width: 300)
            }

            HStack(spacing: Tv.Dimen.spaceL) {
                Button(action: { Task { await test() } }) {
                    Text(testing ? "连接中…" : "测试连接")
                        .font(.system(size: Tv.Font.body, weight: .semibold))
                        .padding(.horizontal, Tv.Dimen.spaceL)
                        .padding(.vertical, 14)
                        .background(Capsule().fill(Tv.Color.accent))
                        .foregroundColor(Tv.Color.bg)
                }
                .disabled(testing)

                Button(action: { Task { await connect() } }) {
                    Text("进入相册")
                        .font(.system(size: Tv.Font.body, weight: .semibold))
                        .padding(.horizontal, Tv.Dimen.spaceL)
                        .padding(.vertical, 14)
                        .background(Capsule().fill(Tv.Color.surfaceHigh))
                        .foregroundColor(Tv.Color.textPrimary)
                }
            }
            .padding(.top, Tv.Dimen.spaceXs)

            if !message.isEmpty {
                Text(message)
                    .font(.system(size: Tv.Font.caption))
                    .foregroundColor(isError ? Tv.Color.danger : Tv.Color.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if twoFactorActive {
                HStack(spacing: Tv.Dimen.spaceM) {
                    Text("两步验证：输入飞牛 App 上的 6 位动态码")
                        .font(.system(size: Tv.Font.body))
                        .foregroundColor(Tv.Color.textPrimary)
                    TextField("000000", text: $twoFactorCode)
                        .font(.system(size: Tv.Font.body, weight: .semibold, design: .monospaced))
                        .frame(width: 200)
                    Button(action: { Task { await submitCode() } }) {
                        Text(submittingCode ? "验证中…" : "验证")
                            .padding(.horizontal, Tv.Dimen.spaceL)
                            .padding(.vertical, 12)
                            .background(Capsule().fill(Tv.Color.accentAlt))
                            .foregroundColor(.white)
                    }
                    .disabled(submittingCode)
                }
                .padding(Tv.Dimen.spaceM)
                .background(RoundedRectangle(cornerRadius: Tv.Dimen.radiusM).fill(Tv.Color.surfaceSoft))
            }

            Spacer()
        }
        .padding(Tv.Dimen.screenPadding)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private var portText: Binding<String> {
        Binding(
            get: { String(settings.port) },
            set: { settings.port = Int($0) ?? 5667 },
        )
    }

    private func field(label: String, text: Binding<String>, placeholder: String, width: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.system(size: Tv.Font.caption))
                .foregroundColor(Tv.Color.textSecondary)
            TextField(placeholder, text: text)
                .font(.system(size: Tv.Font.body))
                .frame(width: width)
        }
    }

    private func secureField(label: String, text: Binding<String>, placeholder: String, width: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.system(size: Tv.Font.caption))
                .foregroundColor(Tv.Color.textSecondary)
            SecureField(placeholder, text: text)
                .font(.system(size: Tv.Font.body))
                .frame(width: width)
        }
    }

    // MARK: - 动作

    private func currentAccess() -> FnOsAccess { settings.access }

    private func test() async {
        testing = true
        isError = false
        message = "正在连接 \(currentAccess().host):\(currentAccess().port) …"
        defer { testing = false }
        let access = currentAccess()
        // 凭据变了就换新会话（旧 WebSocket 一并关闭）
        if repository == nil || testedAccess != access {
            repository?.close()
            repository = FnOsRepository(access: access)
            testedAccess = access
        }
        guard let repository else { return }
        let result = await repository.probe()
        if result.ok {
            stats = result.stats
            isError = false
            message = "连接成功（\(result.latencyMs)ms）· 照片 \(result.stats?.photoCount ?? 0) 张 / 视频 \(result.stats?.videoCount ?? 0) 个，按「进入相册」开始浏览"
        } else if result.twoFactor != nil {
            twoFactorActive = true
            isError = false
            message = "该账号已开启两步验证：请输入飞牛 App 上的 6 位动态码"
        } else {
            stats = nil
            isError = true
            message = result.error ?? "连接失败"
        }
    }

    private func connect() async {
        let access = currentAccess()
        if repository == nil || testedAccess != access {
            repository?.close()
            repository = FnOsRepository(access: access)
            testedAccess = access
        }
        guard let repository else { return }
        // 直接进入：会话在加载图库时按需建立；两步验证未完成时首页会收到明确报错
        let pending = await repository.hasPendingTwoFactor()
        if pending {
            isError = true
            message = "两步验证未完成：请先输入 6 位动态码"
            return
        }
        onConnected(repository)
        self.repository = nil
        testedAccess = nil
    }

    private func submitCode() async {
        guard let repository else { return }
        submittingCode = true
        defer { submittingCode = false }
        do {
            try await repository.verifyTwoFactor(twoFactorCode)
            twoFactorActive = false
            isError = false
            message = "两步验证通过，按「进入相册」开始浏览"
            if let stats = try? await repository.stats() {
                self.stats = stats
            }
        } catch {
            isError = true
            message = FnOsApi.describe(error, access: currentAccess())
        }
    }
}
