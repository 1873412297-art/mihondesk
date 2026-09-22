param([Parameter(Mandatory)][string]$ImagePath)
$ErrorActionPreference = 'Stop'

# The jpackage runtime is a jlink image built from the module list in desktop-app/build.gradle.kts.
# A JDK module used by the app but missing from that list resolves fine on a developer JDK and
# fails only in the packaged build (NoClassDefFoundError at first use), so verify the image itself.
$imageRoot = (Resolve-Path -LiteralPath $ImagePath).Path
$releaseFile = Join-Path $imageRoot 'runtime/release'
if (-not (Test-Path -LiteralPath $releaseFile)) {
    throw "Packaged runtime release file is missing: $releaseFile"
}

$release = Get-Content -LiteralPath $releaseFile -Raw
$moduleMatch = [regex]::Match($release, 'MODULES="([^"]+)"')
if (-not $moduleMatch.Success) {
    throw "Packaged runtime release file does not declare MODULES"
}
$modules = $moduleMatch.Groups[1].Value -split '\s+' | Where-Object { $_ }

# Modules the application actually reaches: jdk.httpserver backs the WebView broker,
# java.net.http backs jsoup, jdk.dynalink backs the script engines.
$required = @(
    'java.base',
    'java.desktop',
    'java.instrument',
    'java.logging',
    'java.prefs',
    'java.sql',
    'java.xml',
    'jdk.unsupported',
    'jdk.httpserver',
    'java.net.http',
    'jdk.dynalink'
)
$missing = @($required | Where-Object { $_ -notin $modules })
if ($missing.Count -gt 0) {
    throw "Packaged runtime is missing required modules: $($missing -join ', ')"
}
Write-Output "PASS: packaged runtime contains every required module ($($required.Count) checked, $($modules.Count) present)."
