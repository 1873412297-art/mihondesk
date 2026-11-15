# CLAUDE.md — mihondesk 编码约束

本文件对所有 AI 编码助手（Claude Code 等）和人类贡献者同样生效。违反下列任一条的改动不得合并；
detekt 已把其中可机器检查的部分设为构建失败项（见 `config/detekt/detekt.yml`）。

## 四条铁律

1. **单个 `.kt` 文件不得超过 400 行，单个函数不得超过 60 行；`ui/` 下的 `@Composable` 改为认知复杂度不超过 15。**
   - Compose 是声明式组件树、一参一行，行数不代表难读，所以 `@Composable` 不受 60 行限制，改由 detekt 的
     `CognitiveComplexMethod`（只作用于 `desktop-app/.../ui/`）把关。不要为了凑行数把组件拆成一串互相传参的小函数。
   - 修 Bug 时不准“就近塞变量 + if-else”。如果修复需要在已超限的文件里加代码，先把相关逻辑拆到新文件，再改。
   - 历史超限文件登记在 `config/file-length-baseline.txt`，detekt 的历史函数签名登记在
     `config/detekt/baseline/<module>.xml`。**只允许删除条目或收紧行数上限，不允许新增。**
     不要运行 `detektBaseline` 来“洗白”新违规。
2. **顶层 `@Composable`（`MihonDesktopApp`、`MainAppWindow`、各 `*Screen`）中禁止新增业务级
   `remember { mutableStateOf(...) }`。**
   - 业务状态放进对应功能域的 Presenter / ScreenModel，以不可变 `UiState` + `StateFlow` 暴露；Screen 只接收
     `state` 并通过 `onAction` 回传事件。
   - 只允许纯 UI 局部状态（悬停、展开、焦点、动画）留在 Composable 里，并且放在最小的那个子组件中。
   - 高频变化的值（阅读页码、下载进度、悬停）以 `() -> T` lambda 或独立小组件传递，避免整棵树重组。
3. **禁止空的 `catch`，禁止静默吞异常。**
   - 至少 `DesktopLogger.warn(tag, message, error)`；致命问题（数据库迁移失败、数据目录不可写）必须给用户可见提示。
   - `catch (_: CancellationException)` 必须重新抛出。`runCatching { }` 的结果不得直接丢弃，要么
     `.onFailure { log }`，要么写明为什么可以忽略。
   - 挂起代码（`suspend fun`、`launch`/`withContext` 等协程块）里的宽泛 `catch (e: Exception)` / `Throwable`
     会连取消一起吞掉：前面先加 `catch (e: CancellationException) { throw e }`。包住挂起调用时不要用
     `runCatching`，用 `mihon.desktop.logging.runSuspendCatching`。（detekt 的普通 `detekt` 任务没有类型信息，
     查不出这条，只能靠评审。）
4. **禁止跨模块堆砌全局状态。**
   - 不新增顶层 `var`、可变 `object` 单例或伴生对象里的可变字段来跨模块传值；依赖通过构造参数注入
     （由 `DesktopRuntime` 组装）。
   - 模块依赖方向：`desktop-app → desktop-library-data / reader-core / extension-* / sync`，反向依赖一律禁止。
   - Presenter 通过构造参数接收 `ioDispatcher: CoroutineDispatcher = Dispatchers.IO` 并传给内部协作类，
     不要写死 `Dispatchers.IO`（参照 `LibraryPresenter`、`LocalMangaDetailPresenter`）。测试传
     `StandardTestDispatcher`；Presenter 跑在 `backgroundScope` 时用 `runCurrent()` 推进，
     `advanceUntilIdle()` 不执行后台任务。

## 资源管理

- 所有 `AutoCloseable` / `Closeable`（QuickJs、ZipFile、7z 句柄、Process、Socket、FileChannel）必须用
  `.use { }` 或由持有者的 `close()` 明确释放；长生命周期持有者自己实现 `AutoCloseable`。
- 子进程（extension-host、webview-host）的生命周期必须绑定到父进程：父进程退出 / 管道断开时子进程必须退出。

## 仓库结构须知（改之前先读）

- 本仓库只保留桌面端。不要把上游 Mihon 的 Android 模块（`app`、`data`、`domain`、`source-*`、
  `presentation-*`、`core*`、`i18n`、`icons`、`telemetry`、`fastlane` 等）再加回来。备份格式兼容由
  `desktop-library-data` 的契约测试覆盖，而不是在这里编译 Android 工程。
- `gradle/build-logic` 只保留 spotless 约定插件。不要再引入 AGP（`com.android.tools.build:gradle`）或
  Android/KMP 约定插件：它们没有模块使用，却会让访问不了 Google Maven 的环境连 `./gradlew help` 都失败。
- 桌面端入口：`desktop-app/src/main/kotlin/mihon/desktop/Main.kt`；运行时组装：`DesktopRuntime.kt`。
- 扩展宿主是独立进程：`extension-host/`（`--extension-host` 子命令启动），通过 `extension-sdk` 的 IPC 通信。
- JCEF 浏览器是按需启动的独立进程：`desktop-webview-host/`，由 `DesktopWebViewManager` 管理。

## 提交前

Windows（与 CI 的 Windows job 一致，`desktop-app` 的测试依赖打包的 codec 和 Win32 API）：

```bash
./gradlew spotlessApply detekt :desktop-app:test
```

Linux / macOS：`desktop-app:test` 里有一批只能在 Windows 通过的测试（`magick.exe`、`kernel32`、超过 255 字节的
文件名），只跑与平台无关的模块；locale 必须是 UTF-8（测试夹具有中文路径）。本机没有 JetBrains Runtime 21
（且 Gradle 无法自动下载）时，用 `MIHON_JBR_HOME` 指一个 JDK 21，再加 `-x :desktop-webview-host:detekt`
（JCEF 宿主只能用 JBR 配置）：

```bash
LC_ALL=C.UTF-8 ./gradlew spotlessApply detekt checkFileLength :reader-core:test :sync:test :extension-sdk:test :extension-host:test :desktop-library-data:test
```

detekt 失败时修代码，不要改阈值、不要加 `@Suppress`（除非在 PR 描述里说明理由）。
