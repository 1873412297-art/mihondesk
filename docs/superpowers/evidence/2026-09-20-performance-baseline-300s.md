# 阅读器性能基线：300 s soak（0.2.18）

日期：2026-09-20 · 基线版本：0.2.18（`f3ec71bb4`，dirty=false）· 运行环境：Windows 11 Insider Preview Build 26220 · CPU：i9-13980HX · 内存：32 GB

## 运行身份

- 可执行文件：`desktop-app/build/compose/binaries/main/app/mihondesk/mihondesk.exe`
- SHA256：`329B002304FA801D368A14C3875B92780C37BF81D3C101EFAA5FF8A8287AEF88`
- App JAR SHA256：`DC5FB4F82BC185E46D36574B64B396957E2D89EECDDBCFBC85CCA7C757498228`
- Fixture SHA256：`9FAF51D89D29B547F6DB4C54D3D27A5A2EBAA625961255AE0BB2BFA754DF7038`

完整身份与元数据：`build/evidence-soak-baseline-20260920-300s/identity.json`  
原始采样：`build/evidence-soak-baseline-20260920-300s/reader-samples.jsonl`、`process-memory-*.jsonl`

## 命令

```powershell
pwsh -File scripts/verify-reader-soak.ps1 `
  -Executable 'desktop-app/build/compose/binaries/main/app/mihondesk/mihondesk.exe' `
  -FixtureDirectory 'build/reader-soak-0218/fixture' `
  -OutputDirectory 'build/evidence-soak-baseline-20260920-300s' `
  -DurationSeconds 300
pwsh -File scripts/summarize-reader-soak.ps1 -OutputDirectory 'build/evidence-soak-baseline-20260920-300s'
python scripts/plot-reader-soak.py 'build/evidence-soak-baseline-20260920-300s'
```

## 结果摘要

| 指标 | 值 |
| --- | --- |
| 运行时长 | 300.16 s |
| 循环数 | 72 |
| 解码 tile 数 | 2,736 |
| 验证通过 | 是（`accepted: true`，exit code 0） |
| 堆使用最小/中位数/最大 | 71.3 / 257.6 / 455.8 MiB |
| 进程工作集最小/中位数/最大 | 116.7 / 737.2 / 1037.6 MiB |
| 私有字节最小/中位数/最大 | 443.3 / 915.8 / 1422.0 MiB |
| reader core 高水位 | 163.9 MiB（限制 256 MiB） |
| 验证资产 | standalone、directory、cbz、cbt、cb7、cbr、epub（7/7） |
| 验证阅读模式 | SINGLE_LTR/RTL、DUAL_LTR/RTL、VERTICAL、WEBTOON（6/6） |
| 采样连续性 | reader 最大间隙 5.62 s，process 最大间隙 5.84 s，PID 一致 |

## 解释与限制

- 300 s 运行是**基线验证**，用于确认 soak 工具链在当前构建上可复用、可出图，并非 30 分钟平台期关门证据。
- 堆使用中位数 257.6 MiB，在 reader core 256 MiB 预算附近波动；最大 455.8 MiB 与 GC 周期相关。
- 进程工作集中位数 737 MiB，已高于原设计"空闲 <500 MiB"目标，但 soak 是**主动解码负载**，不是空闲测量；idle 测试需单独执行。
- 本次测试**不包含 Compose 帧时间、UI 响应、冷启动、空闲内存**；这些项按 §2.5 单独补齐。
- 运行机器是开发机，不是干净 Win10/Win11 验收环境。

## 下一步

1. 跑完整 1800 s soak 复现/否定"30 分钟平台期"。
2. 若 1800 s 后段窗口中位数抬升，用独立完整 JDK 21 + JFR/async-profiler 做分配归因（候选：decode buffer、tile cache、IPC buffer）。
3. 单独测量冷启动 P95、帧时间 P95、空闲内存。
