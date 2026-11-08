package mihon.desktop.i18n

/** Strings for the renderer and DNS settings, kept apart from [UiText], which is at its size limit. */
enum class CompatibilityText(val english: String, val simplified: String, val traditional: String) {
    RenderTitle("Graphics renderer", "图形渲染器", "圖形繪製器"),
    RenderSummary(
        "Pick a fixed renderer if the window flickers, stays black or crashes. Applies after a restart.",
        "窗口闪烁、黑屏或崩溃时可固定渲染器。重启后生效。",
        "視窗閃爍、黑屏或當機時可固定繪製器。重新啟動後生效。",
    ),
    RenderAuto("Automatic", "自动", "自動"),
    RenderDirect3d("Direct3D", "Direct3D", "Direct3D"),
    RenderOpenGl("OpenGL", "OpenGL", "OpenGL"),
    RenderSoftware("Software (compatibility)", "软件（兼容模式）", "軟體（相容模式）"),
    RenderRestart(
        "Restart mihondesk to use the new renderer.",
        "重启 mihondesk 后使用新的渲染器。",
        "重新啟動 mihondesk 後使用新的繪製器。",
    ),
    DohTitle("DNS over HTTPS", "DNS over HTTPS（加密 DNS）", "DNS over HTTPS（加密 DNS）"),
    DohSummary(
        "Resolves source hosts over HTTPS. Not used with an HTTP or SOCKS proxy, which resolves names itself.",
        "通过 HTTPS 解析图源域名。使用 HTTP 或 SOCKS 代理时由代理解析，不使用此项。",
        "透過 HTTPS 解析圖源網域。使用 HTTP 或 SOCKS 代理時由代理解析，不使用此項。",
    ),
    DohOff("Off", "关闭", "關閉"),
    ;

    fun resolve(strings: DesktopStrings): String = when (strings) {
        SimplifiedChineseStrings -> simplified
        TraditionalChineseStrings -> traditional
        else -> english
    }
}
