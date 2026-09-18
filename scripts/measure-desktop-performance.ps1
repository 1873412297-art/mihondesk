[CmdletBinding()]
param(
    [Parameter(Mandatory)][int]$ProcessId,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [ValidateRange(1, 1440)][int]$DurationMinutes = 30,
    [ValidateRange(1, 60)][int]$IntervalSeconds = 5,
    [string]$JcmdPath
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'desktop-process-tree.ps1')
$rootProcess = Get-Process -Id $ProcessId -ErrorAction Stop
$expectedPath = $rootProcess.Path
$expectedCreatedUtc = $rootProcess.StartTime.ToUniversalTime()
if ([IO.Path]::GetFileName($expectedPath) -ne 'mihondesk.exe') { throw 'Target must be a running mihondesk executable' }
$outputRoot = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null
$runName = 'process-memory-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss')
$samplesPath = Join-Path $outputRoot "$runName.jsonl"
$heapPath = Join-Path $outputRoot "$runName-heap.txt"
$started = [DateTime]::UtcNow
$nextHeap = $started
$samples = [Collections.Generic.List[object]]::new()
while (([DateTime]::UtcNow - $started).TotalMinutes -lt $DurationMinutes) {
    $currentRoot = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if (-not $currentRoot -or $currentRoot.Path -ne $expectedPath -or
        -not (Test-DesktopProcessIdentity $expectedCreatedUtc $currentRoot.StartTime)) { break }
    $processRows = @(Get-CimInstance Win32_Process -Property ProcessId,ParentProcessId,Name,CreationDate)
    $treeRows = @(Get-DesktopProcessTree -RootProcessId $ProcessId -RootCreatedUtc $expectedCreatedUtc -Rows $processRows)
    if ($treeRows.Count -eq 0) { Start-Sleep -Seconds $IntervalSeconds; continue }
    $members = @(foreach ($row in $treeRows) {
        $item = Get-Process -Id $row.ProcessId -ErrorAction SilentlyContinue
        if ($item) {
            try { $createdUtc = $item.StartTime.ToUniversalTime() } catch { continue }
            if (-not (Test-DesktopProcessIdentity $row.CreationDate $createdUtc)) { continue }
            $memberSample = [pscustomobject]@{
                processId = [int]$row.ProcessId
                parentProcessId = [int]$row.ParentProcessId
                createdUtc = $createdUtc.ToString('o')
                name = $item.ProcessName
                workingSetBytes = $item.WorkingSet64
                privateBytes = $item.PrivateMemorySize64
                cpuSeconds = $item.CPU
            }
            if (-not $item.HasExited) { $memberSample }
        }
    })
    # All processes can exit between identity enumeration and metric collection.
    # An unobserved tree is not a zero-byte measurement.
    if ($members.Count -eq 0) { Start-Sleep -Seconds $IntervalSeconds; continue }
    $sample = [pscustomobject]@{
        utc = [DateTime]::UtcNow.ToString('o')
        elapsedSeconds = [Math]::Round(([DateTime]::UtcNow - $started).TotalSeconds, 2)
        workingSetBytes = ($members | Measure-Object workingSetBytes -Sum).Sum
        privateBytes = ($members | Measure-Object privateBytes -Sum).Sum
        processes = $members
    }
    $samples.Add($sample)
    $sample | ConvertTo-Json -Depth 4 -Compress | Add-Content -LiteralPath $samplesPath -Encoding utf8
    if ($JcmdPath -and [DateTime]::UtcNow -ge $nextHeap) {
        foreach ($member in $members) {
            $candidate = Get-Process -Id $member.processId -ErrorAction SilentlyContinue
            if (-not $candidate -or
                -not (Test-DesktopProcessIdentity ([DateTime]$member.createdUtc) $candidate.StartTime)) { continue }
            try { $isJvm = @($candidate.Modules | Where-Object ModuleName -eq 'jvm.dll').Count -gt 0 }
            catch { $isJvm = $false }
            if ($isJvm) {
                "UTC $([DateTime]::UtcNow.ToString('o')) JVM $($member.processId)" | Add-Content -LiteralPath $heapPath
                & $JcmdPath $member.processId GC.heap_info 2>&1 | Add-Content -LiteralPath $heapPath
            }
        }
        $nextHeap = [DateTime]::UtcNow.AddSeconds(60)
    }
    Start-Sleep -Seconds $IntervalSeconds
}
[pscustomobject]@{
    executable = $expectedPath
    rootProcessId = $ProcessId
    rootCreatedUtc = $expectedCreatedUtc.ToString('o')
    identityPolicy = 'Root PID plus creation time; every child starts no earlier than its parent; identities rechecked before sampling.'
    requestedMinutes = $DurationMinutes
    sampledSeconds = [Math]::Round(([DateTime]::UtcNow - $started).TotalSeconds, 2)
    samples = $samples.Count
    maxWorkingSetBytes = ($samples | Measure-Object workingSetBytes -Maximum).Maximum
    maxPrivateBytes = ($samples | Measure-Object privateBytes -Maximum).Maximum
    samplesPath = $samplesPath
    heapPath = if ($JcmdPath) { $heapPath } else { $null }
    note = 'Process-tree memory sampling only; this does not prove active reading, frame times, or UI responsiveness.'
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outputRoot "$runName-summary.json")
Get-Content -LiteralPath (Join-Path $outputRoot "$runName-summary.json")
