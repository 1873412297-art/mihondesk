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

## 回归、安装与实际运行

生产代码提交：`63de66fc05437fbb0423268cbe6f193520205cbe`。
源码完整 `desktop-app:test` 实际执行，694 项中 679 项通过、15 项按条件跳过，0 失败；
其中真实窗口探针默认不启用。Spotless 和 `git diff --check` 通过。
本次完整回归设置了 ANGLE 偏好，但 Compose UI 断言大多使用离屏渲染；
这不代替下面的真实窗口和安装程序验证。

`packageMsi`、`verifyCleanDistribution` 和 `scripts/verify-msi-package.ps1` 通过。
分发镜像不包含用户资料和已安装扩展。实际 MSI 安装退出码为 0。

| 项目 | 结果 |
| --- | --- |
| MSI | `desktop-app/build/compose/binaries/main/msi/mihondesk-0.2.18.msi` |
| MSI 字节数 | 485639344 |
| MSI SHA-256 | `C46B32DA12F4E187769457717DB44FCC9F0F58501C77D75FEB84A3458E808E2C` |
| 安装位置 | `%LOCALAPPDATA%/mihondesk/mihondesk.exe` |
| 已安装应用 JAR | `desktop-app-de8d3d1e875e1290c0423f51752fb0b6.jar` |
| JAR SHA-256 | `41BFBE8866CD2B1892DDE94DCFAA0FB5B0C92C03AA4FF5015354A241F39A7B10` |

安装 JAR 与构建镜像哈希相同，嵌入版本 0.2.18、上述生产提交、`dirty=false`。
三个已安装渲染 DLL 均与镜像及内置浏览器中的原文件 SHA-256 一致。
本轮交付 MSI 和本机安装，没有生成新 EXE 安装器、便携 ZIP 或发布 GitHub Release。

实际启动安装 EXE，主进程 PID 89580 响应正常，原生 2048×1239 窗口确认设置页显示
0.2.18，黑底中英文文字与控件正常。进程模块列表直接读到安装目录中的
`skiko-windows-x64.dll`、`libEGL.dll` 和 `libGLESv2.dll`；
`jcmd VM.system_properties` 确认 `skiko.rendering.angle.enabled=true`。

以安装目录 JAR 优先的 classpath 执行全部 UI、渲染配置、主线程及通知测试，
299 项全部通过、0 跳过。界面/阅读对比度用例和帧探针核对生产类来自安装目录。
下载界面浅色/纯黑、640/1024 像素宽及阅读配色截图来自合成测试资料。
真实窗口探针没有强制 `skiko.renderApi`，调用安装 JAR 的默认配置后断言实际 ANGLE：
360 个采样时间戳、平均回调频率 179.93 Hz、P95 6.22 ms、最大 10.29 ms，
大于 20 ms 的间隔为 0，窗口聚焦为 true。该次测量与前述交替实验负载不完全相同，
不能把两个实验的差值直接当作优化提升幅度，也不保证真实页面恒定 180 FPS。

安装 EXE 的独立资料目录阅读验证退出 0，`SUCCEEDED/initial-open`：
单图、目录、CBZ、CBT、CB7、CBR、EPUB，六种阅读模式，共解码 38 个图块。
该验证使用合成资源，不访问真实阅读历史。

升级前用 SQLite backup API 保存一致备份。原生窗口退出受持续输入保护阻止后，
仅终止了路径严格匹配旧安装 EXE 的两个进程；关闭后数据库及下载内容校验一致。
安装前后配置、队列和数据库文件哈希一致。启动新应用后校验数据库完整性、外键及
9 张业务表：旧记录没有被删除或改写，195 个原下载文件（193 页及元数据）哈希一致。
应用运行期间新增一条未收藏在线漫画、一条章节及阅读历史，故全库哈希比较不再相等；
逐行差异验证确认原有记录仍完整，不把当前活动数据回滚成旧备份。

本地证据包括 `source-regression/summary.json`、`installed-regression/summary.json`、
`installed-verification.json`、`installed-native.json`、`installed-processes.json`、
`installed-rendering-properties.txt`、`installed-frame.json`、`installed-reader.json`、
`profile-delta.json`。私有资料、备份和界面原始记录保留在忽略目录，未提交。
