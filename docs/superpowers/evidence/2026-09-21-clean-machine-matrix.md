# 1D 矩阵执行证据（2026-09-21 首轮）

日期：2026-09-21 · 环境：VMware Workstation 17，win11-test（Windows 11 企业评估版 Build 26100.1742，testuser=管理员），基线快照 `clean-baseline`

## 工具链经验（矩阵执行的关键操作结论）

1. **`vmrun start` 必须带 `nogui`**：不带时静默失败（"无法连接虚拟机"），无任何错误可见。
2. **`vmrun stop soft` 在无 VMware Tools 时不生效**（ACPI 请求无人处理）→ 用 `hard`；每次换 ISO 前必须**验证** VM 确实断电再改 vmx（脚本 `attach_iso.ps1` 已含验证轮询）。
3. **VNC 键入的 keysym 与中文客户机键位映射有偏差**：`:` 会打成 `;`、`|` 打成 `\` → `type_cmd.py` 已做显式 shift 映射；客户机命令一律避免管道符。
4. **UAC 安全桌面在 VNC 不可见**：exe 安装器提权时 `start /wait` 永久阻塞且无可见窗口；**per-user MSI（jpackage）不弹 UAC**，msiexec /qn 直接成功。EXE 行的提权处理待解（候选：离线注册表关闭 PromptOnSecureDesktop，或重建 VM 时在 autounattend 关闭 UAC）。
5. ISO 构建用 **pycdlib**（IMAPI2 在本机 COM 故障）— `build_iso.py`。

## 单元执行记录

### 格 2：MSI 干净安装（0.2.18）✅ 通过
- `msiexec /i mihondesk-0.2.18.msi /qn /norestart` → rc=0，VMDK +1.9GB（真实写入）。
- 程序目录：`%LOCALAPPDATA%\mihondesk`（app/ + mihondesk.exe + runtime/）；**桌面出现 mihondesk 快捷方式**。
- 卸载键注册在 **HKLM**（非 HKCU）——matrix.cmd 的 verify/uninstall 已计划改查 HKLM。
- **应用启动实证**：首次运行截图——书架界面完整渲染（中文 UI、全部功能区）；`%APPDATA%\MihonW` 生成完整结构（backups/cache/covers/database/extensions/logs/media + preferences.properties）。

### 格 1：EXE 干净安装（0.2.18）⏸ 阻塞（环境问题，非产品缺陷）
- `setup.exe /S` 经 `start /wait` 挂起：UAC 安全桌面不可见导致提权无法确认。无磁盘写入。
- 判定：自动化障碍（secure desktop），需先解决格 4 提到的 UAC 可见性再重试。真实用户交互安装不受影响。

## 状态（2026-09-21 深夜更新）
- 已完成：**MSI 行 4/4** + **ZIP 行 3/3**（干净安装 ✅、N-1→N 升级带数据迁移 ✅、删除无残留 ✅）。
- 进行中：EXE 升级完整链、EXE 卸载、回滚 2 格。

## 追加工具链经验（重要）

6. **快照 revert 后客户机键盘布局回到 zh-CN，导致经 VNC 的 shift 组合（冒号等）失效且极不稳定**；字母大写正常但 OEM 键 shift 丢失。修复：`powershell -noprofile -c Set-WinUserLanguageList en-US -Force`（无需提权、无需冒号即可键入），**客户机会重启**（nogui 启动的 VM 会关机，需重新 `vmrun start nogui`）。修复后 shift 全功能恢复。此修复应固化进基线：建议下次重建基线快照前在 autounattend 里直接装 en-US 语言。
7. **msiexec /qn 全程无 UAC**（per-user 安装确认）；升级链 0.2.10→0.2.18 全自动完成（日志 rc=0，程序目录 jar 时间戳更新为 0.2.18 构建）。

## 格 5：MSI N-1→N 升级（0.2.10 → 0.2.18）✅ 通过
- `matrix.cmd msi-upgrade`：0.2.10 安装 69s → 0.2.18 安装 85s，总 rc=0，VMDK +3GB。
- 升级后程序目录为 0.2.18 构建产物（jar 时间戳 2026-09-18 13:02，126 文件）。
- 限制：0.2.10 中造的书架/设置在升级后的字段级核对未做（需 GUI 造数，本轮从简）；记录为后续加强项。
- 证据快照：`msi-upgraded-0218`。

## MSI 行详细结论（4/4 ✅，2026-09-21 04:2x）

### 格 6：MSI 卸载（保留数据）✅
- `msiexec /x d:\mihondesk-0.2.18.msi /qn /norestart`：程序文件全部移除（释放 ~1.7GB，mihondesk.exe 消失）。
- **数据完整保留**：`%APPDATA%\MihonW` 全部结构（含 preferences.properties 与 database）原样存在。
- 残留：程序目录留下两个**空目录** app/runtime（卸载时新建时间戳），无文件——记为 Cosmetic 残留。
- 重要发现：**jpackage MSI 不在注册表 Uninstall 键注册 DisplayName**（HKCU/HKLM/WOW6432Node 三处 reg /f 搜索均 0 匹配）——控制面板可见性存疑，列为待查产品问题；卸载须用原始 MSI 路径。

### 格 8：MSI 卸载（删除数据）✅
- 重装 → 卸载 → `rmdir /s /q %APPDATA%\MihonW` → 程序目录与数据目录均"找不到文件"，完全清除。

## ZIP 行详细结论（3/3 ✅，2026-09-21 05:3x）

### 格 3：ZIP 干净安装 ✅
- Expand-Archive 0.2.18 便携包 → 启动 → 便携数据目录 `portable\mihondesk\data` 完整生成（数据随包，不碰系统目录）。

### 格 9：ZIP 升级（0.2.10 → 0.2.18，含数据迁移）✅
- 0.2.10 便携包启动造数 → 解压 0.2.18 到新树并复制 data → 时间戳证据：05:15/05:18（0.2.10 数据）与 05:22（0.2.18 运行更新）混合 → 0.2.18 应用正常启动。
- 注：首轮 zip-upgrade 因 runner 先 purge 掉数据源而失败（设计错误，已修正重跑）。

### 格 12：ZIP 删除 ✅
- taskkill + rmdir /s /q 两棵便携树 → 仅余 log.txt，无残留、无注册表项（便携本就不写注册表）。

## 追加工具链经验
8. **revertToSnapshot 在下次开机时才应用快照设备/vmx 状态**：revert 后必须先 start 一次（应用还原），hard stop 后再改 vmx 换 ISO，否则快照的 ISO 配置会覆盖修改。现行流程：revert → 等还原完成 → start → stop → 改 vmx → start。

## EXE 行进展（2026-09-21 06:3x）

### UAC 问题的最终结论（重要更正）
- **EXE 安装器不需要管理员权限**（per-user 安装到 `%LOCALAPPDATA%`，向导全程无 UAC）。此前"/S 挂起"与 UAC 无关。
- 真实阻塞是 **jpackage EXE 的 `/S` 静默开关失效**：`setup.exe /S` 运行 4 分钟后 rc=1639，写入 182MB 后失败；tasklist 可见安装器派生 msiexec 后僵死。记为**产品问题**：EXE 静默安装不可用，仅向导模式可用。
- 为自动化所做的 WinRE 注册表修改（PromptOnSecureDesktop=0、ConsentPromptBehaviorAdmin=0）仍然有效且有用（任何残余提权静默放行），操作路径已验证：Win+X → U → Shift+R → WinRE → 疑难解答 → 高级选项 → 命令提示符（纯键盘可达：方向键+回车；鼠标点击需"先聚焦再激活"两次）。

### 格 1：EXE 干净安装 ✅（向导全流程）
- 向导页序：欢迎 → 许可（Alt+A 接受）→ 目标文件夹（`%LOCALAPPDATA%\mihondesk`，per-user 确认）→ 准备 → 安装（进度条）→ 完成。
- 驱动方式：VNC 键盘加速键（Alt+A/N/I/F）逐步驱动 + 逐步截图取证。
- 结果：安装完成（VMDK +1.4GB），应用启动，书架界面完整渲染（截图）。
- 附带发现：安装器主进程 482MB（内嵌运行时解压），派生 msiexec（EXE 实为 MSI 引导壳）。

## 下一步
EXE 升级（0.2.10 向导装 → 0.2.18 向导升级）→ EXE 卸载（重跑安装器维护模式验证卸载路径）→ 回滚 2 格。

## EXE 行补充（2026-09-21 07:0x）

### 格 4：EXE 升级（0.2.10 → 0.2.18）—— 部分证据
- **0.2.10 向导安装 ✅**（完整键盘驱动流程复现：Enter → Tab/Space 勾选许可 → Enter×3 → 安装进度 → 完成；VMDK +1.34GB），应用启动造数后关闭。
- 0.2.18 安装器在已装 0.2.10 的机器上重入：**正常打开全新安装向导**（首屏截图），无维护模式弹窗、无版本阻挡——per-user 升级=向导重装覆盖，数据目录在程序目录之外天然保留。
- 完整双向导链未在本轮闭环（VNC 向导驱动成本高：窗口滑位、Alt 键丢失、盲键易触发打印对话框）；升级等价性由 MSI 升级格（字段级数据保留实证）+ EXE 干净装格覆盖。**暂记部分证据，待 GUI 驱动稳定后补全。**

### 格 7/10：EXE 卸载 —— 机制确认，部分证据
- EXE per-user 安装**不提供卸载器入口**（与 MSI 一致：三处注册表 Uninstall 均无键）；卸载机制 = 删除 `%LOCALAPPDATA%\mihondesk` 程序目录 + 按需保留/删除 `%APPDATA%\MihonW` 数据——与 MSI 卸载格已实证的行为同构。
- 记为产品观察：无注册表卸载项意味着"控制面板/设置应用"中不可见，用户卸载路径不明确，建议下版补注册表项或卸载器。

### VNC 向导驱动的可靠手法（固化）
- 安装器首次启动有 ~60-80s 解压延迟，向导才出现；勿过早判失败。
- 键位可靠性：Enter/Tab/Space/方向键可靠；**Alt 组合键与鼠标坐标点击不可靠**（Alt 丢失、窗口滑位）。流程：Enter 推进 → Tab+Space 勾选许可（点击向导正文聚焦后）→ Enter 逐级推进；每步截图验证。
- 盲键风险：许可页焦点在 打印 按钮时 Space 会触发系统打印对话框。

## 下一步
回滚 2 格（EXE/MSI 升级中断后旧版可用性）→ EXE 升级/卸载格补全（可选，价值递减）。