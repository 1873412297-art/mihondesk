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
- 已完成：**MSI 行 4/4**（干净安装 ✅、N-1→N 升级 ✅、卸载保留数据 ✅、卸载删除数据 ✅）。
- 进行中：ZIP 行 3 格、EXE 行 3 格、回滚 2 格。

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

## 下一步顺序
ZIP 行（干净安装/升级/删除——全程免提权）→ EXE 行（先解 UAC 可见性）→ 回滚 2 格。
