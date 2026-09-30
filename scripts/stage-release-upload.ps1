param(
    [Parameter(Mandatory)][string]$Version,
    [string]$ReleaseRoot,
    [string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'

# The Windows release is uploaded by hand, so the uploaded file set is defined here rather than by
# `assembleWindowsRelease` (whose SHA256SUMS.txt deliberately covers the whole app-image tree so the
# local build can be checked in full). Two consumers constrain the uploaded form:
#   * the in-app updater requires a SHA256SUMS.txt with exactly one entry for the asset it downloads,
#     and only the three artifacts plus the two version files are ever uploaded;
#   * the release checklist requires the manifest to list nothing but the uploaded files.
# Keep this set in sync with scripts/verify-release-assets.ps1.

if (-not $ReleaseRoot) {
    $ReleaseRoot = Join-Path (Split-Path -Parent $PSScriptRoot) "desktop-app/build/releases/$Version"
}
if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path (Split-Path -Parent $PSScriptRoot) "build/release-upload-$Version"
}

$artifacts = @(
    "mihondesk-$Version.msi",
    "mihondesk-$Version.exe",
    "mihondesk-$Version-windows-x64-portable.zip"
)
$support = @('desktop-version.txt', 'mihon-build-info.properties')

if (-not (Test-Path -LiteralPath $ReleaseRoot)) {
    throw "Release directory not found: $ReleaseRoot (run :desktop-app:assembleWindowsRelease first)"
}

$missing = @($artifacts + $support | Where-Object {
    -not (Test-Path -LiteralPath (Join-Path $ReleaseRoot $_))
})
if ($missing.Count -gt 0) {
    throw "Missing release file(s) in ${ReleaseRoot}: $($missing -join ', ')"
}

# Refuse to stage a package set that does not describe the version being released.
$declared = (Get-Content -LiteralPath (Join-Path $ReleaseRoot 'desktop-version.txt') -Raw).Trim()
if ($declared -ne $Version) {
    throw "desktop-version.txt in ${ReleaseRoot} says '$declared' but '$Version' was requested"
}
$buildInfo = Get-Content -LiteralPath (Join-Path $ReleaseRoot 'mihon-build-info.properties') -Raw
if ($buildInfo -notmatch "(?m)^version=$([regex]::Escape($Version))\s*$") {
    throw "mihon-build-info.properties in ${ReleaseRoot} does not record version=$Version"
}

if (Test-Path -LiteralPath $OutputDirectory) {
    Remove-Item -LiteralPath $OutputDirectory -Recurse -Force
}
New-Item -ItemType Directory -Path $OutputDirectory | Out-Null

# Sorted so the manifest order is stable between releases; CRLF and no BOM, matching the uploaded
# files of the previous releases (the updater trims each line before matching).
$uploaded = @($artifacts + $support | Sort-Object)
foreach ($name in $uploaded) {
    Copy-Item -LiteralPath (Join-Path $ReleaseRoot $name) -Destination (Join-Path $OutputDirectory $name)
}

$lines = $uploaded | ForEach-Object {
    $hash = (Get-FileHash -LiteralPath (Join-Path $OutputDirectory $_) -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $_"
}
Set-Content -LiteralPath (Join-Path $OutputDirectory 'SHA256SUMS.txt') -Value $lines -Encoding ascii

Write-Host "Staged in ${OutputDirectory}:"
foreach ($name in $uploaded) { Write-Host "  $name" }
Write-Host "  SHA256SUMS.txt ($($uploaded.Count) entries)"
Write-Host ''
Write-Host "Upload these $($uploaded.Count + 1) files to the release, then run:"
Write-Host "  scripts/verify-release-assets.ps1 -Tag v$Version"
