# CoolapkDetailPlus (酷安详情页搜索增强插件)

这是一个基于 **LSPosed (LibXposed / YukiHookAPI)** 的 Xposed 模块，专为酷安客户端设计，旨在为酷安的内容详情页（如 `FeedDetailActivityV8`）提供强大、轻量的**页内文本检索与精准滚动定位**增强功能。

---

## 项目信息

- **版本号**：v0.0.5
- **GitHub 仓库**：`git@github.com:zhou-iceo/Coolapkdetailplus.git`
- **适用框架**：LSPosed / EdXposed / LibXposed (API 102) / YukiHookAPI 1.3.2
- **最低 Android 版本**：Android 8.0 (API 26) +

---

## 核心特性

- **无缝菜单注入与像素级对齐**：
  - 在详情页顶部选项菜单 (`OptionsMenu`) 中自动增加【查找】操作项；
  - 在底栏长按弹窗/更多操作菜单中智能适配注入【查找】按键，与既有按钮 (`复制 - 收藏 - 历史编辑 - 举报`) 在同一水平行平铺呈现；
  - 正圆形图标底座、全新矢量放大镜图标，完美支持日间/夜间模式自适应；
  - 水平与垂直方向像素级精准对齐，消除任何多余空白或错位。
- **优雅悬浮查找交互**：
  - 现代化圆角卡片式悬浮搜索栏，支持单手操作；
  - 实时关键字检索并呈现匹配计数（如 `1/12`）；
  - 醒目高亮区分当前匹配项（橙底白字）与其他匹配项（黄底黑字）。
- **行级精准滚动定位**：
  - 支持点击“上一个”（▲）和“下一个”（▼）按钮平滑滚动定位到准确位置；
  - 支持同一个长帖/长文本 `TextView` 内多处匹配的行级精确定位（通过 `TextView.layout` 行像素计算）；
  - 跨 ClassLoader 安全穿透，完美适配酷安的 `RecyclerView`、`NestedScrollView`、`ScrollView` 等滚动容器；
  - 自动根据当前顶部悬浮栏的屏幕高度动态避让，确保高亮行始终舒适呈现在搜索栏下方；
  - 支持列表滑动回收后离屏项目的智能恢复定位。
- **配套模块管理与实时日志**：
  - 使用 Jetpack Compose 开发的原生模块 App；
  - 实时显示 LSPosed 模块激活状态与版本号 (`v0.0.5`)；
  - 提供仓库地址与实时日志控制台，可在宿主酷安运行时捕获插件的关键检索与滚动定位步骤。

---

## 更新日志

### v0.0.5
1. **全新应用图标与图标规范统一**：
   - 使用全新设计的 `header-logo` 替换模块 App 桌面图标，涵盖 mdpi 至 xxxhdpi 全密度尺寸。
2. **底层矢量搜索图标升级**：
   - 替换底栏【查找】按钮图标为自定义 SVG 矢量放大镜图标（`ic_search_menu.xml`），保证在不同分辨率与主题下的清晰平滑。
3. **UI 像素级深度对齐与布局优化**：
   - **正圆形背景**：强制构建 `GradientDrawable.OVAL` 并实时采样宿主既有按钮背景底色，彻底消除方块背景问题，完美匹配日间与夜间模式；
   - **精确垂直对齐**：移除多余顶边距偏移，精准计算宿主 `RecyclerView` 的内边距，使【查找】按钮中心线与【复制】【收藏】【举报】按钮完全在同一水平线上；
   - **消除空白占位**：将行容器对齐方式重置为起始靠左对齐，彻底解决排布时左侧多出空白占位格的问题。
4. **主界面信息完善与版本号规范**：
   - 主界面增加模块 GitHub 远程仓库地址显示 (`git@github.com:zhou-iceo/Coolapkdetailplus.git`)；
   - 规范版本号显示格式为 `v0.0.5`。

---

## 编译与打包

本工程使用 Gradle 8.9 + AGP 8.9.1 构建。

### 编译 Debug APK
```bash
./gradlew.bat assembleDebug
```
产物位置：
`app/build/outputs/apk/debug/app-debug.apk`

### 编译 Release APK
```bash
./gradlew.bat assembleRelease
```
产物位置：
`app/build/outputs/apk/release/app-release-unsigned.apk`
