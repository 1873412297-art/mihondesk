# 桌面端 Tracker 生产验证用户操作手册（User Action Runbook）

- **日期**：2026-09-20
- **工作项**：Workstream 1B 生产联调验证（Tracker Production Verification）
- **目标受众**：拥有公网账户或自托管实例的联调人员 / 测试用户
- **目标成果**：用户根据本清单在单次会话（One Sitting）内完成凭证收集，并将标准化、即插即用（Paste-Ready）的凭证发送给联调操作员（Operator）

---

## 快速执行总览表

| # | 跟踪器（Tracker） | 类别 | 凭证获取途径 | 发送给操作员的凭证格式 | 预估耗时 |
|---|---|---|---|---|---|
| 1 | **MyAnimeList** | 公网 | 注册 Client + 外部 OAuth 换 Token | `access_token` (+ 可选 `username`) | 5-10 分钟 |
| 2 | **AniList** | 公网 | 内置授权 URL（Implicit Flow） | `access_token` (+ 可选 `username`) | 1-2 分钟 |
| 3 | **Kitsu** | 公网 | 账户用户名 + 密码（Password Grant） | `username` + `password` | 1 分钟 |
| 4 | **Shikimori** | 公网 | 注册 Client + 外部 OAuth 换 Token | `access_token` (+ 可选 `username`) | 5-10 分钟 |
| 5 | **Bangumi** | 公网 | Personal Access Token 申请页 | `access_token` (PAT) | 1-2 分钟 |
| 6 | **MangaUpdates** | 公网 | 账户登录（用户名 + 密码） | `username` + `password` (或 session token) | 1 分钟 |
| 7 | **Hikka** | 公网 | 个人账户设置页 API Token | `api_token` | 1-2 分钟 |
| 8 | **MangaBaka** | 公网 | 账户个人中心生成 PAT (`mb-...`) | `api_token` (PAT `mb-...`) | 1-2 分钟 |
| 9 | **Komga** | 自托管 | 实例设置页 API Key 或用户密码 | `server_url` + (`api_key` 或 `username`+`password`) | 2 分钟 |
| 10 | **Kavita** | 自托管 | 实例用户设置页 API Key | `server_url` + `api_key` | 2 分钟 |
| 11 | **Suwayomi** | 自托管 | 实例 WebUI / Basic 认证 | `server_url` (+ 可选 `username`+`password` 或 `token`) | 2 分钟 |

---

## URL 验证与状态检查矩阵（Curl HEAD 验证）

所有 URL 均在 2026-09-20 使用 `curl.exe -I -s --max-time 10` 进行 HEAD 探测验证。探测结果与修正说明如下：

| 服务 | 探测 URL | HTTP 状态码 | 探测观察与修正说明 |
|---|---|---|---|
| **MyAnimeList** | `https://myanimelist.net/apiconfig` | `303 See Other` | 未登录时重定向至 `https://myanimelist.net/login.php?from=%2Fapiconfig`；登录后正常展示 API 控制台。有效。 |
| **AniList** | `https://anilist.co/api/v2/oauth/authorize?client_id=3865&response_type=token` | `302 Found` | 重定向至 `https://anilist.co/login?...`；登录后即弹出授权页面。与 `AniListTracker.kt:23-24` 一致。有效。 |
| **Kitsu** | `https://kitsu.app` | `200 OK` (带浏览器 UA) / `403` (默认 curl) | 默认 curl UA 会触发 Cloudflare 403，携带标准浏览器 User-Agent 响应 200 OK。官网根域名 `https://kitsu.io` 直接响应 `200 OK`。有效。 |
| **Shikimori** | `https://shikimori.io/oauth` | `200 OK` | 官方 OAuth 说明页正常响应 200。 |
| **Shikimori (Apps)** | `https://shikimori.io/oauth/applications` | `200 OK` | OAuth 应用管理列表正常响应 200（`shikimori.one` 域 301 重定向至 `shikimori.io`）。创建应用路径为 `https://shikimori.io/oauth/applications/new`。有效。 |
| **Bangumi (Dev)** | `https://bangumi.tv/dev/app` | `200 OK` | 开发者应用列表正常响应 200。有效。 |
| **Bangumi (PAT)** | `https://next.bgm.tv/demo/access-token` | `302 Found` | 未登录重定向至 `/demo/login?backTo=...`；登录后为官方演示 PAT 生成页。与 `BangumiTracker.kt:27` 一致。有效。 |
| **MangaUpdates (Web)** | `https://www.mangaupdates.com` | `429 Too Many Requests` | 网站受到 Envoy/Nginx 严格限流保护，非浏览器/未鉴权请求返回 429。测试人员请在浏览器正常打开。 |
| **MangaUpdates (API)** | `https://api.mangaupdates.com` | `200 OK` | OpenAPI 规范与文档页正常响应 200。有效。 |
| **Hikka** | `https://hikka.io` | `200 OK` | 官网首页响应 200。 |
| **Hikka (Settings)** | `https://hikka.io/settings` | `307 Temporary Redirect` | 未登录重定向至 `/login`，登录后进入个人设置页。有效。 |
| **MangaBaka (Settings)** | `https://mangabaka.org/settings` | `404 Not Found` | **【修正】** 原预期路径 404。经服务文档响应头 `Link: <https://mangabaka.org/data/api>` 及 OpenAPI `api.json` 查证，MangaBaka 为 SvelteKit 单页应用，登录入口为 `https://mangabaka.org/auth?action=login`，登录后在右上角用户个人资料（Profile）中生成 Personal Access Token（格式为 `mb-...`）。文档页 `https://mangabaka.org/data/api` 响应 200 OK。 |

---

## 8 个公网 Tracker 凭证获取操作手册（Checklist）

### 1. MyAnimeList (MAL)

- **认证类型**：`TrackerAuthType.CREDENTIALS`（`MyAnimeListTracker.kt:44`）
- **客户端机制说明**：App 内部未内置硬编码 OAuth Client（`MyAnimeListTracker.kt:58-62` 仅从 password/token/access_token 字段提取 Bearer Token，并在 `GET https://api.myanimelist.net/v2/users/@me` 验证）。因此用户必须在 MAL 注册个人客户端并完成一次外部授权码换 Token。

#### 操作步骤：
- [ ] **1.1 登录 MAL**：访问 [https://myanimelist.net](https://myanimelist.net) 登录你的账户。
- [ ] **1.2 注册客户端**：打开 [https://myanimelist.net/apiconfig](https://myanimelist.net/apiconfig) 并点击 **"Create ID"**（或 "Create App"）。
  - **App Name**：填写任意名称，例如 `Mihon-Desktop-Test`
  - **App Type**：选择 `Web` 或 `Other`
  - **Redirect URL**：填写 `http://localhost:8080`（推荐）或自定义协议（如 `mihon://oauth`）
  - **Description**：填写 `Personal test client for manga tracking`
  - **Commercial / Non-Commercial**：选择 `Non-commercial`
  - 勾选同意条款后保存，获取 **Client ID** 和 **Client Secret**。
- [ ] **1.3 获取 Authorization Code**：
  在浏览器访问以下链接（将 `{CLIENT_ID}` 替换为你申请到的 Client ID，`code_challenge` 填任意 43~128 位字符串，如 `1234567890123456789012345678901234567890123`）：
  ```text
  https://myanimelist.net/v1/oauth2/authorize?response_type=code&client_id={CLIENT_ID}&code_challenge=1234567890123456789012345678901234567890123&code_challenge_method=plain
  ```
  点击 **"Authorize"**，浏览器将跳转至 `http://localhost:8080/?code={AUTHORIZATION_CODE}`（网页打不开没关系，从浏览器地址栏复制 `code=` 后的参数字符串）。
- [ ] **1.4 换取 Access Token**：
  打开终端（PowerShell 或 CMD）执行以下命令换取 Token：
  ```bash
  curl.exe -X POST "https://myanimelist.net/v1/oauth2/token" \
    -d "client_id={CLIENT_ID}" \
    -d "client_secret={CLIENT_SECRET}" \
    -d "grant_type=authorization_code" \
    -d "code={AUTHORIZATION_CODE}" \
    -d "code_verifier=1234567890123456789012345678901234567890123"
  ```
  响应 JSON 中的 `"access_token"` 即为目标凭证。
- [ ] **1.5 发送给操作员**：
  ```text
  [MAL]
  Username: <你的 MAL 用户名>
  Access Token: <生成的 access_token 字符串>
  ```
  > **操作员填入方式**：在 App 登录对话框中，Username 填用户名，Password 字段粘贴 `access_token`。

- **频控与 ToS 注意事项**：
  - MAL API v2 严禁高频轮询，建议频率不超过 1~2 req/s；突发请求会触发 HTTP 429 或 Cloudflare 临时封禁。
  - 测试账号需为已验证邮箱的常规账户；Token 有效期通常为 31 天。

---

### 2. AniList

- **认证类型**：`TrackerAuthType.TOKEN`（`AniListTracker.kt:42`）
- **客户端机制说明**：App 内置官方 OAuth Client（`client_id=3865`，`AniListTracker.kt:23-24`），采用 Implicit Grant Flow，**用户完全无需注册 Client**。

#### 操作步骤：
- [ ] **2.1 登录 AniList**：若无账号先在 [https://anilist.co/signup](https://anilist.co/signup) 注册并登录。
- [ ] **2.2 打开授权页面**：
  在 App 内点击 AniList 登录框中的授权链接，或直接在浏览器打开：
  ```text
  https://anilist.co/api/v2/oauth/authorize?client_id=3865&response_type=token
  ```
- [ ] **2.3 授权并获取 Token**：
  在页面中点击 **"Authorize"**。页面将重定向跳转，浏览器地址栏中会出现形如：
  `https://anilist.co/api/v2/oauth/pin#access_token=eyJ0eXAiOiJKV1QiLC...&token_type=Bearer&expires_in=31536000`
  复制 `#access_token=` 与 `&token_type` 之间的长字符串。
- [ ] **2.4 发送给操作员**：
  ```text
  [AniList]
  Username: <你的 AniList 用户名>
  Access Token: <复制的 access_token 字符串>
  ```
  > **操作员填入方式**：在 App 登录对话框中，Token 字段粘贴 `access_token`，Username 填账户名（可选）。

- **频控与 ToS 注意事项**：
  - AniList 限制严格：每个 IP/Token **每分钟最多 90 次请求**（响应头含 `X-RateLimit-Remaining`）。
  - 超限后返回 HTTP 429 并携带 `Retry-After` 头，自动化脚本切忌密集触发搜索/更新。

---

### 3. Kitsu

- **认证类型**：`TrackerAuthType.CREDENTIALS`（`KitsuTracker.kt:32`）
- **客户端机制说明**：App 硬编码了客户端凭据（`CLIENT_ID` / `CLIENT_SECRET`，`KitsuTracker.kt:167-170`），使用 OAuth2 Password Grant 直接换取 Token；密码不落盘，仅 Token 被持久化保存。

#### 操作步骤：
- [ ] **3.1 准备账号**：在 [https://kitsu.app](https://kitsu.app) 或 [https://kitsu.io](https://kitsu.io) 注册普通用户并完成邮箱确认。
- [ ] **3.2 发送给操作员**：
  ```text
  [Kitsu]
  Username: <你的 Kitsu 用户名或注册邮箱>
  Password: <你的 Kitsu 密码>
  ```
  > **操作员填入方式**：直接在 App 登录对话框的 Username 与 Password 输入框填入。

- **频控与 ToS 注意事项**：
  - Kitsu 对密码错误有暴力破解保护，连续失败会导致 IP 短暂限制。
  - 请使用专用联调测试密码，避免提供日常主力敏感密码。

---

### 4. Shikimori

- **认证类型**：`TrackerAuthType.TOKEN`（`ShikimoriTracker.kt:23`）
- **客户端机制说明**：App 外部授权模式，需用户在 Shikimori 上注册个人 OAuth 应用并换取 Bearer Token，并在 GraphQL API 中附带 `user_rates` 权限（`ShikimoriTracker.kt:27-34, 75`）。

#### 操作步骤：
- [ ] **4.1 登录 Shikimori**：访问 [https://shikimori.io](https://shikimori.io) 登录账户。
- [ ] **4.2 创建 OAuth 客户端**：
  打开 [https://shikimori.io/oauth/applications/new](https://shikimori.io/oauth/applications/new)（或从 [https://shikimori.io/oauth/applications](https://shikimori.io/oauth/applications) 点击右上角新建）。
  - **Name**：`Mihon Desktop Test`
  - **Redirect URI**：`urn:ietf:wg:oauth:2.0:oob`（Out-of-band 模式，最便于手动复制）
  - **Scopes**：勾选 `user_rates`（**必须**，用于管理漫画进度和收藏）
  - 保存后获取 **Client ID** (App ID) 和 **Client Secret** (App Secret)。
- [ ] **4.3 获取 Authorization Code**：
  在浏览器访问以下链接（替换 `{CLIENT_ID}`）：
  ```text
  https://shikimori.io/oauth/authorize?client_id={CLIENT_ID}&redirect_uri=urn%3Aietf%3Awg%3Aoauth%3A2.0%3Aoob&response_type=code&scope=user_rates
  ```
  点击授权后，页面会直接展示一串授权码 `{AUTH_CODE}`。
- [ ] **4.4 换取 Access Token**：
  在终端执行：
  ```bash
  curl.exe -X POST "https://shikimori.io/oauth/token" \
    -H "User-Agent: MihonW/0.1 (Windows)" \
    -d "grant_type=authorization_code" \
    -d "client_id={CLIENT_ID}" \
    -d "client_secret={CLIENT_SECRET}" \
    -d "code={AUTH_CODE}" \
    -d "redirect_uri=urn:ietf:wg:oauth:2.0:oob"
  ```
  返回 JSON 中的 `access_token` 即为有效凭证。
- [ ] **4.5 发送给操作员**：
  ```text
  [Shikimori]
  Username: <你的 Shikimori 昵称>
  Access Token: <生成的 access_token 字符串>
  ```
  > **操作员填入方式**：在 App 登录对话框中，Token 字段粘贴 `access_token`。

- **频控与 ToS 注意事项**：
  - Shikimori API 限制为 **5 req/s** 且 **90 req/min**。
  - 所有请求**必须携带合规的 User-Agent 头**（App 内部已配置 `MihonW/0.1 (Windows)`，手动 curl 时务必添加 `-H "User-Agent: ..."`，否则会被 DDoS-Guard 拦截为 403）。

---

### 5. Bangumi (番组计划)

- **认证类型**：`TrackerAuthType.TOKEN`（`BangumiTracker.kt:26`）
- **客户端机制说明**：Bangumi 提供官方 Personal Access Token 生成器，无需配置复杂 OAuth Client。

#### 操作步骤（推荐方法：Personal Access Token）：
- [ ] **5.1 登录 Bangumi**：访问 [https://bgm.tv](https://bgm.tv) 或 [https://bangumi.tv](https://bangumi.tv) 登录账户。
- [ ] **5.2 生成 Access Token**：
  访问官方 Token 生成页：[https://next.bgm.tv/demo/access-token](https://next.bgm.tv/demo/access-token)（App 内登录引导亦指向此链接，`BangumiTracker.kt:27`）。
  - 点击生成个人 Access Token。
  - 备注可填 `Mihon Desktop`，有效期选择合适时长。
  - 复制生成的 Token 字符串。
- [ ] **备选方法（开发者应用）**：若需使用 OAuth 客户端，可在 [https://bangumi.tv/dev/app](https://bangumi.tv/dev/app) 创建个人应用，所需 Scope 为读取与修改用户收藏（`collection` 读写）。
- [ ] **5.3 发送给操作员**：
  ```text
  [Bangumi]
  Username: <你的 Bangumi 用户名或数字 UID>
  Personal Access Token: <生成的 Access Token 字符串>
  ```
  > **操作员填入方式**：在 App 登录对话框中，Token 字段粘贴 Token，Username 选填。

- **频控与 ToS 注意事项**：
  - Bangumi API v0 官方规范建议频控不超过 1 req/s。
  - 必须携带声明自身联系方式或名称的 User-Agent（App 已默认处理）。
  - 注意：Bangumi 不支持 `REREADING`（重读中）状态（`BangumiTracker.kt:31`）。

---

### 6. MangaUpdates (Baka-Updates Manga)

- **认证类型**：`TrackerAuthType.CREDENTIALS`（`MangaUpdatesTracker.kt:24`）
- **客户端机制说明**：App 通过 `PUT /v1/account/login`（`MangaUpdatesTracker.kt:33-46`）使用用户账号密码获取 `context.session_token` 并持久化，后续使用 `Authorization: Bearer <session_token>`。

#### 操作步骤：
- [ ] **6.1 注册/确认账号**：在浏览器打开 [https://www.mangaupdates.com](https://www.mangaupdates.com) 注册账户并验证。
- [ ] **6.2 发送给操作员**：
  ```text
  [MangaUpdates]
  Username: <你的 MangaUpdates 用户名>
  Password: <你的 MangaUpdates 密码>
  ```
  *(若希望直接提供 Session Token，可在终端运行 `curl.exe -X PUT "https://api.mangaupdates.com/v1/account/login" -H "Content-Type: application/json" -d "{\"username\":\"<USER>\",\"password\":\"<PASS>\"}"` 并发送返回的 `session_token`)*
  > **操作员填入方式**：在 App 登录对话框填入 Username 和 Password 即可完成换 Token 登录。

- **频控与 ToS 注意事项**：
  - **严重警告**：`www.mangaupdates.com` 网页端部署了极其激进的 Envoy/Nginx 限流（探测时多次触发 HTTP 429 Too Many Requests）。请在真实浏览器中操作注册，切勿使用脚本频繁请求网站端。
  - API 端（`api.mangaupdates.com`）限流相对宽松，但依然严禁并行并发爆破。
  - 注意：MangaUpdates 不支持 `REREADING`（重读中）状态（`MangaUpdatesTracker.kt:26`）。

---

### 7. Hikka

- **认证类型**：`TrackerAuthType.TOKEN`（`HikkaTracker.kt:16`，继承自 `JsonTokenTracker`）
- **客户端机制说明**：Hikka 为乌克兰语圈动漫与漫画追踪平台（`api.hikka.io`）。认证采用自定义 HTTP 请求头 `auth: <token>`（无 `Bearer ` 前缀，见 `HikkaTracker.kt:16`）。

#### 操作步骤：
- [ ] **7.1 注册/登录**：访问 [https://hikka.io](https://hikka.io) 并注册/登录。
- [ ] **7.2 获取 API Token**：
  访问个人设置页：[https://hikka.io/settings](https://hikka.io/settings)（未登录会 307 跳转至 `/login`）。
  - 在设置中找到 **API Token**（或 "API токен" / "Tokens"）区域。
  - 生成并复制 Token 字符串。
- [ ] **7.3 发送给操作员**：
  ```text
  [Hikka]
  Username: <你的 Hikka 用户名>
  API Token: <复制的 token 字符串>
  ```
  > **操作员填入方式**：在 App 登录对话框中，Token 字段粘贴 API Token，Username 选填。

- **频控与 ToS 注意事项**：
  - 遵循常规 Web 客户端使用限制（建议每分钟不超过 60 次调用）。
  - 搜索接口返回 Ukrainian/English 标题，注意联调测试时搜索词应使用常见漫画名或罗马音。

---

### 8. MangaBaka

- **认证类型**：`TrackerAuthType.TOKEN`（`MangaBakaTracker.kt:16`，继承自 `JsonTokenTracker`）
- **客户端机制说明**：MangaBaka（`mangabaka.org`）采用 Personal Access Token（PAT，前缀为 `mb-`，根据其 OpenAPI 规范）。App 代码中使用标准 `Authorization: Bearer <token>` 发起请求（`MangaBakaTracker.kt:16`）。

#### 操作步骤：
- [ ] **8.1 登录账号**：访问 [https://mangabaka.org](https://mangabaka.org) 或 [https://mangabaka.org/auth?action=login](https://mangabaka.org/auth?action=login)（支持 Email、Discord、MAL、AniList 第三方快速登录）。
- [ ] **8.2 生成 Personal Access Token**：
  - 登录后点击右上角个人头像 -> 进入 **Profile / Account Settings**。
  - 找到 **Developer / API / Personal Access Tokens** 选项。
  - 生成新的 Token（以 `mb-` 开头的密钥字符串，如 `mb-live-...`）。
  *(注：开发者文档详见 [https://mangabaka.org/data/api](https://mangabaka.org/data/api))*。
- [ ] **8.3 发送给操作员**：
  ```text
  [MangaBaka]
  Username: <你的 MangaBaka 用户名/昵称>
  Personal Access Token: <以 mb- 开头的 Token 字符串>
  ```
  > **操作员填入方式**：在 App 登录对话框中，Token 字段粘贴 PAT。

- **频控与 ToS 注意事项**：
  - Cloudflare 托管，遵循其标准 Rate Limiting（返回 429）。
  - 切勿滥用批量同步或未授权爬取公共目录。

---

## 3 个自托管 Tracker 凭证获取操作手册（Checklist）

自托管服务需要联调人员提供**可从公网或测试网络直接访问的实例 URL**（若在同一内网则提供局域网 IP 与端口）。

### 9. Komga

- **认证类型**：`TrackerAuthType.SERVER`（`KomgaTracker.kt:23`）
- **认证方式**：二选一（`KomgaTracker.kt:115-132`）：
  - 方式 A（推荐）：无用户名 + **API Key**（App 发送 `X-API-Key: <key>` 头）。
  - 方式 B：**用户名 + 密码**（App 发送 HTTP Basic Auth 头）。
- **凭证获取**：
  - API Key 获取：进入 Komga WebUI -> 右上角个人设置（Account Settings） -> **API Keys** -> 生成新 Key。
- [ ] **发送给操作员**：
  ```text
  [Komga]
  Server URL: http://<ip-or-domain>:<port> (例: http://192.168.1.50:25600)
  API Key: <生成的 X-API-Key>  (推荐，此时 Username 留空)
  -- 或 --
  Username: <Komga 账户邮箱/用户名>
  Password: <Komga 密码>
  ```
- **特性支持**：不支持评分（Score），支持全部 6 种阅读状态（`KomgaTracker.kt:28-29`）。

---

### 10. Kavita

- **认证类型**：`TrackerAuthType.SERVER`（`KavitaTracker.kt:20`）
- **认证方式**：**Server URL + API Key**（`KavitaTracker.kt:28-42`）。
  - App 接收 URL 与 API Key 后调用 `POST /api/Plugin/authenticate?pluginName=Tachiyomi-Kavita` 交换 JWT Token，后续自动续期；持久化保存的是 API Key 本身。
- **凭证获取**：
  - 进入 Kavita WebUI -> 点击设置 -> 用户设置（User Settings） / 客户端插件（Clients） -> 复制 **API Key**。
- [ ] **发送给操作员**：
  ```text
  [Kavita]
  Server URL: http://<ip-or-domain>:<port> (例: http://192.168.1.50:5000)
  API Key: <Kavita API Key 字符串>
  ```
  *(注：Dialog 中的 Username 可留空，Password/Token 栏填入 API Key)*
- **特性支持**：不支持评分（`KavitaTracker.kt:26`），仅支持 3 种状态：`PLAN_TO_READ`、`READING`、`COMPLETED`（`KavitaTracker.kt:25`）。

---

### 11. Suwayomi (Suwayomi-Server / Tachidesk)

- **认证类型**：`TrackerAuthType.SERVER`（`SuwayomiTracker.kt:24`）
- **认证方式**：**Server URL + 可选认证凭据**（`SuwayomiTracker.kt:123-127`）：
  - 方式 A：无密码保护实例（本地/内网默认），仅需 Server URL，凭据留空。
  - 方式 B：配置了 Basic Auth，提供用户名 + 密码（App 发送 `Authorization: Basic ...`）。
  - 方式 C：配置了 Token Auth，提供 Bearer Token（App 发送 `Authorization: Bearer <token>`）。
- **凭证获取**：
  - 查看 Suwayomi 配置文件 `server.conf`（`server.basicAuthEnabled`、`server.basicAuthUsername`、`server.basicAuthPassword`）或 WebUI 设置。
- [ ] **发送给操作员**：
  ```text
  [Suwayomi]
  Server URL: http://<ip-or-domain>:<port> (例: http://127.0.0.1:4567)
  Username: <用户名，若无认证则留空>
  Password/Token: <密码或 Token，若无认证则留空>
  ```
- **特性支持**：不支持评分（`SuwayomiTracker.kt:27`），仅支持 3 种状态（根据 `unreadCount` 判断完成状态，`SuwayomiTracker.kt:74-78`）。

---

## 联调操作员快速粘贴模板（Operator Paste Sheet）

测试用户完成后，请将以下文本块完整复制并发送给联调操作员：

```text
=================== MIHON TRACKER CREDENTIAL SUBMISSION ===================
1. MyAnimeList:
   Username: 
   Access Token: 

2. AniList:
   Username: 
   Access Token: 

3. Kitsu:
   Username: 
   Password: 

4. Shikimori:
   Username: 
   Access Token: 

5. Bangumi:
   Username: 
   Personal Access Token: 

6. MangaUpdates:
   Username: 
   Password: 

7. Hikka:
   Username: 
   API Token: 

8. MangaBaka:
   Username: 
   Personal Access Token (mb-...): 

--------------------------------------------------------------------------
[Self-Hosted Instances - Optional / If Available]
9. Komga:
   Server URL: 
   API Key (or User/Pass): 

10. Kavita:
   Server URL: 
   API Key: 

11. Suwayomi:
   Server URL: 
   Username / Token: 
==========================================================================
```
