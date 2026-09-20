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

## 状态
- 已完成：格 2（MSI 干净安装）。进行中：其余 11+2 格。
- 下一步顺序建议：MSI 行续（升级/卸载保留/卸载删除）→ ZIP 行（无需提权）→ EXE 行（先解 UAC）。
