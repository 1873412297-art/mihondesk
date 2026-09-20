# 桌面端 Tracker 凭证需求矩阵（11 个）

- 日期：2026-09-20
- 目的：为 1B 生产联调准备每个 tracker 的认证方式与所需用户凭证清单
- 范围：`desktop-app/src/main/kotlin/mihon/desktop/track/` 下全部 11 个 tracker（`DesktopTrackerManager.kt:111-121` 注册）及基类 `DesktopTracker.kt` / `JsonTokenTracker.kt`
- 登录 UI：`desktop-app/src/main/kotlin/mihon/desktop/ui/track/TrackerLoginDialog.kt`（三种 authType：CREDENTIALS / TOKEN / SERVER，见 `TrackerModels.kt:69-73`）

## 凭证矩阵

| Tracker（ID） | 认证类型 | 用户需提供什么凭证 | 浏览器 OAuth 步骤 | 需要真实账户 / 自托管实例 | 备注 |
|---|---|---|---|---|---|
| MyAnimeList（1） | OAuth2 Bearer token（authType=CREDENTIALS） | 用户自行获取的 MAL OAuth access token（粘贴到 password/token 字段） | 需要，但在 App 外部完成：MAL 官方 API 要求用户先在 myanimelist.net/apiconfig 注册自己的 API client（client id/secret），再走 authorization code flow 换 token；App 内无浏览器流程 | 真实 MAL 账户 | `MyAnimeListTracker.kt:58-74` 接受 `token`/`password`/`access_token` 键；登录用 `GET /users/@me` 验证。App 不含硬编码 client，用户必须自行注册 client 并生成 token |
| AniList（2） | OAuth implicit token（authType=TOKEN） | AniList access token | 需要，App 内可一键打开授权 URL（`authUrl`，见 `TrackerLoginDialog.kt:90-94`），用户在浏览器授权后从 redirect fragment 复制 token 回 App | 真实 AniList 账户 | `AniListTracker.kt:23-24` 硬编码 `client_id=3865&response_type=token`（与 Mihon 上游共用 client），**无需用户注册 OAuth client**；GraphQL API |
| Kitsu（3） | OAuth2 password grant（authType=CREDENTIALS） | Kitsu 用户名 + 密码；或直接提供已有 access token | 不需要 | 真实 Kitsu 账户 | `KitsuTracker.kt:167-170` 硬编码 CLIENT_ID/CLIENT_SECRET；密码立即在 `POST /api/oauth/token` 换成 access token，仅 token 被持久化（密码不落盘） |
| Shikimori（4） | OAuth2 Bearer token（authType=TOKEN） | Shikimori OAuth access token | 需要，但在 App 外部完成：Shikimori 要求用户先在 shikimori.io/oauth 注册 OAuth 应用（client id/secret）再走 authorization code flow 拿 token；`authUrl` 仅指向 `/oauth` 页面供参考 | 真实 Shikimori 账户 | `ShikimoriTracker.kt` 使用 GraphQL `/api/graphql` + v2 user_rates API；App 不含硬编码 client |
| Bangumi（5） | Personal Access Token（authType=TOKEN） | bangumi.tv Personal Access Token（`authUrl` 指向 `https://next.bgm.tv/demo/access-token` 申请页） | 不需要（可选打开申请页） | 真实 bangumi.tv 账户 | `BangumiTracker.kt:33-47` 用 `GET /v0/me` 验证 Bearer token；无需注册 OAuth client；不支持 REREADING 状态 |
| Komga（6） | Self-hosted + API key 或 HTTP Basic（authType=SERVER） | 自托管服务器 URL + 二选一：X-API-Key（无用户名时）或 username+password（HTTP Basic） | 不需要 | 需要自托管 Komga 实例及账户/API key | `KomgaTracker.kt:115-132`：有用户名走 `Authorization: Basic`，无用户名走 `X-API-Key` header；Komga 无内置评分，支持全部状态 |
| MangaUpdates（7） | 用户名+密码换 session token（authType=CREDENTIALS） | MangaUpdates 用户名 + 密码 | 不需要 | 真实 MangaUpdates 账户 | `MangaUpdatesTracker.kt:33-53`：`PUT /v1/account/login` 返回 `session_token`，仅 session token 持久化；不支持 REREADING |
| Kavita（8） | Self-hosted + API key 换 JWT（authType=SERVER） | 自托管服务器 URL + Kavita API key | 不需要 | 需要自托管 Kavita 实例 | `KavitaTracker.kt:35-42`：用 apiKey 调 `POST /api/Plugin/authenticate?pluginName=Tachiyomi-Kavita` 换 JWT；持久化的是 API key（`persistenceToken get() = apiKey`），JWT 过期后自动用 key 重新认证；不支持评分，仅 3 种状态 |
| Suwayomi（9） | Self-hosted + HTTP Basic 或 Bearer token（authType=SERVER） | 自托管服务器 URL + 可选 username+password（HTTP Basic），或仅 Bearer token（Suwayomi basic-auth token） | 不需要 | 需要自托管 Suwayomi 实例 | `SuwayomiTracker.kt:123-127`：有用户名走 Basic，否则 Bearer；持久化 secret；不支持评分，仅 3 种状态 |
| Hikka（10） | API token（authType=TOKEN，继承 `JsonTokenTracker`） | hikka.io API token（账户设置中生成） | 不需要 | 真实 hikka.io 账户 | `HikkaTracker.kt:16` 使用自定义 header `auth`（无 `Authorization`、无 `Bearer ` 前缀），`JsonTokenTracker.kt:21-22` 参数化；`GET /user/me` 验证 |
| MangaBaka（11） | API token（authType=TOKEN，继承 `JsonTokenTracker`） | mangabaka.org API token（账户设置中生成） | 不需要 | 真实 MangaBaka 账户 | `MangaBakaTracker.kt:16` 用标准 `Authorization: Bearer`（`JsonTokenTracker` 默认值）；`GET /v1/my/profile` 验证 |

## 关键发现摘要

### 用户必须自行注册 OAuth client 的服务（2 个）
- **MyAnimeList**：App 无硬编码 client id。用户须先在 MAL API 配置页注册自己的 API client，再用 authorization code flow（外部工具/App 外浏览器）换取 access token，粘贴进登录框。
- **Shikimori**：同样需用户自行在 shikimori.io 注册 OAuth 应用并离线完成 code flow 拿 token。

### 只需粘贴 token / 用户名密码，无需用户注册 client（9 个）
- **AniList**：唯一在 App 内有浏览器授权入口（`authUrl` 按钮），且用**硬编码 client_id=3865 的 implicit flow**，用户零注册。
- **Bangumi / Hikka / MangaBaka**：直接在网站设置页生成 Personal/Access token，粘贴即可。
- **Kitsu**：硬编码 client id/secret + password grant，用户只给用户名密码。
- **MangaUpdates**：用户名密码换 session token。
- **Komga / Kavita / Suwayomi**：自托管服务，凭证为 server URL + API key / 用户名密码 / auth token，由用户自己的实例颁发。

### 需要浏览器 OAuth 步骤的（按完成位置分）
- **App 内可引导**：AniList（一键打开授权 URL，implicit flow）。
- **App 外必须由用户完成**：MyAnimeList、Shikimori（code flow，需自行注册 client）。

### 联调测试账户需求
- 公网服务需真实账户 8 个：MAL、AniList、Kitsu、Shikimori、Bangumi、MangaUpdates、Hikka、MangaBaka。
- 自托管实例 3 个：Komga、Kavita、Suwayomi（1B 联调需准备三个可访问的实例及有效 API key/账户）。
- 凭证敏感提示：Kitsu/MangaUpdates 的密码仅用于换取 token，不落盘；持久化的均为 token / API key / server URL（`TrackerLoginInfo`，`TrackerModels.kt:75-81`）。
