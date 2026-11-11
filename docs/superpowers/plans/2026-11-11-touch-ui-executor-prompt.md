# 执行提示词（交给 DeepSeek 等执行代理）

把下面"提示词正文"整段粘贴给执行代理。前提：代理能在本机仓库 `E:\资料\Project\Self\hongguoTV` 里读写文件、运行命令（Bash / PowerShell）。

---

## 提示词正文

你是一名资深 Android / Kotlin 工程师，要在本地仓库 `E:\资料\Project\Self\hongguoTV`（子工程 `kotlin-tv`）里，按一份已经写好的实施计划，把电视版 App 的手机和平板界面重做成触摸优先的短剧 App 风格。电视端必须保持原样。

### 第一步：先读，不要先写代码

依次完整阅读下面两份文档，它们是你唯一的需求来源，不要凭记忆或猜测补充需求：

1. 设计规格：`docs/superpowers/specs/2026-11-11-touch-ui-design.md`
2. 实施计划：`docs/superpowers/plans/2026-11-11-touch-ui.md`

计划共 15 个任务，每个任务给出了要新建/修改的文件、接口签名、要先写的测试、要运行的命令和提交信息。计划开头的"Global Constraints"和"Review Focus"对每个任务都生效。

### 开工前的准备

1. 在仓库根目录运行 `git status`。工作区里有与本任务无关的未提交改动：`kotlin-tv/decoder-ffmpeg/build.gradle.kts`、`kotlin-tv/decoder-ffmpeg/tools/build_native.py`，以及仓库根目录两个名字是一串十六进制的无名文件。**不要修改、暂存、提交、删除它们。** 之后每次 `git add` 都只添加当前任务列出的路径，禁止使用 `git add .` 或 `git add -A`。
2. 当前在 `main` 分支。先新建分支：`git switch -c feat/touch-ui`。
3. 把上面两份文档提交一次（只 `git add` 这两份规格/计划文件和本提示词文件）：`git commit -m "docs: add touch UI spec and implementation plan"`。
4. 运行一次基线：`./kotlin-tv/gradlew -p kotlin-tv :core:test :app:testDebugUnitTest`，记录通过的测试数量。若基线本身失败，停下来报告失败的测试名和原因，不要继续。

### 执行规则

- **严格按任务顺序**做 Task 1 到 Task 15，不要跳过、合并或调换顺序。每个任务内部按它的 Step 顺序做：先写失败测试 → 运行并确认确实失败 → 实现 → 运行并确认通过 → 提交。
- 每完成一个 Step，把计划文件里对应的 `- [ ]` 改成 `- [x]`，这样中断后可以从断点继续。
- 接口签名、文件路径、数值（宽度分级阈值、dp、毫秒数等）以计划为准，**不要改名、不要自行换阈值**。如果你认为计划里的接口有问题，先停下来报告原因和你的建议，等人确认，不要擅自改。
- 只做计划要求的事（YAGNI）：不新增第三方依赖，不做亮度/音量手势、画中画、投屏，不重构与本任务无关的代码，不改数据层、账号同步、解码、中继、更新逻辑。
- `MainActivity.kt` 有约 5200 行，**不要整文件读取**。用 Grep 找到函数位置，再用偏移读取需要的几十到一百多行。计划里点名的函数名都可以在该文件里直接搜到。
- 电视分支（`DeviceUi.television(context)` 为真的路径）的行为不能变。每个任务结束都要运行 `./kotlin-tv/gradlew -p kotlin-tv :app:testDebugUnitTest`，已有的电视相关测试必须保持通过。如果某个旧测试失败，先判断是不是你的改动破坏了电视行为：是，就修你的代码；只有 Task 15 里点名的"仅覆盖旧触摸补丁"的用例才可以删改，并要逐个说明理由。
- 测试失败时不要靠删断言、加 `@Ignore`、放宽期望值来让它变绿。先读失败输出，找出根因再改。
- 命令从仓库根目录运行；Gradle 用 `./kotlin-tv/gradlew -p kotlin-tv ...`。Robolectric 测试首次运行需要联网下载依赖，下载慢或失败时重试，仍失败就停下来报告。
- 路径含中文，Windows 下用 Git Bash 时注意引号：`cd "E:/资料/Project/Self/hongguoTV"`。

### 设备验证（计划里标注"设备验证"的 Step 必须做，不能省略）

模拟器 `emulator-5554` 已经连接，`adb` 已在 PATH 中。按计划里的"设备验证配方"安装、临时修改分辨率、截图。要点：

- 安装的是 preview 版本，包名 `com.hongguotv.nativeapp.preview`。
- 截图保存到你自己的临时目录，保存后用能看图的工具打开确认，不要只凭命令成功就说"界面正常"。如果你的环境看不了图片，就在报告里明说"截图未能查看"，不要编造看到了什么。
- 每次验证结束必须执行：`adb shell wm size reset`、`adb shell wm density reset`；如果改过字体缩放，再执行 `adb shell settings put system font_scale 1.0`；如果关过网络，必须恢复（`adb shell svc wifi enable`、`adb shell svc data enable`）。
- 电视界面没有设备可验证，只有单元测试覆盖，最终报告里必须明确写"电视界面未在设备上验证"。

### 什么时候必须停下来问人

遇到下面任一情况，停止并汇报，不要自己拍板：

- 基线测试失败，或某个任务的测试在你合理修复后仍然失败。
- 计划里的接口、文件或函数名在代码里不存在，或与实际代码对不上。
- 发现需要改动电视分支的行为才能完成任务。
- 需要新增依赖，或需要修改计划"文件结构"之外的文件。
- 设备验证中发现的问题超出当前任务范围。

汇报格式：哪个任务的哪个 Step；你运行的命令；完整的错误输出（关键部分）；你的判断和建议。

### 提交规则

- 每个任务按计划里写的提交信息提交一次（Task 13 按计划要求分两次提交）。提交前运行 `git status` 与 `git diff --stat`，确认只包含当前任务的文件，没有那四个无关文件。
- 不要推送远程，不要创建 PR，不要改写已有提交，不要使用 `--no-verify`。

### 最终交付

全部 15 个任务完成后，给出一份报告，诚实地写：

1. 每个任务是否完成、对应的提交哈希。
2. 最终运行 `./kotlin-tv/gradlew -p kotlin-tv :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 的结果，以及测试数量与基线的对比。
3. 在手机竖屏、手机横屏播放、平板横屏三种配置下实际验证了哪些页面和操作，哪些没有验证。
4. 与规格不一致的地方（也要写进规格末尾的"实现偏差"小节）。
5. 已知问题和风险。没有做到的事情直接写"没有做"，不要含糊带过。
