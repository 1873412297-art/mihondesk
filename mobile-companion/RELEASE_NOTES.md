### Android 伴侣应用（测试版）

- `mihondesk-companion-0.2.21.apk`（约 2.2 MB）：手机端伴侣应用。自动读取 Mihon 的备份，通过 Google Drive 与桌面端同步书架、分类和阅读进度；也可以手动导入 / 导出 Mihon 备份（`.tachibk`）。
- `mihondesk-companion-0.2.21-debug.apk`（约 21 MB）：未压缩版，小安装包无法打开时使用。

**使用方法**

1. 在 Mihon 中开启「设置 → 数据与存储 → 自动备份」。
2. 在伴侣应用的「同步」页选择 Mihon 的数据目录（包含 `autobackup` 的那个文件夹），并用 Google 账号登录。
3. 之后每次打开应用，都会读取 Mihon 最新的自动备份并同步到 Google Drive；桌面端在「书架同步」中登录同一个 Google 账号即可看到。

**说明**

- 需要 Google Play 服务和手机上已登录的 Google 账号。
- 手机端看到的是 Mihon 上一次自动备份时的状态；电脑上的改动不会自动写回 Mihon，需要时可用「导出为 Mihon 备份」再在 Mihon 中恢复。
- 当前为测试签名，以后换成正式签名时需要先卸载再安装。
- 详细说明见 [mobile-companion/README.md](https://github.com/1873412297-art/mihondesk/blob/main/mobile-companion/README.md)。
