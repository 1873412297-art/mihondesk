# 1E 性能 SLO 测量结果（2026-09-20）

日期：2026-09-20 · 机器：Windows 11 Insider Build 26220 · i9-13980HX · 32 GB · 安静负载（无模拟器/构建并发）· 可执行文件：`mihondesk.exe` SHA256 `329B0023…F88`（0.2.18）

## 结果总览

| SLO | 目标 | 方法 | 结果 | 判定 |
| --- | --- | --- | --- | --- |
| 冷启动 | ≤5 s | N=30 + 首跑 1 次；就绪信号 = 隔离数据目录写出 `preferences.properties`（50 ms 轮询）；中位/P95 | 首跑 1250.7 ms；常态中位 1166.0 ms，**P95 1239.2 ms**（min 1135.5 / max 1406.0，30/30 就绪） | **通过** |
| 空闲内存 | <500 MB | 启动后静置，进程树私有字节采样 7 min（10 s 间隔），取 4–7 min 稳态中位 | 稳态私有字节中位 **463.1 MiB**（P95 463.3，工作集中位 341.4） | **通过**（余量 ~37 MB，偏紧） |
| 30 分钟长读内存 | 有界、末两窗对首窗 ≤5% | 1800 s soak × 2（常规 + 安静复跑） | 两次均通过（详见 [soak 证据](2026-09-20-performance-soak-1800s.md)） | **通过（交叉确认）** |
| 帧时间 P95 | ≤33 ms | 真实 ComposeWindow 阅读负载（512 页顺序翻页 + 48 次跨模式跳页，`LongReaderWindowTest`），显示时钟回调间隔 | **P95 10.22 ms**（6,968 次回调，Direct3D @180Hz，最大单次 527 ms 尖峰） | **通过**（方法见下） |

原始数据：`build/perf-startup-20260920/`（identity + 30 样本 + summary）、`build/perf-idle-20260920/`（40 样本 jsonl）、`build/frame-metrics-20260920/`（window-report.json + callback-nanos.txt）。工具：新增 `scripts/measure-startup-performance.ps1`。

## 帧时间方法说明（重要）

指标为 **Compose 显示时钟回调间隔**（`withFrameNanos`），在真实 `ComposeWindow` 中驱动真实 `ReaderScreen`（Direct3D 渲染、180 Hz 显示器）测得；它是帧产出的上界代理，**不等于 GPU 呈现 FPS**（无 OS 级呈现遥测，本机无 PresentMon）。判定理由：P95 10.22 ms 距 33 ms 目标余量 3 倍，即使计入呈现开销结论稳健。527 ms 最大尖峰为单次事件（模式切换/首次组合），不影响 P95 判定。后续如需严格呈现帧数据，可装 PresentMon 复测（低优先）。

## 备注与偏差声明

- 冷启动"就绪"信号为偏好文件落盘，可能略早于首帧绘制；但结果距 5 s 上限余量 4 倍，结论稳健。
- 空闲内存余量仅 ~7%：后续功能（新主题、后台服务常驻）应监控该指标回归。
- 启动工具链怪癖（已绕开并记录）：空闲测量曾出现经 `pwsh -Command` 内 `Start-Process` 启动时启动器随即退出的现象（当时存在残留实例持有 profile 锁，疑为主要原因）；`measure-startup-performance.ps1` 的 `Start-Process -WindowStyle Hidden` 路径经 30/30 次实测正常。soak/空闲测量采用 bash 直接启动 + 按启动时间发现 launcher PID 的稳妥路径。
- soak 必须在安静机器执行（并发负载曾导致 fixture 故意损坏资产触发 `CorruptImage` 拒绝，见 soak 证据）。
