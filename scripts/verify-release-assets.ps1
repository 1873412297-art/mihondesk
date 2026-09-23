param(
    [Parameter(Mandatory)][string]$Tag,
    [string]$Repository = '1873412297-art/mihondesk'
)
$ErrorActionPreference = 'Stop'

# The in-app updater hard-requires a SHA256SUMS.txt asset and an exact
# releases/download/<tag>/<name> URL per trusted asset, so a release missing any of
# these is not installable from inside the app even though the files download fine
# by hand. Verify the published release against those rules rather than trusting
# that the upload step was complete.

if ($Tag -notmatch '^v?[0-9]+\.[0-9]+\.[0-9]+$') {
    throw "Tag '$Tag' is not a version tag (expected vX.Y.Z)"
}
$version = $Tag.TrimStart('v')

$releaseJson = gh release view $Tag --repo $Repository --json tagName,isDraft,isPrerelease,assets
if ($LASTEXITCODE -ne 0) { throw "Release $Tag not found in $Repository" }
$release = $releaseJson | ConvertFrom-Json

$names = @($release.assets | ForEach-Object { $_.name })
Write-Output "Release $Tag has $($names.Count) asset(s): $($names -join ', ')"

$required = @(
    'SHA256SUMS.txt',
    'desktop-version.txt',
    'mihon-build-info.properties',
    "mihondesk-$version.msi",
    "mihondesk-$version.exe",
    "mihondesk-$version-windows-x64-portable.zip"
)
# The manifest covers every uploaded file except itself.
$manifested = @($required | Where-Object { $_ -ne 'SHA256SUMS.txt' })
$missing = @($required | Where-Object { $_ -notin $names })
if ($missing.Count -gt 0) {
    throw "Release $Tag is missing required asset(s): $($missing -join ', ')"
}

# Every asset name must be safe and every download URL must be the canonical
# releases/download/<tag>/<name> form the updater accepts.
$urlPattern = "^https://github\.com/$([regex]::Escape($Repository))/releases/download/$([regex]::Escape($Tag))/"
foreach ($asset in $release.assets) {
    if ($asset.name -notmatch '^[a-zA-Z0-9._-]+$') {
        throw "Asset name is not updater-safe: $($asset.name)"
    }
    if ($asset.url -notmatch $urlPattern) {
        throw "Asset URL is not the canonical release download path: $($asset.name) -> $($asset.url)"
    }
    if ($asset.size -le 0) {
        throw "Asset reports a zero size: $($asset.name)"
    }
}

# The manifest must carry exactly one hash line per downloadable artifact, matching
# the names above (the updater rejects missing or ambiguous entries).
$tempDir = Join-Path ([IO.Path]::GetTempPath()) ("release-assets-" + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tempDir | Out-Null
try {
    gh release download $Tag --repo $Repository --pattern 'SHA256SUMS.txt' --dir $tempDir --clobber | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Could not download SHA256SUMS.txt from $Tag" }
    $manifestPath = Join-Path $tempDir 'SHA256SUMS.txt'
    if (-not (Test-Path -LiteralPath $manifestPath)) { throw "SHA256SUMS.txt did not download" }

    $entries = @{}
    foreach ($line in Get-Content -LiteralPath $manifestPath) {
        $match = [regex]::Match($line, '^([a-fA-F0-9]{64}) [ *](.+)$')
        if (-not $match.Success) { continue }
        $name = $match.Groups[2].Value
        if ($entries.ContainsKey($name)) {
            throw "Manifest lists $name more than once (updater rejects ambiguous checksums)"
        }
        $entries[$name] = $match.Groups[1].Value.ToLowerInvariant()
    }
    Write-Output "Manifest lists $($entries.Count) entr(y/ies)."

    foreach ($name in $manifested) {
        if (-not $entries.ContainsKey($name)) {
            throw "Manifest has no SHA-256 entry for $name (in-app update would fail)"
        }
    }

    # Compare the published hashes with the artifacts this checkout just built,
    # when they are available - that is the only way to catch an upload that
    # replaced a file after the manifest was written.
    $localRoot = Join-Path (Split-Path -Parent $PSScriptRoot) "desktop-app/build/releases/$version"
    if (Test-Path -LiteralPath $localRoot) {
        foreach ($name in $manifested) {
            $local = Join-Path $localRoot $name
            if (-not (Test-Path -LiteralPath $local)) { continue }
            $actual = (Get-FileHash -LiteralPath $local -Algorithm SHA256).Hash.ToLowerInvariant()
            if ($actual -ne $entries[$name]) {
                throw "Published hash for $name does not match the local build ($actual vs $($entries[$name]))"
            }
        }
        Write-Output "Published hashes match the local build under desktop-app/build/releases/$version."
    } else {
        Write-Output "Local build directory not present; compared the manifest shape only."
    }
} finally {
    Remove-Item -LiteralPath $tempDir -Recurse -Force -ErrorAction SilentlyContinue
}

if ($release.isDraft) { throw "Release $Tag is still a draft" }
Write-Output "PASS: release $Tag carries every asset the in-app updater needs, and the manifest covers them."
