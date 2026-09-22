# 功能计划：缺失图源检测与引导安装（Missing Source Resolver）

日期：2026-09-22 · 需求来源：导入备份后部分书籍因图源扩展未安装导致封面不显示、书籍打不开
触发场景示例：导入记录含 sourceId=560822675588930101（NHentai.xxx ZH），但该扩展未安装

## 已确认的技术事实（写代码前已核实）

1. **反向映射可行**：`ExtensionStoreItem.sources: List<SourceDescriptor>`（含 id/name/lang/className），仓库索引（index.pb/index.min.json）解析时填充（ExtensionStoreService.kt:330/426）。`SourceDescriptor.id` 即图源 id，可直接与 `MangaRecord.sourceId` 匹配。
2. **id 生成确定性**：`TachiyomiExtensionConverter.generateSourceId(name, lang)` = `md5("${name.lowercase()}/$lang/1")` 前 8 字节大端 & Long.MAX_VALUE——即使索引未带 id，也可离线计算候选 id（作为兜底路径）。
3. **本地源**：sourceId=0（Local source / BundledLocalSource）必须跳过。
4. **安装通道现成**：`DesktopExtensionInstaller.downloadAndInstall(...)` 含信任弹窗、sha256 校验、安装注册，直接复用。
5. **检测条件**：`DesktopSourceManager.get(sourceId) == null` 且 sourceId != 0。

## 方案

### 1. 核心解析器（新文件）
`desktop-app/src/main/kotlin/mihon/desktop/extension/MissingSourceResolver.kt`
- `data class MissingSourceInfo(val sourceId: Long, val mangaCount: Int, val extension: ExtensionStoreItem?)`
- `fun findMissingSources(mangas: List<MangaRecord>, installedSourceIds: Set<Long>, available: List<ExtensionStoreItem>): List<MissingSourceInfo>`
  - 过滤：sourceId != 0、不在 installedSourceIds
  - groupBy(sourceId) 计数
  - 对每个 sourceId 在 available 里找 `sources.any { it.id == sourceId }` 的扩展；找不到则 extension=null
- `suspend fun ensureAvailableExtensions(): List<ExtensionStoreItem>`：若 presenter 缓存为空则经 storeService 拉取一次（失败返回空列表，静默）

### 2. 检测点 A：漫画详情页
- MangaDetailPresenter：详情加载时若 `sourceManager.get(manga.sourceId) == null && sourceId != 0`，state 增加 `missingSource: MissingSourceInfo?`（异步经 resolver 解析扩展名）。
- MangaDetailScreen：missingSource != null 时在封面/标题区上方渲染横幅：
  - 文案：「图源未安装：{extName ?: "未知扩展"}（{mangaCountInLibrary} 部漫画受影响）」
  - 按钮 [下载并安装]（ext != null 时可用）/ [忽略]（本次会话内不再提示该 manga）
  - 点击下载 → installer.downloadAndInstall（信任弹窗流程不变）→ 成功后自动重载详情（presenter.refreshDetails()）
- 扩展为 null（仓库找不到）时横幅提示「图源在扩展仓库中未找到」，无按钮。

### 3. 检测点 B：导入后批量扫描
- 备份导入完成流程（AndroidBackupImporter 调用方 / 书架 presenter 导入入口）：导入成功后对书架跑一次 resolver。
- 结果 >0 时弹一次性 AlertDialog：「检测到 N 部漫画缺少图源（M 个扩展可安装）：{扩展名列表}」[全部安装] [稍后]
  - [全部安装]：逐个 downloadAndInstall（复用现有并发/信任逻辑，信任弹窗每个扩展一次）
  - 找不到扩展的 sourceId 在对话框尾部一行小字：「X 部漫画的图源在仓库中未找到」

### 4. 边界与幂等
- 已安装但被停用（isSourceEnabled=false）不算缺失
- 仓库未配置或索引拉取失败：静默跳过，不弹任何提示
- 用户点 [忽略] 只记会话内状态，不写偏好（避免状态泄漏）
- 安装失败沿用现有 errorMessage 通道

### 5. i18n（recoveryText 三语）
- missingSourceBannerTitle / missingSourceBannerAction / missingSourceInstallAll / missingSourceNotFound 等，en + zh-CN + zh-TW

### 6. 测试
- MissingSourceResolverTest：命中单个扩展、同扩展多图源、未命中（null）、sourceId=0 跳过、已安装跳过、多漫画计数
- MangaDetailPresenter 测试：source 缺失时 state.missingSource 填充；安装成功后清除
- 新增测试若涉及 installer，复用现有 fake/拦截模式

### 7. 明确不做
- 不自动静默安装（必须用户点击确认）
- 不做扩展版本/降级判断
- 不处理图源 id 在仓库找不到时的外部搜索（仅提示）

## 关键文件
- 新增：extension/MissingSourceResolver.kt
- 改：ui/library/MangaDetailPresenter.kt、ui/library/MangaDetailScreen.kt（详情横幅）
- 改：备份导入完成后的入口（Android 备份导入调用方，导入成功回调处）
- 改：i18n/DesktopStrings.kt（三语文案）
- 参考：BrowsePresenter（availableExtensions 缓存）、DesktopExtensionInstaller.downloadAndInstall

## 验收（主线）
导入含未装图源的备份 → 详情页出横幅 → 点击下载安装 → 信任 → 图源可用、封面/章节加载；批量导入后弹汇总对话框，全部安装后书架恢复正常。
