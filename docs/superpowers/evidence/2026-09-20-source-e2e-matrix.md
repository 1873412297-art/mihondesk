# 1A 图源端到端矩阵首轮实测（2026-09-20）

日期：2026-09-20 · runner：`scripts/verify-source-e2e-matrix.py`（broker_http 修复后）· 扩展：`.worktrees/suwayomi/.superpowers/sdd/t3-samples/converted/`（7 个官方同版本 .mext）· 宿主：0.2.18 app image（`329B0023…F88`）· 网络：本机（中国大陆出口）

原始数据：`build/source-e2e-matrix-20260920.json` / `.md` / `-evidence/`（每源 host stderr）；**最终快照（Filter 修复后同一镜像复跑）：`build/source-e2e-matrix-final-20260920.json/.md`，五源结论与上表一致，归因全部自动正确**（MangaPlus 经 per-source hint 判 `site_block`）。

## 结果矩阵

| 源 | 类型 | 安装 | 浏览 | 搜索 | 详情 | 章节 | 页面 | 图片 | 结论 | 归因 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| MangaDex | 普通 HTTP | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **通过** | — |
| NHentai | 普通 HTTP | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | **通过** | — |
| BiliManga | 偏好驱动/zh | ✅ | ✅ | ⛔ 需登录 | ✅ | ✅ | ✅ | ✅ | **受限通过** | `manual_interaction` |
| MangaFire | WebView/验证码 | ✅ | ❌ 403 | ❌ 403 | ⛔ | ⛔ | ⛔ | ⛔ | 不支持 | `site_block` |
| MangaPlus | 图像解密 | ✅ | ❌ | ❌ | ⛔ | ⛔ | ⛔ | ⛔ | 不支持 | `site_block`（地区封锁） |

## 归因细节

- **BiliManga**：搜索报「獲取搜索憑證失敗，請稍後重試」——搜索需登录态 Cookie；浏览/详情/章节/页面/图片全通过。人工扫码登录一次即可转为通过（需用户人工）。
- **MangaFire**：browse/search 均 HTTP 403（Cloudflare 挑战）。扩展 shim 的 WebView 验证码分支本就不在自动化覆盖内（E4 已记录），按 SOP 需人工交互一次后记录会话有效期。
- **MangaPlus**：初始报 `InstantiationError: Filter$Select`（真 shim 缺陷，**当日已修复并证明**，见下文"后续"节）；修复后 browse/search 失败定性为**地区封锁**——本网络对所有 MangaPlus API 端点均收到 4 字节 protobuf 错误信封（HTTP 200 包装），扩展解析后抛错属正确行为。

## 与历史证据的关系

- E3（转换+加载）结论不变且被超越：本轮证明了其中 2 源全链真实阅读、1 源仅登录受限、1 源被站点封锁、1 源暴露 shim 缺陷。
- 「加载成功 ≠ 全站通过」的警告在 MangaPlus 上应验（加载过、浏览挂）。

## 后续（2026-09-20 当日修复进展）

**MangaPlus `Filter$Select` InstantiationError 已修复并证明**。根因（javap 反汇编实证）：MangaPlus v1.6.66 的字节码在 `c0.h(JsonElement)`（运行时 `filters.json.zst` 远程配置过滤器构建器）中通过 Kotlin 合成默认参数构造器 `(String, Object[], int, int, DefaultConstructorMarker)` **直接 `new Filter$Select`**；shim 中 `Select` 为 abstract → 链接期 `InstantiationError`。修复：shim `Filter.Select` 改 concrete `open class`（扩展抽象子类继续合法），重建后 `get_filter_list` 探测返回真实过滤器列表（Status Select + Separator + Header）——崩溃代码路径恢复。

**剩余**：~~MangaPlus browse/search 仍失败~~ **已定性（2026-09-20 深挖）**：broker 日志（runner 新增 `MIHON_E2E_BROKER_LOG` 调试通道）显示扩展发出 1 个请求即获 HTTP 200，但响应体为 **4 字节 protobuf 错误信封 `12 02 08 03`（ErrorResult）**；本机直连全部 MangaPlus API 端点（rankingV2/allV2/title_detailV3）均返回同一 4 字节信封——**地区封锁**（MangaPlus 未对中国大陆授权），非 shim 缺陷。扩展解析错误信封后抛 "unknown error happened" 属正确行为。归因更正为 `site_block`（样本 hints 已加规则）。Filter.Select 修复仍然成立（它阻断的是网络步骤之前的过滤器构建）。结论：在授权地区网络下 MangaPlus 有望通过（修复后），中国大陆网络为受限环境。

## 限制

- 单次测量，未做 7 天复测（M1 前补）。
- 网络环境为中国大陆出口：MangaFire/MangaPlus 的部分表现可能受网络位置影响；归因已按"站点封锁/shim 缺陷"分开，MangaPlus 的 Filter 错误与网络无关。
- 会话型源（BiliManga 登录后搜索、MangaFire 人工过验证码）待用户人工辅助后升级为通过。
