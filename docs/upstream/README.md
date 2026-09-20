# upstream/ — 上游同步资料

本目录记录 mihon-w（Mihon Windows 桌面移植 fork）与上游 [mihonapp/mihon](https://github.com/mihonapp/mihon) 的同步相关资料：

- [SYNC.md](SYNC.md) — 上游同步规程：remote 配置、同步节奏（落后 tag ≤2 周触发）、merge 合入方式、每次同步后的强制检查清单、冲突处理原则（Android 侧跟随上游 / 桌面侧 shim 适配）。
- [MIHON_CHANGELOG.md](MIHON_CHANGELOG.md) — 上游 changelog 副本，每次同步后更新，记录合并基点。
- [suwayomi-reference.md](suwayomi-reference.md) — Suwayomi 参考基线与扩展兼容性差异对照。

机制设计见 `docs/superpowers/plans/2026-09-20-production-readiness-continuation.md` §4 Phase 3。
