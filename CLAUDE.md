# CLAUDE.md — mihondesk 编码约束

本文件对所有 AI 编码助手（Claude Code 等）和人类贡献者同样生效。违反下列任一条的改动不得合并；
detekt 已把其中可机器检查的部分设为构建失败项（见 `config/detekt/detekt.yml`）。

## 四条铁律

1. **单个 `.kt` 文件不得超过 400 行，单个函数不得超过 60 行。**
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
4. **禁止跨模块堆砌全局状态。**
   - 不新增顶层 `var`、可变 `object` 单例或伴生对象里的可变字段来跨模块传值；依赖通过构造参数注入
     （由 `DesktopRuntime` 组装）。
   - 模块依赖方向：`desktop-app → desktop-library-data / reader-core / extension-* / sync`，反向依赖一律禁止。

## 资源管理

- 所有 `AutoCloseable` / `Closeable`（QuickJs、ZipFile、7z 句柄、Process、Socket、FileChannel）必须用
  `.use { }` 或由持有者的 `close()` 明确释放；长生命周期持有者自己实现 `AutoCloseable`。
- 子进程（extension-host、webview-host）的生命周期必须绑定到父进程：父进程退出 / 管道断开时子进程必须退出。

## 仓库结构须知（改之前先读）

- `app/`、`baseline-profile/`、`data/`、`domain/`、`source-*`、`presentation-*`、`core*`、`i18n`、`icons`、
  `telemetry` 属于 **Android 端**，仍由 CI 构建 APK，并用于备份格式双向兼容验证（见 README“Android 回归”）。
  桌面端模块不依赖它们；不要在桌面代码里引用它们，也不要未经维护者确认删除它们。
- 桌面端入口：`desktop-app/src/main/kotlin/mihon/desktop/Main.kt`；运行时组装：`DesktopRuntime.kt`。
- 扩展宿主是独立进程：`extension-host/`（`--extension-host` 子命令启动），通过 `extension-sdk` 的 IPC 通信。
- JCEF 浏览器是按需启动的独立进程：`desktop-webview-host/`，由 `DesktopWebViewManager` 管理。

## 提交前

```bash
./gradlew spotlessApply detekt :desktop-app:test
```

detekt 失败时修代码，不要改阈值、不要加 `@Suppress`（除非在 PR 描述里说明理由）。
