# Windows 动画渲染修复（0.2.18）

分支：`codex/fix-animation-smoothness`，基于 `45e7b2a12`。
用户反馈所有动画不顺滑，因此本轮检查共用的显示时钟和渲染入口。

## 定位与调整

本机 Java 报告测试显示器为 180 Hz。旧版使用 Skiko 0.150.1 的 DIRECT3D 后端。
真实 ComposeWindow 的连续动画测量使用 `withFrameNanos`，每轮丢弃前 120 帧，
记录随后 360 个时间戳、359 个帧间隔；没有使用 Compose UI 测试的虚拟时钟。
在同一进程交替运行两种后端，得到：

| 次序 | 后端 | 平均回调频率 | 帧间隔 P95 | 大于 20 ms |
| --- | --- | ---: | ---: | ---: |
| 1 | DIRECT3D | 108.34 Hz | 17.35 ms | 9 |
| 2 | ANGLE | 142.02 Hz | 11.76 ms | 0 |
| 3 | DIRECT3D | 122.13 Hz | 14.45 ms | 0 |
| 4 | ANGLE | 147.93 Hz | 11.50 ms | 0 |

这是合成动画的显示时钟回调频率，不是 GPU 实际呈现计数，也不是全应用帧率保证。
独立进程的 JFR 样本中，AWT 线程主要等待渲染，渲染线程样本集中在 Direct3D flush/swap。
结合交替测量，证据支持调整默认 Windows 渲染后端；不能据此认定所有卡顿只有一个原因。

Windows 安装版在 ANGLE 依赖齐全时优先启用该后端。三个原生 DLL 从已经锁定版本的
JBR 浏览器运行时复制到主 Skiko DLL 同级目录，保持上游文件原样并保留原许可证。
主应用 Java 17 运行时、垂直同步、动画时长和系统计时器配置不变。
用户显式指定的 `SKIKO_RENDER_API`、`skiko.renderApi` 或 ANGLE 开关优先。
依赖缺失时保持原默认；ANGLE 初始化失败仍使用 Skiko 自带的 Direct3D/OpenGL/软件回退链。
未配齐 ANGLE DLL 的真实窗口实验已经观察到回退到 DIRECT3D 并继续出帧。

## 验证入口与边界

`DesktopRenderingTest` 覆盖安装版默认、用户覆盖项、缺失依赖和非 Windows 平台。
默认选择用例先在空实现失败，再在修复后通过。
`DesktopFramePacingTest` 是环境变量显式启用的真实窗口探针，普通回归默认跳过。
它可以核对实际渲染 API、安装目录中的配置类来源，并可选择显示中英文文字。

本地原始资料位于忽略目录 `build/smoothness-evidence/`：
`paired-0.json` 至 `paired-3.json`、`paired.log`、`baseline.jfr`、
`rendering-red.xml`、`rendering-green.log`、`text-smoke.json`。
测试探针曾因临时原生目录漏复制 Skiko 的 `icudtl.dat` 打印 ICU 提示；
补入同版 Skiko 数据后复跑成功且提示消失。安装镜像本来就包含该文件，
没有用浏览器的不同版本 ICU 数据替换它。

代码、安装包和安装后结果分别记录；安装验收完成后补充以下结果。
