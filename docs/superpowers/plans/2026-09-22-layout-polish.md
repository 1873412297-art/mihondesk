# 布局排版优化计划（mihondesk 0.2.18+）

日期：2026-09-22 · 类型：UI/排版渐进优化（不改主题系统、不改功能逻辑）
依据：2026-09-22 逐屏截图体检（书架/设置常规/进度记录/高级与诊断/漫画详情）

## 体检结论（按严重度）

| # | 区域 | 问题 |
|---|---|---|
| 1 | 主导航栏（左竖条） | 仅"书架"有文字标签，其余 4 个图标裸奔，用户无法识别图源/历史/下载/设置入口 |
| 2 | 设置页内容区 | 左对齐窄栏 + 右侧大面积空白；分区（界面语言/无痕模式/应用信息）无卡片容器、无分隔，视觉层级弱；说明文字与控件宽度不一致 |
| 3 | 进度记录页 | 11 个 tracker 行纯文本：无服务标识、无分组（公网 vs 自托管）、无容器/分隔、通栏大面积空白；状态文案小且弱 |
| 4 | 书架页顶部 | 右侧操作区拥挤混乱：3 个导入/更新按钮 + "180dp"滑条 + 筛选/排序/多选挤作一团，无主次 |
| 5 | 漫画详情面板 | 顶部 5 个小描边按钮（移出书架/刷新/编辑信息/分类/进度记录）无主次；Description 原样显示 `**Groups**:` 等 Markdown 符号；标签/分类云无小节标题 |
| 6 | 全局 | 缺统一"区块卡片"模式；标题/说明/正文层级未按 Material3 type scale 规范 |

## 目标

不改配色与主题引擎，只调整结构、容器、间距、层级，使每个界面达到：导航可识别、分区有容器、操作有主次、宽度利用合理。

## 实施方案（P0→P2，单窗口可验证）

### P0 主导航标签化（DesktopShell.kt）
- 5 个主功能项统一 `icon + label`（书架/图源/历史/下载/任务，设置与关于在底部沿用现状）。
- 标签样式：图标下 10sp caption，选中项 = 图标+标签着色 + 圆角容器高亮（NavigationRailItem 或自定义 Surface），不选中时 onSurfaceVariant。
- 验收：每个图标可辨、中英日切换不截断。

### P1 设置页内容区卡片化（SettingsScreen.kt）
- 右侧内容列：`Modifier.widthIn(max = 880.dp).fillMaxWidth()`，每个分区一张 `Card(shape = 12dp)`：标题（titleMedium）+ 说明（bodySmall/onSurfaceVariant）+ 内容，卡间距 16dp。
- 常规页：界面语言卡、无痕模式卡、应用程序信息卡；开关行保持右对齐。
- 高级与诊断页沿用同一卡片模式（已有分区直接套容器，不重组内容）。
- 说明文字与控件同宽，去掉说明文字超长行（换行宽度=卡宽）。

### P1 进度记录页结构化（SettingsScreen.kt 进度记录分区）
- 每个 tracker 一行改为卡片行：左起 28dp 圆形首字母徽章（服务名首字母，surfaceVariant 底）→ 服务名（bodyLarge/SemiBold）+ 状态文案（已登录为 xxx = primary；未登录 = outline）→ 右侧 登录/退出登录/连接 按钮（FilledTonalButton）。
- 分组小标题："公网服务"（MyAnimeList/AniList/Kitsu/Shikimori/Bangumi/MangaUpdates/Hikka/MangaBaka）与"自托管实例"（Komga/Kavita/Suwayomi），labelMedium onSurfaceVariant。
- 卡片间距 8dp，组间距 20dp。

### P2 书架顶部操作区重排（MihonDesktopApp.kt）
- 第一行右侧：主按钮"立即检查书架更新"（FilledButton）+ 两个导入按钮降级为 OutlinedButton。
- "180dp"封面尺寸滑条与"筛选与排序/多选"合并为第二行工具条（Surface tonal，左滑条右两按钮），与卡片网格对齐。
- 布局模式与筛选 chips 保持现状。

### P2 漫画详情面板优化（MihonDesktopApp.kt 详情面板）
- 顶部操作行：进度记录提为 FilledTonalButton（最右，识别为常用入口），移出书架/刷新/编辑信息/分类保持 OutlinedButton 一组，间距 8dp。
- Description 文本：去掉原始 `**` Markdown 加粗符号（简单替换 `**([^*]+)**` → `$1`，不引入完整 Markdown 解析器）。
- 标签区加小节标题 "标签"（labelLarge），Categories 区加 "分类"（labelLarge），与现有内容对齐。

## 不做（明确排除）
- 主题/配色/暗色逻辑、字体系统、间距 token 全局重构
- 阅读器、WebView、图源浏览页布局
- 任何功能/状态/持久化逻辑变更（仅纯 UI 结构调整）
- 图标资源引入（badge 用文字首字母，不下载服务 logo）

## 关键文件
- desktop-app/src/main/kotlin/mihon/desktop/ui/DesktopShell.kt（导航栏，477 行）
- desktop-app/src/main/kotlin/mihon/desktop/ui/settings/SettingsScreen.kt（设置全页，2326 行，进度记录分区在其内）
- desktop-app/src/main/kotlin/mihon/desktop/ui/MihonDesktopApp.kt（书架顶栏 + 漫画详情面板，1262 行）
- i18n：新文案沿用现有 recoveryText(en, zh-CN, zh-TW) 三语风格

## 验证（轻量，主线路径）
1. `./gradlew :desktop-app:compileKotlin` + 相关现有单测不回归。
2. 打包重启，逐屏截图对比体检表 6 项问题是否消除。
3. testTag 变更最少化——现有 E2E/UI 测试若引用被改动的结构，同步修正测试。
