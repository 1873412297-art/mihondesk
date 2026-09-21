# 1B Tracker 生产联调 — Bangumi 首行记录

日期：2026-09-21 · 工作项：Workstream 1B 生产矩阵执行（计划文档 §2.2 步骤 4）
账户：Bangumi（番组计划）· 用户 `滴`（uid 1020247）· 凭证类型：Personal Access Token（`TrackerAuthType.TOKEN`）
应用：mihondesk 0.2.18 + 当日构建（含本会话三项网络层修复）
截图：`2026-09-21-tracker-bangumi/*.png`

## 生产矩阵（计划 §2.2 步骤 4 表格）

| 服务 | 登录 | 搜索绑定 | 进度写入 | 退出重进 | 刷新 | 解绑 | 结论 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Bangumi | ✅ | ✅ | ✅（缺陷修复后复测：阅读器退出触发 TrackOnReadSync，远端 ep_status 0→1） | ✅ | ✅（重新打开对话框即重读本地+远端状态） | ✅（本地解绑；远端收藏不删除，API 无删除端点） | **通过（6/6，2026-09-22 复测闭环）** |

## 执行记录

1. **Token 预验证（curl）**：`GET /v0/me` → 200，`username=1020247`。
2. **登录红态**：0.2.18 打包版登录报"登录失败"。
3. **根因 1（登录）**：okhttp `BridgeInterceptor` 自动发送 `User-Agent: okhttp/<version>`，Bangumi Cloudflare 对该 UA 返 403（curl 对照：`okhttp/4.12.0` UA → 403；`MihonW` UA → 200）。`BangumiTracker.authenticated()` 未设 UA → 落到 okhttp 默认。**0.2.18 起一直存在**（685d91d2a）。修复：`defaultTrackerHttpClient()` 加 UA 应用拦截器（未带 UA 时补 `MihonW/0.1 (Windows)`）+ 契约测试加 UA 断言（原 mock 不校验 UA，测试全绿但生产挂）。修复后登录 ✅（界面显示"已登录为 1020247"）。
4. **根因 2a（搜索，环境层）**：宿主机系统代理 127.0.0.1:7897（Clash/mihomo）掐断 okhttp 发往 api.bgm.tv 的请求（`unexpected end of stream`，curl 同代理正常；**强制 HTTP/1.1 无效**；关系统代理后立即 200）。tracker 客户端走系统代理但应用无 tracker 级代理控制（"图源网络"设置不作用于 tracker）——记为产品改进项。测试期间临时关闭系统代理，测毕已恢复。
5. **根因 2b（搜索/写入，okhttp 5 行为）**：`TRACKER_JSON_MEDIA_TYPE` 改为精确 `application/json` 后仍被 Bangumi 415。反射实验定位：**okhttp 5.5.0 `String.toRequestBody(MediaType)` 自动追加 `; charset=utf-8`**（`byte[]` 变体不追加）。Bangumi collections 端点对 content-type 精确匹配（curl 对照：带 charset → 415；精确 → 202）。修复：track 包 12 处 JSON 请求体统一走 `jsonRequestBody()`（`toByteArray(UTF_8).toRequestBody(...)`）。修复后搜索 ✅（结果列表出航海王等）、绑定写入 ✅（POST /collections/3510 → Bangumi 侧确认收藏存在，`updated_at` 刷新）。
6. **进度写入断点（两个产品缺陷，均已在 DB/界面取证）**：
   - **缺陷 A：TrackingDialog"编辑"按钮无响应**。绑定后点击 Bangumi 行"编辑"4 次（不同精确坐标），EditTrackDetailsDialog 均不弹出（截图 03 为点击前状态）。手动改章节/评分/状态的路径整体不可用。
   - **缺陷 B：TrackOnReadSync 静默跳过非正章节号**。阅读器读完章节（本地已正确标"已读"），但远端 ep_status 不变。查库取证：`chapter.chapter_number = -1.0`（单本同人本常见），`TrackOnReadSyncService.onChapterRead` 的守卫 `if (chapterNumber <= track.lastChapterRead) continue` 对 -1.0 ≤ 0.0 永远跳过 → 离线队列空、远端无写入、无任何用户提示。
7. **退出重进**：重启应用后 Bangumi 仍"已登录"，绑定仍显示"航海王 • 阅读中"（截图 04）✅。重新打开跟踪对话框即完成状态刷新 ✅。
8. **解绑**：点击"解绑"后本地 `tracking` 表清空（SQL 取证）✅。远端收藏保留（tracker 惯例）；Bangumi v0 API 无收藏删除端点（DELETE 404 实测），**需用户在 bangumi.tv 手动移除"航海王"收藏**（1 次点击）。

## 修复清单（本会话已提交）

- `TrackerApiSupport.kt`：UA 拦截器 + 强制 HTTP/1.1 + `TRACKER_JSON_MEDIA_TYPE` 精确化 + `jsonRequestBody()` helper（去调试插桩）。
- 9 个 tracker 文件 12 处调用点改走 `jsonRequestBody()`。
- `DesktopBangumiTrackerApiTest.kt`：补 UA 断言 + 改用默认客户端（覆盖拦截器路径）。
- 回归：35 项 tracker 测试全绿。

## 产品缺陷（当日发现当日修复，2026-09-22 提交）

1. ~~TrackingDialog"编辑"按钮无响应~~ **已修复**：根因是主 AlertDialog 与编辑 AlertDialog 同时组合时的桌面端弹层问题；修复为三个对话框互斥组合（edit > search > main）。修复验证：AniList 编辑对话框正常弹出并完成进度写入（见 2026-09-22-tracker-production-anilist.md）。
2. ~~TrackOnReadSync 静默跳过非正章节号~~ **已修复**：`onChapterRead` 将 chapterNumber <= 0 映射为 1.0 后再比较/写入。修复验证：单本（-1.0）读完 → 远端 ep_status 0→1。新增 3 个单测（-1.0 同步 1 / 幂等 / 正章节回归）。
3. tracker 客户端代理不可控（Clash 类代理下全挂）——**未修**，产品改进项见下节。

## 分级结论

**通过（2026-09-22 复测闭环 6/6）**：登录、搜索绑定、退出重进、刷新、解绑全链通过；进度写入在修复缺陷 A（TrackingDialog 编辑按钮无响应）与缺陷 B（TrackOnReadSync 跳过非正章节号）后复测通过——阅读器读完单本（chapter_number=-1.0）→ 退出触发同步 → Bangumi 远端 ep_status 0→1（updated_at 刷新）、离线队列 drain 为空。两项缺陷修复见提交 1B-defect-fixes（TrackingDialog 单对话框互斥重构 + onChapterRead 非正章节号映射为 1.0 + 3 个新单测）。

### 遗留产品改进项（非缺陷，未修）

- tracker 客户端代理不可控（Clash 类代理下 okhttp 请求被系统性掐断，Bangumi/AniList 均复现）——建议复用/扩展"图源网络"代理设置至 tracker 客户端。
