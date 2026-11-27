import SwiftUI

struct ArtBookView: View {
    let items: [MediaItem]
    var onClose: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @FocusState private var focus: String?
    @State private var openYear: Int?
    @State private var month = 1
    @State private var viewer: HomeView.ViewerSession?

    private let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.firstWeekday = 2
        return calendar
    }()

    private var photosByYear: [Int: [MediaItem]] {
        Dictionary(grouping: items.filter { $0.type == .photo && $0.dateTaken != nil }) {
            calendar.component(.year, from: $0.dateTaken!)
        }
    }

    var body: some View {
        let years = photosByYear
        VStack(alignment: .leading, spacing: Tv.Dimen.spaceL) {
            HStack {
                Button {
                    goBack()
                } label: {
                    Label("返回", systemImage: "arrow.left").padding(Tv.Dimen.spaceS)
                }
                .buttonStyle(TvRemoteButtonStyle())
                .focused($focus, equals: "back")
                .tvFocus(focus == "back")
                Text(openYear.map { "\(String($0)) 年画册" } ?? "艺术画册")
                    .font(.system(size: Tv.Font.display, weight: .semibold))
                Spacer()
                Text(openYear.map { "\(years[$0]?.count ?? 0) 张照片" } ?? "\(years.count) 本年度画册")
                    .foregroundStyle(Tv.Color.textSecondary)
            }
            if let year = openYear {
                calendarPage(year: year, photos: years[year] ?? [])
            } else if years.isEmpty {
                Text("有拍摄日期的照片会按年份整理成画册")
                    .foregroundStyle(Tv.Color.textSecondary)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: Tv.Dimen.spaceL), count: 3),
                              spacing: Tv.Dimen.spaceL) {
                        ForEach(years.keys.sorted(by: >), id: \.self) { year in
                            if let photos = years[year], let cover = photos.first {
                                book(year: year, photos: photos, cover: cover)
                            }
                        }
                    }
                    .padding(Tv.Dimen.spaceM)
                }
            }
        }
        .padding(Tv.Dimen.screenPadding)
        .background(Tv.Color.bg.ignoresSafeArea())
        .foregroundStyle(Tv.Color.textPrimary)
        .preferredColorScheme(.dark)
        .onExitCommand { goBack() }
        .fullScreenCover(item: $viewer) { session in
            PhotoViewer(items: session.items, initialIndex: session.index) { viewer = nil }
                .preferredColorScheme(.dark)
        }
    }

    private func book(year: Int, photos: [MediaItem], cover: MediaItem) -> some View {
        let id = "year-\(year)"
        return Button {
            month = cover.dateTaken.map { calendar.component(.month, from: $0) } ?? 1
            openYear = year
        } label: {
            VStack(alignment: .leading, spacing: Tv.Dimen.spaceM) {
                RemoteImage(url: cover.thumbnailUrl)
                    .frame(height: 230)
                    .clipShape(RoundedRectangle(cornerRadius: Tv.Dimen.radiusM))
                Text("\(String(year)) 年").font(.system(size: 46, weight: .bold, design: .serif))
                Text("\(photos.count) 张照片").font(.system(size: Tv.Font.body))
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(Tv.Dimen.spaceM)
            .foregroundStyle(Tv.Color.paperInk)
            .background(Tv.Color.paper, in: RoundedRectangle(cornerRadius: Tv.Dimen.radiusL))
        }
        .buttonStyle(TvRemoteButtonStyle())
        .focused($focus, equals: id)
        .tvFocus(focus == id)
    }

    private func calendarPage(year: Int, photos: [MediaItem]) -> some View {
        let first = calendar.date(from: DateComponents(year: year, month: month, day: 1))
        let weekday = first.map { calendar.component(.weekday, from: $0) } ?? 2
        let start = (weekday + 5) % 7
        let days = first.flatMap { calendar.range(of: .day, in: .month, for: $0)?.count } ?? 0
        let dated = photos.compactMap { item -> (Int, MediaItem)? in
            guard let date = item.dateTaken, calendar.component(.month, from: date) == month else { return nil }
            return (calendar.component(.day, from: date), item)
        }
        let byDay = Dictionary(grouping: dated, by: { $0.0 })
        return VStack(spacing: Tv.Dimen.spaceM) {
            HStack(spacing: Tv.Dimen.spaceL) {
                monthButton("上个月", id: "previous", change: -1)
                    .disabled(month <= 1)
                Text("\(month) 月").font(.system(size: Tv.Font.display, weight: .bold, design: .serif))
                monthButton("下个月", id: "next", change: 1)
                    .disabled(month >= 12)
            }
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: Tv.Dimen.spaceS), count: 7),
                      spacing: Tv.Dimen.spaceS) {
                ForEach(["一", "二", "三", "四", "五", "六", "日"], id: \.self) { Text($0).padding(.bottom, Tv.Dimen.spaceS) }
                ForEach(0..<42, id: \.self) { offset in
                    let day = offset - start + 1
                    if (1...max(days, 1)).contains(day), days > 0 {
                        dayTile(day, photos: byDay[day]?.map(\.1) ?? [])
                    } else {
                        Color.clear.frame(height: 70)
                    }
                }
            }
            .padding(Tv.Dimen.spaceM)
            .foregroundStyle(Tv.Color.paperInk)
            .background(Tv.Color.paper, in: RoundedRectangle(cornerRadius: Tv.Dimen.radiusL))
        }
    }

    private func monthButton(_ title: String, id: String, change: Int) -> some View {
        Button {
            withAnimation(reduceMotion ? nil : .easeInOut(duration: 0.2)) { month += change }
            if month == 1 || month == 12 { focus = change < 0 ? "next" : "previous" }
        } label: {
            Label(title, systemImage: change < 0 ? "chevron.left" : "chevron.right")
                .padding(Tv.Dimen.spaceS)
                .background(Tv.Color.surface, in: RoundedRectangle(cornerRadius: Tv.Dimen.radiusM))
        }
        .buttonStyle(TvRemoteButtonStyle())
        .focused($focus, equals: id)
        .tvFocus(focus == id)
    }

    @ViewBuilder private func dayTile(_ day: Int, photos: [MediaItem]) -> some View {
        if let first = photos.first {
            let id = "day-\(day)"
            Button {
                viewer = HomeView.ViewerSession(items: photos, index: 0)
            } label: {
                RemoteImage(url: first.thumbnailUrl)
                    .frame(height: 70)
                    .clipShape(RoundedRectangle(cornerRadius: Tv.Dimen.radiusM))
                    .overlay(alignment: .topTrailing) {
                        Text(String(day)).font(.system(size: Tv.Font.body, weight: .bold))
                            .padding(.horizontal, Tv.Dimen.spaceXs).background(.black.opacity(0.75), in: Capsule())
                            .foregroundStyle(.white).padding(Tv.Dimen.spaceXs)
                    }
            }
            .accessibilityLabel("\(month) 月 \(day) 日，\(photos.count) 张照片")
            .buttonStyle(TvRemoteButtonStyle())
            .focused($focus, equals: id)
            .tvFocus(focus == id)
        } else {
            Text(String(day)).frame(maxWidth: .infinity, minHeight: 70)
                .background(Tv.Color.paperInk.opacity(0.07), in: RoundedRectangle(cornerRadius: Tv.Dimen.radiusM))
        }
    }

    private func goBack() {
        if let year = openYear {
            openYear = nil
            focus = "year-\(year)"
        } else {
            onClose()
        }
    }
}
