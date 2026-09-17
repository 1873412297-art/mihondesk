[CmdletBinding()]
param([Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $OutputDirectory).Path
$result = Get-Content -LiteralPath (Join-Path $root 'result.json') -Raw | ConvertFrom-Json
if (-not $result.accepted) { throw 'Reader workload has not completed successfully' }
$readerRows = @(Get-Content -LiteralPath (Join-Path $root 'reader-samples.jsonl') | ForEach-Object { $_ | ConvertFrom-Json })
$memoryFile = @(Get-ChildItem -LiteralPath $root -Filter 'process-memory-*.jsonl')
if ($memoryFile.Count -ne 1) { throw 'Expected exactly one process-memory stream' }
$memoryRows = @(Get-Content -LiteralPath $memoryFile[0].FullName | ForEach-Object { $_ | ConvertFrom-Json })
if ($readerRows.Count -lt 2 -or $memoryRows.Count -lt 1) { throw 'Insufficient memory samples' }
$verification = Get-Content -LiteralPath (Join-Path $root 'stdout.json') -Raw | ConvertFrom-Json
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
    distinctReaderPids = @($readerRows.processId | Sort-Object -Unique)
    readerSamples = $readerRows.Count
    processSamples = $memoryRows.Count
    heapUsed = Describe @($readerRows | ForEach-Object { $_.heapUsedBytes })
    workingSet = Describe @($memoryRows | ForEach-Object { $_.workingSetBytes })
    privateBytes = Describe @($memoryRows | ForEach-Object { $_.privateBytes })
    coreHighWaterBytes = $verification.coreResidentAndInFlightHighWaterBytes
    cacheHighWaterBytes = $verification.cacheResidentHighWaterBytes
    windows = $windows
    limitations = @(
        'Headless active decoding/session workload; no Compose frame timing or UI responsiveness measurement.',
        'Heap samples occur between completed cycles; core high-water uses the independent verifier poller.',
        'Process sampling starts after launch and uses its own elapsed clock; all child processes are included.',
        'Five-minute windows are descriptive evidence; no automatic memory plateau claim is made.',
        'This is the current development machine, not clean Win10/Win11 release acceptance.'
    )
}
$analysis | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $root 'analysis.json')
$analysis | ConvertTo-Json -Depth 8
