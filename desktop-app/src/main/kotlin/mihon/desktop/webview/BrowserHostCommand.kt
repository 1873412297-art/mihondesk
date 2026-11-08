package mihon.desktop.webview

import mihon.desktop.platform.ProfileTemporaryDirectory
import java.nio.file.Path

/** The `desktop-webview-host` JVM command line; its temp files stay inside the profile. */
internal fun browserHostCommand(browserRuntime: Path, hostDistribution: Path): List<String> = listOf(
    browserRuntime.resolve("bin/java.exe").toString(),
    *ProfileTemporaryDirectory.childJvmArguments().toTypedArray(),
    "--add-modules=jcef",
    "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
    "-cp",
    hostDistribution.resolve("lib").toString() + "/*",
    "mihon.webview.MainKt",
)
