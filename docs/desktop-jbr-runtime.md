# Desktop runtime: JetBrains Runtime 21

Since 0.3.1 the packaged mihondesk runtime is **JBR 21** (JetBrains Runtime). Its
`WindowDecorations.CustomTitleBar` API (via `org.jetbrains.runtime:jbr-api`) draws the 32dp Ink
caption while keeping the native Windows frame: Snap, Snap Layouts (hover maximize), the system
menu (Alt+Space or click the app icon), double-click maximize and native caption buttons (close
hover #C42B1C).

## Building

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.gradle\jdks\jetbrains_s_r_o_-21-amd64-windows.2"
.\gradlew.bat :desktop-app:assembleWindowsRelease
```

- The app runtime image is jlinked from a JetBrains JDK 21 toolchain (Gradle resolves it from
  `~/.gradle/jdks`). Set `MIHON_JBR_HOME` to force a specific JBR install.
- Kotlin still compiles to the JDK 17 toolchain, and the MSI/EXE step uses JDK 17's `jpackage`
  because `packaging/windows` WiX overrides predate JDK 21's templates (JDK 21 adds `JpCaDll`).

## Fallbacks

- Off JBR, in Windows high-contrast mode, or with **Settings > Advanced > Use system title bar**
  (applies after restart), the window keeps the normal system caption.
- Borderless / fullscreen reading hides the Compose caption strip without rebuilding the window;
  native caption buttons stay reachable.