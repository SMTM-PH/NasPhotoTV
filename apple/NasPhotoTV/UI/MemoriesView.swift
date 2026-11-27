import SwiftUI

struct MemoriesView: View {
    let collections: [HomeView.Collection]
    var focus: FocusState<String?>.Binding
    var onOpen: (HomeView.Collection) -> Void

    @State private var selectedID: String?
    @State private var showGrid = false

    var body: some View {
        VStack(alignment: .leading, spacing: Tv.Dimen.spaceM) {
            HStack {
                Text(showGrid ? "全部智能分类" : "智能分类")
                    .font(.system(size: Tv.Font.section, weight: .bold))
                Text(showGrid ? "\(collections.count) 个分类" : "\(collections.count) 个分类 · ↓ 瀑布浏览")
                    .foregroundStyle(Tv.Color.textSecondary)
                Spacer()
                Button(showGrid ? "返回回忆" : "瀑布浏览") {
                    showGrid.toggle()
                    focus.wrappedValue = "memory-layout"
                }
                .focused(focus, equals: "memory-layout")
                .tvFocus(focus.wrappedValue == "memory-layout")
            }
            if showGrid {
                CollectionsTab(collections: collections, focusedId: focus, emptyText: "暂无智能分类", onOpen: onOpen)
            } else if let selected = collections.first(where: { $0.id == selectedID }) ?? collections.first {
                HStack(spacing: Tv.Dimen.spaceL) {
                    VStack(alignment: .leading, spacing: Tv.Dimen.spaceM) {
                        Text("飞牛 AI 场景事物").foregroundStyle(Tv.Color.accent)
                        Text(selected.name).font(.system(size: Tv.Font.display, weight: .bold)).lineLimit(2)
                        Text("\(selected.itemCount) 项").foregroundStyle(Tv.Color.textSecondary)
                        Button("打开分类") { onOpen(selected) }
                            .focused(focus, equals: "memory-open")
                            .tvFocus(focus.wrappedValue == "memory-open")
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    RemoteImage(url: selected.coverUrl)
                        .frame(width: 750, height: 325)
                        .clipShape(RoundedRectangle(cornerRadius: Tv.Dimen.radiusL))
                }
                .padding(Tv.Dimen.spaceL)
                .background(Tv.Color.stage, in: RoundedRectangle(cornerRadius: Tv.Dimen.radiusL))
                ScrollViewReader { proxy in
                    ScrollView(.horizontal) {
                        LazyHStack(spacing: Tv.Dimen.spaceM) {
                            ForEach(collections) { item in
                                Button { onOpen(item) } label: {
                                    VStack(alignment: .leading, spacing: Tv.Dimen.spaceS) {
                                        RemoteImage(url: item.coverUrl).frame(width: 240, height: 110).clipped()
                                        Text(item.name).lineLimit(1).padding(.horizontal, Tv.Dimen.spaceS)
                                    }
                                    .padding(.bottom, Tv.Dimen.spaceS)
                                    .background(Tv.Color.surface)
                                    .clipShape(RoundedRectangle(cornerRadius: Tv.Dimen.radiusM))
                                }
                                .buttonStyle(TvRemoteButtonStyle())
                                .focused(focus, equals: "memory-\(item.id)")
                                .tvFocus(focus.wrappedValue == "memory-\(item.id)")
                                .onMoveCommand { direction in
                                    switch direction {
                                    case .down:
                                        showGrid = true
                                        focus.wrappedValue = "collection-\(item.id)"
                                    case .up:
                                        focus.wrappedValue = "memory-open"
                                    case .left, .right:
                                        guard let index = collections.firstIndex(where: { $0.id == item.id }) else { return }
                                        let target = index + (direction == .left ? -1 : 1)
                                        guard collections.indices.contains(target) else { return }
                                        let next = collections[target]
                                        proxy.scrollTo(next.id, anchor: .center)
                                        focus.wrappedValue = "memory-\(next.id)"
                                    @unknown default: break
                                    }
                                }
                                .id(item.id)
                            }
                        }
                        .padding(Tv.Dimen.spaceS)
                    }
                }
                Spacer(minLength: 0)
            }
        }
        .foregroundStyle(Tv.Color.textPrimary)
        .onChange(of: focus.wrappedValue) { _, current in
            guard let current,
                  let collection = collections.first(where: { "memory-\($0.id)" == current }) else { return }
            selectedID = collection.id
        }
        .onExitCommand(perform: showGrid ? closeGrid : nil)
    }

    private func closeGrid() {
        showGrid = false
        focus.wrappedValue = "memory-layout"
    }
}
