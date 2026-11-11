# 手机 / 平板触摸界面设计

日期：2026-11-11
状态：待用户审阅

## 1. 背景与问题

应用最初为电视（D-pad 遥控器）设计。界面全部由 `MainActivity.kt`（约 5200 行）用代码拼装，
控件是带焦点高亮的"文字按钮"，页面里写死了"方向键选择，确定播放"之类的提示，播放控制是
一个 TV 按钮面板（`showPanel()`），弹窗走 `TvTools.choose()`。

此前两次提交（`1044c88`、`5ae539c`）只在这套 TV 界面上打补丁：给 `showCatalog()` 加了
`applyPhoneCatalogChrome()` / `phoneBottomBar()`，给播放加了 `showTouchTransport()`，
`HomeFeedScreen` 里加了竖滑逻辑。结果在手机/平板上仍然是 TV 布局：顶部文字标签栏、
大块黑色视频区加侧边简介、全是文字按钮，触摸体验很差。另外 `DeviceUi.form()` 用
`smallestScreenWidthDp >= 600` 区分平板，一台宽约 640dp 的设备会被误判为平板。

## 2. 目标与非目标

目标：

- 手机和平板使用触摸优先、短剧 App 风格的全新界面，覆盖所有页面。
- 屏幕方向跟随传感器，竖屏优先；播放可横屏全屏。
- 电视端（`UI_MODE_TYPE_TELEVISION`）的界面、焦点逻辑、测试保持不变。
- 不新增第三方依赖（项目没有 Compose / RecyclerView，继续使用原生 View）。

非目标（YAGNI）：

- 不做亮度 / 音量手势、画中画、投屏、深色/浅色主题切换。
- 不改动数据层、账号同步、解码、中继、更新等与界面无关的逻辑。
- 不重写 `MainActivity` 的播放引擎；只在其外面加触摸外壳。

## 3. 已确认的决策

| 项目 | 决策 |
| --- | --- |
| 整体风格 | 短剧 App 风格：首页为全屏竖滑推荐流 |
| 屏幕方向 | 跟随传感器，竖屏优先；手机浏览保持竖屏，平板可自由旋转，播放可全屏横屏 |
| 实现方式 | 新增 `touch/` 界面层，通过窄接口复用现有数据与播放逻辑；不再向 `MainActivity` 里加 `if (phone)` |
| 范围 | 首页、找剧、排行榜、收藏、历史、预约、设置与账号、详情、播放器、全部弹窗 |

## 4. 架构

### 4.1 分层

```
MainActivity（现有，状态与播放引擎的拥有者）
  ├─ 电视分支：现有 showCatalog / showDetail / playEpisode UI，原样保留
  └─ 触摸分支：创建 TouchShell，实现 TouchHost 接口交给它
        TouchShell（根视图 + 导航栈 + 底栏/侧栏）
          ├─ HomeFeedPager      推荐流
          ├─ DiscoverScreen     找剧：搜索 + 排行榜
          ├─ LibraryScreen      追剧：收藏 / 历史 / 预约
          ├─ MeScreen           我的：账号 + 设置
          ├─ DetailScreen       详情
          ├─ PlayerChrome       播放器触摸控件
          └─ TouchSheet         底部弹层（替代 TvTools.choose 等）
```

新文件放在 `kotlin-tv/app/src/main/kotlin/com/hongguotv/nativeapp/touch/`，包名
`com.hongguotv.nativeapp.touch`，类均为 `internal`。

### 4.2 TouchHost 接口

`MainActivity` 的 `screen`、`tab`、`detail`、`episodeIndex`、`player` 等状态都是 private，
触摸层不直接访问它们，而是通过 `TouchHost` 读取状态、发出命令：

- 数据读取：`library`、`cloudAccount` 的收藏 / 历史 / 预约查询，搜索联想，榜单快照，
  详情与相关作品。这些已经是 `Library`、`AccountController`、`ContentRepository` 的公开方法，
  `TouchHost` 只做转发，不新增业务逻辑。
- 播放命令：`playEpisode(index, position)`、`togglePlayback()`、`seekTo()`、`skipEpisode()`、
  `setSpeed()`、`setQuality()`、`setFrameMode()`，以及当前播放状态（位置、时长、是否播放、
  是否缓冲、是否出错、当前集）。
- 生命周期：`onBackPressed` 路由到 `TouchShell.back()`；`onResume/onPause/onStop` 通知
  当前页面。

接口只暴露触摸层需要的方法，保持小而稳定，便于用假实现写单元测试。

### 4.3 MainActivity 的改动范围

- `showCatalog()` / `showDetail()` / `playEpisode()` 入口处：`DeviceUi.touchFirst(this)` 时
  交给 `TouchShell`，否则走原电视逻辑。
- 删除上两次提交留下的触摸补丁：`applyPhoneCatalogChrome()`、`phoneBottomBar()`、
  `showTouchTransport()`、`attachPlayerGestures()` 的触摸分支，以及 `HomeFeedScreen`、
  `TvSearchPanel`、`EpisodePanel` 等文件中的 phone/tablet 分支，避免两套逻辑并存。
- 播放引擎（`ensurePlayer`、`playEpisode` 的准备与恢复、预取、中继、进度保存）不动。
  播放器 `PlayerView`（`video_player.xml`，TextureView）挂到触摸播放页的容器里。

### 4.4 复用与保留

- 保留：`FeedBackend` / `PublicFeedBackend` / `TrialPlayback` / `DramaFeedState`（推荐流后端，
  含试播缓存与预取；其中不涉及视图的逻辑从 `HomeFeedScreen` 抽成 `FeedController`）；
  `ArtworkLoader` 图片加载；
  `RankingScreen`、`DiscoveryScreen` 中的数据整理逻辑（榜单切换、分类）。
- 重做：以上各类的视图部分（布局、手势、焦点），改为触摸交互。电视仍使用原视图。

## 5. 设备与窗口尺寸

新增 `WindowClass`（按当前窗口宽度 dp，而非 `smallestScreenWidthDp`）：

| 分级 | 宽度 | 典型设备 | 导航 | 网格列数 |
| --- | --- | --- | --- | --- |
| Compact | < 600dp | 手机竖屏 | 底部栏 | 3 |
| Medium | 600–840dp | 手机横屏 / 小平板竖屏 | 左侧窄栏 | 4 |
| Expanded | > 840dp | 平板横屏 | 左侧窄栏 | 5–6 |

- 电视仍由 `UI_MODE_TYPE_TELEVISION` 判定，优先于上表。
- 窗口尺寸变化（旋转、分屏）时按新分级重排，`AndroidManifest.xml` 已声明
  `configChanges="...orientation|screenSize|screenLayout..."`，Activity 不重建。
- `DeviceUi.preferredOrientation()` 对触摸设备改为 `SCREEN_ORIENTATION_FULL_SENSOR`
  （手机仍尊重系统的自动旋转开关）；电视保持横屏。

## 6. 导航与页面栈

- 顶层 4 个标签：**首页**、**找剧**、**追剧**、**我的**。
  - 找剧：顶部搜索栏，下方分段"推荐 / 排行榜"。搜索栏点击后进入搜索态（联想词 + 历史），
    搜索结果为海报网格。
  - 追剧：分段"收藏 / 观看历史 / 账户预约"。
  - 我的：账号卡片（登录状态、同步），下方设置分组列表。
- 顶层标签之间切换保留各自的滚动位置和状态；详情和播放压入栈。
- 页面栈：`TouchShell` 维护 `ArrayDeque<Page>`；系统返回键/手势弹出一层；在顶层非首页标签
  按返回回到首页，在首页按返回退出。播放页返回时保存进度并回到详情（或回到来源页，
  与现有 `directPlayback` 语义一致）。
- 紧凑宽度：底部导航栏 56dp，图标 + 文字，当前项用强调色；平板：左侧 80dp 窄栏，
  图标在上、文字在下。
- 适配系统栏：使用 `fitsSystemWindows` / WindowInsets，不压住状态栏、手势条和刘海。

## 7. 各页面设计

### 7.1 首页推荐流（HomeFeedPager）

- 全屏竖向翻页，每页一部剧的第一集试播；上滑下一部，下滑上一部；翻页有吸附动画。
- 画面底部渐变遮罩上显示：标题、集数 / 标签、简介（两行，点击展开）。
- 右侧垂直操作栏：收藏（切换，已收藏高亮）、选集、评论、"看全集"主按钮。
- 顶部细标签：推荐 / 漫剧 / 真人剧，横向居中，当前项下划线；取代电视版的"换一批"按钮，
  下拉刷新换一批。
- 点击画面暂停 / 继续，暂停时显示居中播放图标；底部一条细进度条。
- 平板横屏：视频居中保持画面比例，左右留白显示渐变背景，操作栏靠右。
- 沿用 `autoPreview` 设置；试播失败时显示"重试"和"看全集"，不显示 TV 提示文字。

### 7.2 找剧（DiscoverScreen）

- 搜索栏：圆角输入框，点击后弹出系统软键盘；输入时联想词列表（复用
  `SearchSuggestionsView` 的数据逻辑）；空输入时显示最近搜索（可清除）。
- 推荐分段：现有 `DiscoveryScreen` 的内容分区，改为横向滚动的海报行 + 纵向网格。
- 排行榜分段：顶部横向可滚动的榜单标签（芯片），下方为带名次的列表；平板为两列。
- 海报卡片：封面 2:3、标题单行、副标题（集数 / 标签）；点击进详情，长按弹出操作面板
  （收藏、查看完整标题、移出记录等，复用 `quickActions` 的业务）。
- 下拉刷新；滚动到底自动加载下一页（新的触摸版 `PosterGridView`，`ContinuousCatalogView`
  与电视焦点导航耦合，不复用）。

### 7.3 追剧（LibraryScreen）

- 收藏、历史：网格，历史项叠加进度条和"第 N 集"。长按进入管理操作。
- 预约：列表，显示更新状态和提醒。
- 空状态给出明确说明和"去找剧"按钮。
- 账号收藏与本机收藏的切换保持现有语义（`librarySources`），以顶部开关条呈现。

### 7.4 详情（DetailScreen）

- 顶部：封面（模糊背景）+ 标题 + 集数 / 标签；返回和分享/更多图标在顶栏。
- 简介：默认两行，点击展开。
- 主操作：底部固定栏：左侧"收藏"图标按钮，右侧"继续第 N 集 / 开始观看 / 重新观看"主按钮，
  语义沿用现有的 `ResumePlayback.target`。
- 选集：按 20 集分组的分段标签（1–20、21–40…），下方 5 列网格；标记"当前"、"上次"、"已看"；
  集数多时顶部放"跳转"。
- 评论入口；相关作品横向海报行（`RelatedWorks` 逻辑）。
- 平板：左栏封面与信息，右栏选集与相关作品，双栏滚动。

### 7.5 我的（MeScreen）

- 账号卡片：未登录显示"登录 / 同步"；已登录显示昵称和同步状态。登录流程使用现有
  `AccountController`，其对话框改为底部弹层 / 全屏页。
- 设置：分组列表（播放、画面、清晰度、倍速、弹幕、同步与备份、解码与伴机、更新、关于），
  开关用标准 Switch；选项类设置点进后以底部弹层选择。内容与 `settings()` 现有项一一对应，
  去掉仅与遥控器有关的描述文字。

### 7.6 弹层与对话框（TouchSheet）

- 统一的底部弹层：圆角、拖动条、遮罩点击关闭、系统返回关闭；平板为居中卡片（最大宽度 480dp）。
- 单选列表、开关列表、网格选集、文字输入、确认对话框五种模板。
- 触摸模式下 `TvTools.choose / chooseGrid / episodePicker / phoneInput` 的调用点改为
  `TouchSheet`；"手机输入"这类 TV 专用入口在触摸模式隐藏。

## 8. 播放器

- 画面：全屏沉浸，隐藏系统栏；状态栏区域不遮挡控件（控件放在安全区内）。
- 控件（点击画面显示，3 秒无操作自动隐藏）：
  - 顶栏：返回、标题和集数、更多（打开设置弹层）。
  - 底栏：播放/暂停、进度条（可拖动，显示当前 / 总时长）、下一集、选集、倍速、全屏切换。
  - 右侧可选：评论与弹幕开关。
- 手势：单击显示/隐藏控件；双击暂停/继续；水平拖动快进快退（显示时间气泡，松手生效）；
  垂直滑动上一集/下一集（沿用 `PlaybackInput.verticalFlingDelta`）；
  长按 2 倍速（松开恢复）。
- 弹层：选集、倍速、清晰度、画面比例、画面旋转、定时关闭、弹幕设置、评论、伴机代解码，
  全部为 `TouchSheet`。播放设置里与电视相关的项（如"常驻播放进度"）保留，
  其余沿用现有 `library` 偏好。
- 方向：竖屏下视频按比例居中，上下为黑底；转到横屏自动铺满。全屏按钮在竖屏下强制横屏
  （`requestedOrientation` 临时置为横屏），再次点击恢复跟随传感器。
- 关键约束：`MainActivity.onConfigurationChanged()` 目前在播放中收到配置变化会调用
  `playEpisode(...)` 重新准备播放（为电视分辨率切换而写）。触摸模式下旋转不得重新准备播放，
  只重新布局，保持位置和播放状态。
- 错误与缓冲：缓冲圈、错误提示和"重试 / 换源"按钮沿用现有恢复逻辑
  （`PlaybackRecoveryUi`），样式改为触摸版。

## 9. 设计令牌（TouchTheme）

- 颜色：沿用 `TvStyle` 的强调色 `#FF7844` 和深色背景，保证品牌一致；新增表面色和分隔线色。
- 字号：正文 15sp、次要 13sp、标题 18–22sp、不低于 12sp；尊重系统字体缩放。
- 触摸目标：不小于 48dp；卡片和按钮用水波纹按压态，不使用焦点高亮样式。
- 间距：页面水平边距 16dp（平板 24dp），卡片间距 12dp，圆角 12dp（弹层 20dp）。
- 所有触摸视图设置 `contentDescription`，满足基本的无障碍朗读。

## 10. 对电视端的保护

- 电视分支的代码路径不变：`DeviceUi.television(context)` 为真时不创建 `TouchShell`。
- `enableRemoteFocus()` 等电视焦点工具保持原行为。
- 现有电视测试（`TvUsabilityTest`、`TvSearchPanelTest`、`RankingScreenTest` 等）必须继续通过。
  删除触摸补丁时同步更新仅覆盖这些补丁的测试（`HomeFeedScreenTest`、`PlaybackInputTest`
  中的触摸用例），改为覆盖新的触摸层。

## 11. 测试与验证

自动化（JVM / Robolectric）：

- `WindowClass` 分级与列数（边界：599/600/840dp，横竖屏）。
- 页面栈：压入、返回、顶层标签返回首页、首页返回退出、播放返回保存进度。
- `TouchSheet`：打开 / 关闭 / 返回键关闭 / 选择回调。
- 手势判定：水平拖动换算为目标时间、垂直滑动阈值、双击与单击区分、长按倍速恢复。
- `TouchHost` 假实现驱动的页面渲染测试：详情页选集标记、追剧空状态、搜索联想列表。
- 现有电视测试全部保持通过：`./gradlew -p kotlin-tv :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`。

设备验证（模拟器 / 真机）：

- 当前连接的设备 `emulator-5554`（720×1920，density 180，约 640dp 宽）。验证时用
  `adb shell wm size` / `wm density` 临时切换为手机竖屏（1080×2400、density 420）和平板横屏
  （2560×1600、density 320），截图检查每个页面，测完用 `wm size reset` / `wm density reset`
  恢复。
- 场景：首页翻页与试播、找剧搜索与榜单、追剧三个分段、详情选集与继续观看、播放全流程
  （控件显隐、手势、旋转保持位置、全屏切换、选集弹层）、设置各分组、系统返回逐层回退。
- 无法验证的项目（真机解码性能、特定厂商旋转行为）在交付说明里明确标注，不声称已通过。

## 12. 实施阶段

分阶段交付，每阶段可独立构建、安装和验收：

1. **基础**：`WindowClass`、`TouchTheme`、`TouchSheet`、`TouchShell`（导航栈、底栏/侧栏、
   WindowInsets）、`TouchHost` 接口与 `MainActivity` 接入，清理上两次的触摸补丁。
2. **浏览页**：找剧（搜索 + 排行榜）、追剧三个分段。
3. **详情页**：含选集、继续观看、相关作品、评论入口。
4. **播放器**：触摸控件、手势、弹层、旋转与全屏。
5. **首页推荐流**：全屏翻页、操作栏、下拉刷新。
6. **我的 / 设置 / 账号**：设置分组、账号登录与同步弹层。

## 13. 风险与应对

- **MainActivity 状态耦合重**：用 `TouchHost` 作为唯一接缝，并在阶段 1 用假实现验证接口足够；
  接口缺方法时在 `MainActivity` 里补薄转发，不把触摸逻辑塞进去。
- **旋转引发重新准备播放**：见第 8 节关键约束，作为阶段 4 的首要验证项。
- **没有 RecyclerView**：海报长列表用新写的 `PosterGridView`（`GridView` 适配器，分页触发、
  封面经 `ArtworkLoader` 按可见优先级加载）；其他页面内容有限，用 `ScrollView` 即可。
- **删除旧补丁可能影响电视测试**：先跑一遍电视测试基线，删除后逐项对照。
- **工作区已有未提交改动**（`decoder-ffmpeg` 构建脚本及两个根目录下的无名文件）：
  与本任务无关，不触碰，也不纳入本任务的提交。
