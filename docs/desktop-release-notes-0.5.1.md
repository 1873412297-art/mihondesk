## mihondesk 0.5.1

修复 0.5.0 中 Google Drive 同步无法使用的问题。

- **Google Drive 同步**：0.5.0 发行包缺少 Google Drive 客户端密钥，「设置 → 备份」中的同步卡片显示“此版本未配置 Google Drive”，无法登录。0.5.1 恢复正常，与手机伴侣应用同步书架、分类和阅读进度。
- **文案修正**：首页和空书架上的「与手机同步书架」卡片改为说明通过 Google Drive 与手机伴侣应用同步，不再提已移除的扫码配对。

0.5.0 的新版本提醒、自动下载与一键安装、页面过渡动画等更新见 [0.5.0 发布说明](https://github.com/1873412297-art/mihondesk/releases/tag/v0.5.0)。

### 安装

- `mihondesk-0.5.1.exe`：推荐，下载后按向导安装
- `mihondesk-0.5.1.msi`：需要 MSI 安装方式时使用
- `mihondesk-0.5.1-windows-x64-portable.zip`：便携版，完整解压到可写目录后运行其中的 `mihondesk.exe`
- `mihondesk-0.5.1-macos-arm64.dmg`：macOS（Apple Silicon）版。未签名、未公证；首次打开请在 Finder 中右键选择“打开”
- 下载后请使用 `SHA256SUMS.txt` 校验文件完整性

安装版数据仍保存在 `%APPDATA%\MihonW`，升级不会丢失书架、图源、设置与登录数据。已安装 0.5.0 的用户会在应用内收到更新提醒。
