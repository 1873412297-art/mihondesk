# 功能计划：失效封面自动修复（Cover 404 Auto-Repair）

日期：2026-09-22 · 需求来源：导入备份后部分书籍封面地址在 CDN 已 404（死链），需手动逐个点"刷新"
前置事实（已核实）：封面加载失败时 DesktopImageLoader.loadFromNetwork 拿到 404 后静默返回 null；详情页"刷新"会从图源重取详情与封面地址，可修复死链；批量盲探几千个 URL 有限流风险，不可取。

## 方案

### 1. 图片加载失败信号（基础层）
- `ImageRequest` 增加可选回调：`val onHttpError: ((code: Int) -> Unit)? = null`（默认 null，零影响）。
- `DesktopImageLoader.loadFromNetwork`：响应非 2xx 时除现有逻辑外调用 `request.onHttpError?.invoke(response.code)`（注意 request 参数透传；该回调在 Dispatchers.IO 触发，调用方需自行切线程/记状态）。
- `MangaCover` 组合件增加可选参数 `onCoverHttpError: ((Int) -> Unit)? = null`，透传给 ImageRequest；不传行为不变。

### 2. 会话级失效登记表（新文件）
`desktop-app/src/main/kotlin/mihon/desktop/image/CoverFailureRegistry.kt`
- object 单例（或挂到 DesktopImageLoader 伴生）：`record(mangaId: Long, url: String, code: Int)`、`fun snapshot(): Map<Long, String>`（mangaId→url，去重，仅记录 404/410）、`fun clear(mangaId: Long)`、`fun clearAll()`。
- 线程安全（ConcurrentHashMap）。只记 4xx（可重试的 5xx/网络错误不记，避免误伤临时故障）。

### 3. 详情页自动刷新（增强一）
- MangaDetailScreen 给封面 MangaCover 传 onCoverHttpError：code==404/410 时调 `presenter.onCoverLoadFailed(code)`。
- LibraryPresenter：会话内 `autoRefreshedCoverMangaIds: MutableSet<Long>`；`onCoverLoadFailed`：若 manga 图源已安装且 id 不在集合中 → 加入集合并触发一次现有"刷新详情"逻辑（复用 刷新按钮 的现成路径，勿新写网络代码）；已安装判断沿用 sourceManager.get(sourceId) != null。
- 防循环：刷新后若新封面仍 404，集合已含 id 不再触发。

### 4. 书架批量修复入口（增强二）
- CoverFailureRegistry.snapshot() 非空时，书架顶部栏（"立即检查书架更新"旁）出现次要按钮："修复失效封面 (N)"。
- 点击 → presenter 顺序对 registry 中的 mangaId 跑现有刷新详情（间隔 300ms 防限流；可取消——沿用现有任务取消模式，若无则允许关闭页面中断协程即可）。
- 每成功一部 clear(mangaId)；全部完成后 registry 空、按钮消失。刷新失败（网络/图源错误）的保留在 registry 并计入按钮计数。
- i18n 三语：coverRepairAction(N) / coverRepairRunning 等，recoveryText 模式。

### 5. 边界
- 仅记录 404/410；429/5xx/IOException 不登记（避免把限流当死链）。
- 本地源（sourceId=0）不触发自动刷新。
- 无图源（sourceManager.get==null）不触发（那是缺失图源功能的场景）。
- MangaCover 的 contentDescription 字母占位逻辑不变。

### 6. 测试
- CoverFailureRegistryTest：record/dedupe/仅 4xx/snapshot/clear。
- LibraryPresenter 测试（若现有 harness 可及）：onCoverLoadFailed 触发一次刷新、同 id 不重复、图源缺失不触发。
- 既有 DesktopImageLoader 测试不回归。

## 不做
- 不做全库盲探式封面体检（限流风险）
- 不改信任/安装流程
- 不动缺失图源横幅逻辑

## 关键文件
- image/DesktopImageLoader.kt（ImageRequest + onHttpError）
- image/CoverFailureRegistry.kt（新）
- ui/common/MangaCover.kt（参数透传）
- ui/library/MangaDetailScreen.kt + LibraryPresenter.kt（自动刷新 + 批量入口）
- i18n/DesktopStrings.kt（三语）
