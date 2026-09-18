[CmdletBinding()]
param([Parameter(Mandatory)][string]$OutputDirectory, [switch]$MainProcessesOnly)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $OutputDirectory).Path
$result = Get-Content -LiteralPath (Join-Path $root 'result.json') -Raw | ConvertFrom-Json
if (-not $result.accepted) { throw 'Reader workload has not completed successfully' }
$readerRows = @(Get-Content -LiteralPath (Join-Path $root 'reader-samples.jsonl') | ForEach-Object { $_ | ConvertFrom-Json })
$memoryFile = @(Get-ChildItem -LiteralPath $root -Filter 'process-memory-*.jsonl')
if ($memoryFile.Count -ne 1) { throw 'Expected exactly one process-memory stream' }
$memoryRows = @(Get-Content -LiteralPath $memoryFile[0].FullName | ForEach-Object { $_ | ConvertFrom-Json })
$missingProcessSamples = @($memoryRows | Where-Object {
    @($_.processes).Count -eq 0 -or $null -eq $_.workingSetBytes -or $null -eq $_.privateBytes
} | Select-Object utc,elapsedSeconds)
$memoryRows = @($memoryRows | Where-Object {
    @($_.processes).Count -gt 0 -and $null -ne $_.workingSetBytes -and $null -ne $_.privateBytes
})
$processScope = 'process tree'
$excludedProcessSamples = @()
if ($MainProcessesOnly) {
    $ownership = Get-Content -LiteralPath (Join-Path $root 'processes.json') -Raw | ConvertFrom-Json
    $mainIds = @([long]$ownership.launcherPid, [long]$result.soak.processId)
    if (@($mainIds | Where-Object { $_ -le 0 }).Count) { throw 'Missing authoritative launcher or runtime identity' }
    $processScope = 'recorded launcher and reader JVM only; other child processes excluded'
    $excludedProcessSamples = @(foreach ($row in $memoryRows) {
        foreach ($member in $row.processes) {
            if ([long]$member.processId -notin $mainIds) {
                [pscustomobject]@{ elapsedSeconds = $row.elapsedSeconds; processId = $member.processId; name = $member.name }
            }
        }
        $row.processes = @($row.processes | Where-Object { [long]$_.processId -in $mainIds })
        if ($row.processes.Count -eq 0) { throw 'Process sample contains no recorded main process' }
        $row.workingSetBytes = ($row.processes | Measure-Object workingSetBytes -Sum).Sum
        $row.privateBytes = ($row.processes | Measure-Object privateBytes -Sum).Sum
    })
}
if ($readerRows.Count -lt 2 -or $memoryRows.Count -lt 1) { throw 'Insufficient memory samples' }
$verification = Get-Content -LiteralPath (Join-Path $root 'stdout.json') -Raw | ConvertFrom-Json
$readerPids = @($readerRows.processId | Sort-Object -Unique)
if ($readerPids.Count -ne 1 -or $readerPids[0] -ne $result.soak.processId) {
    throw 'Reader samples do not belong to the completed runtime'
}
if ($readerRows[0].cycles -ne 0 -or $readerRows[0].decodedTiles -ne 0 -or
    $readerRows[-1].cycles -ne $result.soak.cycles -or
    $readerRows[-1].decodedTiles -ne $result.soak.decodedTiles) {
    throw 'Reader sample boundaries do not match the completed workload'
}
$maxReaderGap = 0.0
for ($index = 1; $index -lt $readerRows.Count; $index++) {
    $previous = $readerRows[$index - 1]
    $current = $readerRows[$index]
    $gap = $current.elapsedSeconds - $previous.elapsedSeconds
    if ($gap -lt 0 -or $current.cycles -lt $previous.cycles -or $current.decodedTiles -lt $previous.decodedTiles) {
        throw 'Reader sample time or progress moved backwards'
    }
    $maxReaderGap = [Math]::Max($maxReaderGap, $gap)
}
$maxProcessGap = 0.0
for ($index = 1; $index -lt $memoryRows.Count; $index++) {
    $gap = $memoryRows[$index].elapsedSeconds - $memoryRows[$index - 1].elapsedSeconds
    if ($gap -lt 0) { throw 'Process sample time moved backwards' }
    $maxProcessGap = [Math]::Max($maxProcessGap, $gap)
}
$observedProcessIds = @($memoryRows | ForEach-Object { $_.processes.processId } | Sort-Object -Unique)
if ($observedProcessIds -notcontains $result.soak.processId) {
    throw 'Process memory stream did not observe the reader runtime'
}
function Describe([double[]]$Values) {
    if ($Values.Count -eq 0) { return $null }
    $sorted = @($Values | Sort-Object)
    $middle = [int][Math]::Floor($sorted.Count / 2)
    $median = if ($sorted.Count % 2) { $sorted[$middle] } else { ($sorted[$middle - 1] + $sorted[$middle]) / 2 }
    [pscustomobject]@{
        samples = $sorted.Count
        minMiB = [Math]::Round($sorted[0] / 1MB, 3)
        medianMiB = [Math]::Round($median / 1MB, 3)
        maxMiB = [Math]::Round($sorted[-1] / 1MB, 3)
    }
}
$windows = @(for ($start = 0; $start -lt $result.soak.elapsedSeconds; $start += 300) {
    $readerWindow = @($readerRows | Where-Object { $_.elapsedSeconds -ge $start -and $_.elapsedSeconds -lt ($start + 300) })
    $processWindow = @($memoryRows | Where-Object { $_.elapsedSeconds -ge $start -and $_.elapsedSeconds -lt ($start + 300) })
    [pscustomobject]@{
        startMinute = $start / 60
        endMinute = [Math]::Min([double]($start + 300) / 60.0, [double]$result.soak.elapsedSeconds / 60.0)
        heapUsed = Describe @($readerWindow | ForEach-Object { $_.heapUsedBytes })
        workingSet = Describe @($processWindow | ForEach-Object { $_.workingSetBytes })
        privateBytes = Describe @($processWindow | ForEach-Object { $_.privateBytes })
    }
})
$analysis = [ordered]@{
    identity = (Get-Content -LiteralPath (Join-Path $root 'identity.json') -Raw | ConvertFrom-Json)
    soak = $result.soak
    verifiedAssets = $verification.verifiedAssets
    verifiedModes = $verification.verifiedModes
    distinctReaderPids = $readerPids
    continuity = [ordered]@{
        readerBoundariesMatchCompletedWorkload = $true
        readerFirstSampleSeconds = $readerRows[0].elapsedSeconds
        readerLastSampleSeconds = $readerRows[-1].elapsedSeconds
        maxReaderSampleGapSeconds = [Math]::Round($maxReaderGap, 3)
        processFirstSampleSeconds = $memoryRows[0].elapsedSeconds
        processLastSampleSeconds = $memoryRows[-1].elapsedSeconds
        maxProcessSampleGapSeconds = [Math]::Round($maxProcessGap, 3)
        observedProcessIds = $observedProcessIds
    }
    readerSamples = $readerRows.Count
    processSamples = $memoryRows.Count
    missingProcessSamples = $missingProcessSamples
    processScope = $processScope
    excludedProcessSamples = $excludedProcessSamples
    heapUsed = Describe @($readerRows | ForEach-Object { $_.heapUsedBytes })
    workingSet = Describe @($memoryRows | ForEach-Object { $_.workingSetBytes })
    privateBytes = Describe @($memoryRows | ForEach-Object { $_.privateBytes })
    coreHighWaterBytes = $verification.coreResidentAndInFlightHighWaterBytes
    cacheHighWaterBytes = $verification.cacheResidentHighWaterBytes
    windows = $windows
    limitations = @(
        'Headless active decoding/session workload; no Compose frame timing or UI responsiveness measurement.',
        'Heap samples occur between completed cycles; core high-water uses the independent verifier poller.',
        "Process sampling starts after launch and uses its own elapsed clock; scope: $processScope.",
        'Five-minute windows are descriptive evidence; no automatic memory plateau claim is made.',
        'This is the current development machine, not clean Win10/Win11 release acceptance.'
    )
}
$analysis | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $root 'analysis.json')
$analysis | ConvertTo-Json -Depth 8
