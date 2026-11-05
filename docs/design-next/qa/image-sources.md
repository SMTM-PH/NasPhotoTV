# 设计预览摄影素材来源

这些照片仅打包到 `app/src/debug/res/raw`，用于离线设计评审。正式 Release 不包含示例照片。截图中的「本地示例图库」「设计预览」和相册内容不是用户 NAS 数据。

下载及许可核对日期：2026-11-04（北京时间）。

## 许可

采用 [Unsplash License](https://unsplash.com/license)：允许免费用于商业或非商业项目，并允许下载、复制和分发；不可将未充分修改的照片单独出售，也不可汇编成竞品图库服务。这里将摄影作品作为 App 界面的示例内容，不单独售卖。

官方许可页与摄影分类索引已通过网页工具查阅。部分单图短链接在工具和直接请求中返回不可访问或 403，因此保留官方摄影页 ID、官方图片 CDN 源 URL 及官方分类索引，便于后续复核。

## 图片清单

| Debug 资源 | 摄影师 | 官方摄影页 | 官方图片源 | 预览用途 |
|---|---|---|---|---|
| `design_wall_ocean.jpg` | Sean Oulashin | [KMn4VEeEPR8](https://unsplash.com/photos/KMn4VEeEPR8) | [photo-1507525428034-b723cf961d3e](https://images.unsplash.com/photo-1507525428034-b723cf961d3e) | 海岸主视觉、近期照片、全屏看图 |
| `design_wall_forest.jpg` | Lukasz Szmigiel | [2ShvY8Lf6l0](https://unsplash.com/photos/2ShvY8Lf6l0) | [photo-1426170042593-200f250dfdaf](https://images.unsplash.com/photo-1426170042593-200f250dfdaf) | 森林相册与照片卡片 |
| `design_wall_sunset.jpg` | Sebastien Gabriel | [-IMlv9Jlb24](https://unsplash.com/photos/-IMlv9Jlb24) | [photo-1475924156734-496f6cac6ec1](https://images.unsplash.com/photo-1475924156734-496f6cac6ec1) | 日落相册与照片卡片 |
| `design_wall_aurora.jpg` | Luca Bravo | [Boathouse on a mountain lake](https://unsplash.com/photos/brown-house-near-body-of-water-zAjdgNXsMeg) | [photo-1470770841072-f978cf4d019e](https://images.unsplash.com/photo-1470770841072-f978cf4d019e) | 湖山相册与照片卡片 |
| `design_wall_mono.jpg` | Daniel Leone | [v7daTKlZzaw](https://unsplash.com/photos/v7daTKlZzaw) | [photo-1485470733090-0aae1788d5af](https://images.unsplash.com/photo-1485470733090-0aae1788d5af) | 山川相册与照片卡片 |
| `design_wall_candy.jpg` | Luca Bravo | [eOpewngf68w](https://unsplash.com/photos/eOpewngf68w) | [photo-1501785888041-af3ef285b470](https://images.unsplash.com/photo-1501785888041-af3ef285b470) | 湖畔相册与照片卡片 |

摄影页的补充官方索引：[Forest Wallpapers](https://unsplash.com/wallpapers/nature/forest)、[Sunset Beach](https://unsplash.com/s/photos/sunset-beach)、[Mountain Images](https://unsplash.com/images/nature/mountain)。

下载参数统一为 `?auto=format&fit=max&w=1600&q=80&fm=jpg`，保留原图比例。六张 JPEG 已分别打开检查并通过图像解码校验。资源名沿用已有示例文件的名称，不代表摄影作品的真实地点或拍摄时间；App 中日期、收藏和相册分组都是明确的设计示例数据。
