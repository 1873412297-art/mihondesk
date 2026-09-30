<#
.SYNOPSIS
    Removes stale per-user Windows tray icon registrations left behind by copies of the app.

.DESCRIPTION
    Every run of mihondesk.exe makes Windows record a persistent tray icon registration for that
    executable path under HKCU\Control Panel\NotifyIconSettings\<hash> (values include
    ExecutablePath, InitialTooltip, IconSnapshot and UID); the packaged runtime installs a
    java.awt tray icon for any command, so even a headless run such as `mihondesk.exe --version`
    registers one. The entry survives deleting the copy, so Windows keeps showing the app icon in
    the notification area and in Settings > Personalization > Taskbar > Other system tray icons.
    Restarting explorer.exe does not clear it while the entry exists. Because the icon identity is
    derived from the executable path (and from the launcher name, so an old MihonW.exe copy counts
    as a *different* application than mihondesk.exe), every scratch, sandbox, worktree or soak copy
    that was ever started leaves another entry behind.

    This script removes the entries whose ExecutablePath is inside -PathPrefix, so a whole scratch
    tree can be swept with a single call. It only writes to the current user's
    HKCU\Control Panel\NotifyIconSettings key, so no elevation is required, and it never removes an
    entry whose ExecutablePath is outside -PathPrefix.

    Safety: an entry whose executable belongs to a currently running process is kept by default, so
    a sweep can never hide the tray icon of the app the user is actually running. Use
    -IncludeLiveProcesses to remove those as well (the icon disappears until that copy starts
    again and re-registers it).

    Per-entry failures (access denied, a subkey held open by the shell) are reported and skipped
    instead of terminating the script, and a missing HKCU\Control Panel\NotifyIconSettings key is
    not an error. The script completes with exit code 0 in every case, including "nothing matched",
    so it is safe to call from other verification scripts.

.PARAMETER PathPrefix
    Directory to sweep, for example an extracted sandbox or a test/worktree build directory. Every
    matching entry (ExecutablePath equal to, or below, this directory) is removed. The value is
    normalised with [IO.Path]::GetFullPath and compared case-insensitively, so relative paths and
    trailing separators are accepted. The directory does not have to exist any more.

.PARAMETER KeepLiveProcesses
    Skip entries whose executable is a currently running process. This is already the default; the
    switch exists so callers can state the safe behaviour explicitly.

.PARAMETER IncludeLiveProcesses
    Also remove entries whose executable is a currently running process. This is the risky mode:
    the running copy loses its tray icon until it registers one again. Cannot be combined with
    -KeepLiveProcesses.

.EXAMPLE
    ./clear-tray-icon-registrations.ps1 -PathPrefix "$env:TEMP\mihon-sandbox-1a2b3c4d" -WhatIf
    Lists what would be removed for that sandbox without changing the registry.

.EXAMPLE
    ./clear-tray-icon-registrations.ps1 -PathPrefix 'D:\my project\mihon-w\build' -KeepLiveProcesses
    Sweeps every build directory under the repository, keeping registrations of running copies.

.NOTES
    Console output only (Write-Host): one line per removed entry, then a summary count.
    Entries created by the app's own runtime usually have no InitialTooltip value, so "(no tooltip)"
    is the normal shape rather than a sign of a malformed entry.
    Known limitation: a process whose image path cannot be queried (elevated process owned by
    another user) is invisible to the running-process check, so its registration can be swept in
    the default mode. This only affects entries inside -PathPrefix.
#>
[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)][string]$PathPrefix,
    [switch]$KeepLiveProcesses,
    [switch]$IncludeLiveProcesses
)
$ErrorActionPreference = 'Continue'
if ($IncludeLiveProcesses -and $KeepLiveProcesses) {
    throw 'Pass either -KeepLiveProcesses (the default) or -IncludeLiveProcesses, not both'
}

function Get-RunningProcessPaths {
    $paths = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($process in @(Get-Process -ErrorAction SilentlyContinue)) {
        $image = $null
        try { $image = $process.Path } catch { $image = $null }
        if ([string]::IsNullOrWhiteSpace($image)) { continue }
        try { [void]$paths.Add([IO.Path]::GetFullPath($image)) } catch { }
    }
    return $paths
}

function Invoke-TrayRegistrationCleanup {
    $settingsKey = 'HKCU:\Control Panel\NotifyIconSettings'
    $prefix = [IO.Path]::GetFullPath($PathPrefix).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    $prefixWithSeparator = $prefix + [IO.Path]::DirectorySeparatorChar
    Write-Host "Clearing tray icon registrations under: $prefix"

    if (-not (Test-Path -LiteralPath $settingsKey)) {
        Write-Host "Registry key $settingsKey does not exist; nothing to remove."
        return
    }

    $matched = 0
    $removed = 0
    $wouldRemove = 0
    $declined = 0
    $skippedLive = 0
    $failed = 0
    $liveProcessPaths = $null

    foreach ($entry in @(Get-ChildItem -LiteralPath $settingsKey -ErrorAction SilentlyContinue)) {
        $properties = Get-ItemProperty -LiteralPath $entry.PSPath -ErrorAction SilentlyContinue
        if (-not $properties) { continue }
        $executableValue = $properties.PSObject.Properties['ExecutablePath']
        if (-not $executableValue) { continue }
        $executable = [string]$executableValue.Value
        if ([string]::IsNullOrWhiteSpace($executable)) { continue }
        # Registry values can hold shell-relative names ("{FOLDERID}\app.exe"); those are never
        # under an absolute prefix, and resolving them against the current directory could match.
        if (-not [IO.Path]::IsPathRooted($executable)) { continue }
        $normalized = $null
        try { $normalized = [IO.Path]::GetFullPath($executable) } catch { continue }
        if (-not $normalized.StartsWith($prefixWithSeparator, [StringComparison]::OrdinalIgnoreCase)) { continue }
        $matched++

        $tooltipValue = $properties.PSObject.Properties['InitialTooltip']
        $tooltip = if ($tooltipValue) { [string]$tooltipValue.Value } else { '' }
        $tooltipNote = if ([string]::IsNullOrWhiteSpace($tooltip)) { 'no tooltip' } else { "tooltip: $tooltip" }

        if (-not $IncludeLiveProcesses) {
            if ($null -eq $liveProcessPaths) { $liveProcessPaths = Get-RunningProcessPaths }
            if ($liveProcessPaths.Contains($normalized)) {
                $skippedLive++
                Write-Host "Kept (belongs to a running process): $normalized ($tooltipNote)"
                continue
            }
        }

        $target = "$settingsKey\$($entry.PSChildName)"
        if (-not $PSCmdlet.ShouldProcess($target, "Remove tray icon registration for $normalized")) {
            if ($WhatIfPreference) { $wouldRemove++ } else { $declined++ }
            Write-Host "Would remove: $normalized ($tooltipNote)"
            continue
        }
        try {
            Remove-Item -LiteralPath $entry.PSPath -Recurse -Force -ErrorAction Stop
            $removed++
            Write-Host "Removed: $normalized ($tooltipNote)"
        } catch {
            $failed++
            Write-Warning "Could not remove $($entry.PSChildName) ($normalized): $($_.Exception.Message)"
        }
    }

    Write-Host ''
    if ($matched -eq 0) {
        Write-Host "No tray icon registration is under $prefix; nothing to remove."
        return
    }
    $summary = if ($WhatIfPreference) {
        "Would remove $wouldRemove of $matched matching tray icon registration(s) for $prefix; nothing was changed."
    } else {
        "Removed $removed of $matched matching tray icon registration(s) for $prefix."
    }
    if ($declined -gt 0) { $summary += " Not confirmed: $declined." }
    if ($skippedLive -gt 0) { $summary += " Kept as running process: $skippedLive." }
    if ($failed -gt 0) { $summary += " Failed: $failed." }
    Write-Host $summary
}

if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) {
    Write-Host 'Not running on Windows; no tray icon registrations to clear.'
    return
}
try {
    Invoke-TrayRegistrationCleanup
} catch {
    Write-Warning "Tray icon registration cleanup aborted: $($_.Exception.Message)"
}
