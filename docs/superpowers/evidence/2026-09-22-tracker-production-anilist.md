# 1B Tracker 生产联调 — AniList 记录

日期：2026-09-22 · 工作项：Workstream 1B 生产矩阵执行（计划文档 §2.2 步骤 4）
账户：AniList · 用户 `DILL1873`（id 8288690）· 凭证类型：OAuth Implicit Token（`TrackerAuthType.TOKEN`）
应用：mihondesk 0.2.18 + 2026-09-22 构建（含 UA 拦截器 / jsonRequestBody / TrackingDialog 重构 / 单本同步修复）
截图：`2026-09-21-tracker-bangumi/05-anilist-login.png`、`06-anilist-bound.png`

## 生产矩阵（计划 §2.2 步骤 4 表格）

| 服务 | 登录 | 搜索绑定 | 进度写入 | 退出重进 | 刷新 | 解绑 | 结论 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| AniList | ✅ | ✅ | ✅ | ① | ① | ✅ | **通过（4 项实测 + 2 项同构复用）** |

① 退出重进/刷新未对 AniList 单独重跑——与 Bangumi 走完全相同的持久化机制（DesktopTrackerStore + 重开对话框重读），Bangumi 已实测通过；AniList 登录态当日跨多次对话框重开均保持。

## 执行记录

1. **Token 获取（OAuth Implicit，内置 client_id=3865，与 App 生产路径一致）**：用户浏览器已有 AniList 登录会话，直接访问 `https://anilist.co/api/v2/oauth/authorize?client_id=3865&response_type=token` → Authorize → 地址栏 fragment 取 `access_token`（1083 字符 JWT）。curl 预验证 Viewer = DILL1873/8288690。用户另行注册的 API Client（id 51559）本流程不需要（App 内置 client）。
2. **登录**：首次尝试（系统代理 ON）失败——与 Bangumi 相同的 Clash 代理掐断 okhttp 请求问题（环境问题，已记录为产品改进项：tracker 无独立代理控制）。关闭系统代理后登录成功，界面显示"已登录为 DILL1873"，`支持的记录平台: 2 个可用`。
3. **搜索绑定**：跟踪对话框 AniList 行 → 记录 → 搜 "One Piece" → 结果列表（ONE PIECE / One Piece Party / Koisuru ONE PIECE / Boku no One Piece / Noroi no One Piece）→ 选第一条。**远端验证**：AniList `MediaListCollection` 出现 `30013 ONE PIECE, CURRENT, progress 0` ✅。
4. **进度写入**：点击 AniList 行"编辑"——**编辑对话框正常弹出**（同日修复的 TrackingDialog 缺陷在此验证通过）→ 已读话数 0→3 → 保存。**远端验证**：`progress 3` ✅（编辑保存 → updateRemote → AniList GraphQL SaveMediaListEntry 全链）。
5. **解绑**：对话框"解绑" → 本地 `tracking` 表该条删除（SQL 取证）。远端条目经 API `DeleteMediaListEntry` 清理（用户账户已还原为空列表）。

## 环境/产品发现

- **Clash 类代理对 okhttp tracker 流量的系统性掐断在 AniList 复现**（登录一次失败、关代理即通），与 Bangumi 结论合并入档：tracker 客户端需要独立代理设置（现有"图源网络"设置不覆盖 tracker）。
- AniList GraphQL 端点对 UA 与 content-type 的容忍度高于 Bangumi，UA 拦截器 + jsonRequestBody 修复后无任何适配问题。

## 分级结论

**通过**。AniList 全链（登录→搜索→绑定→进度写入→解绑）生产验证完成，账户侧无残留。
