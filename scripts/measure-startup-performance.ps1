[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Executable,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [ValidateRange(5, 200)][int]$Samples = 30,
    [int]$WarmupRuns = 3,
    [int]$TimeoutSeconds = 45,
    [int]$CooldownMilliseconds = 400
)
$ErrorActionPreference = 'Stop'
$exePath = (Resolve-Path -LiteralPath $Executable).Path
if ([IO.Path]::GetFileName($exePath) -ne 'mihondesk.exe') { throw 'Expected packaged mihondesk.exe' }
$outputRoot = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $outputRoot) { throw 'Use a new output directory for each run' }
New-Item -ItemType Directory -Path $outputRoot | Out-Null
$dataRoot = Join-Path $outputRoot 'data'
New-Item -ItemType Directory -Path $dataRoot | Out-Null

$identity = [ordered]@{
    executable = $exePath
    executableSha256 = (Get-FileHash -LiteralPath $exePath -Algorithm SHA256).Hash
    samples = $Samples
    warmupRuns = $WarmupRuns
    startedUtc = [DateTime]::UtcNow.ToString('o')
    method = 'Process start -> first startup artifact (preferences.properties) appears in the isolated data dir, polled every 50 ms; MainWindowHandle as secondary signal. Graceful close between runs.'
    operatingSystem = (Get-CimInstance Win32_OperatingSystem | Select-Object Caption,Version,BuildNumber)
    processor = @(Get-CimInstance Win32_Processor | Select-Object Name,NumberOfCores,NumberOfLogicalProcessors)
    physicalMemoryBytes = (Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory
}
$identity | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $outputRoot 'identity.json')

function Measure-Launch([string]$Arguments, [string]$DataDir, [int]$TimeoutSec) {
    # Remove stale startup artifacts from previous runs so the signal is fresh.
    $marker = Join-Path $DataDir 'preferences.properties'
    if (Test-Path -LiteralPath $marker) { Remove-Item -LiteralPath $marker -Force }
    $sw = [Diagnostics.Stopwatch]::StartNew()
    $proc = Start-Process -FilePath $exePath -ArgumentList $Arguments -WindowStyle Hidden -PassThru
    try {
        $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSec)
        $ready = $false
        $viaWindow = $false
        while ([DateTime]::UtcNow -lt $deadline) {
            $proc.Refresh()
            if ($proc.HasExited) { break }
            if (Test-Path -LiteralPath $marker) { $ready = $true; break }
            if ($proc.MainWindowHandle -ne 0) { $ready = $true; $viaWindow = $true; break }
            Start-Sleep -Milliseconds 50
        }
        $sw.Stop()
        [pscustomobject]@{
            ready = $ready
            viaWindow = $viaWindow
            milliseconds = $sw.Elapsed.TotalMilliseconds
            exitCode = if ($proc.HasExited) { $proc.ExitCode } else { $null }
        }
    } finally {
        if (-not $proc.HasExited) {
            try { $proc.CloseMainWindow() | Out-Null } catch { }
            if (-not $proc.WaitForExit(10000)) { try { $proc.Kill() } catch { } }
        }
    }
}

$results = [Collections.Generic.List[object]]::new()

# First-run (cold, no caches): a dedicated fresh data dir, measured once.
$coldDir = Join-Path $dataRoot 'first-run'
New-Item -ItemType Directory -Path $coldDir | Out-Null
$cold = Measure-Launch "--portable --data-dir=`"$coldDir`"" $coldDir $TimeoutSeconds
$cold | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outputRoot 'first-run.json')
$results.Add([pscustomobject]@{ kind = 'first-run'; milliseconds = $cold.milliseconds; ready = $cold.ready })
Start-Sleep -Milliseconds $CooldownMilliseconds

# Warm-up runs against the shared data dir.
$warmDir = Join-Path $dataRoot 'warm'
New-Item -ItemType Directory -Path $warmDir | Out-Null
for ($i = 0; $i -lt $WarmupRuns; $i++) {
    Measure-Launch "--portable --data-dir=`"$warmDir`"" $warmDir $TimeoutSeconds | Out-Null
    Start-Sleep -Milliseconds $CooldownMilliseconds
}

# Measured warm runs.
$rows = [Collections.Generic.List[object]]::new()
for ($i = 1; $i -le $Samples; $i++) {
    $r = Measure-Launch "--portable --data-dir=`"$warmDir`"" $warmDir $TimeoutSeconds
    $rows.Add($r)
    $results.Add([pscustomobject]@{ kind = 'warm'; milliseconds = $r.milliseconds; ready = $r.ready })
    Start-Sleep -Milliseconds $CooldownMilliseconds
}
$rows | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $outputRoot 'warm-samples.json')

$warmMs = @($rows | Where-Object ready | ForEach-Object { $_.milliseconds } | Sort-Object)
function Get-Percentile([double[]]$sorted, [double]$p) {
    if ($sorted.Count -eq 0) { return $null }
    $rank = [Math]::Ceiling($p / 100.0 * $sorted.Count) - 1
    if ($rank -lt 0) { $rank = 0 }
    if ($rank -ge $sorted.Count) { $rank = $sorted.Count - 1 }
    return $sorted[$rank]
}
$summary = [ordered]@{
    firstRunMilliseconds = $cold.milliseconds
    firstRunReady = $cold.ready
    warmSamples = $warmMs.Count
    warmNotReady = ($rows | Where-Object { -not $_.ready }).Count
    warmMedianMilliseconds = if ($warmMs.Count) { Get-Percentile $warmMs 50 } else { $null }
    warmP95Milliseconds = if ($warmMs.Count) { Get-Percentile $warmMs 95 } else { $null }
    warmMinMilliseconds = if ($warmMs.Count) { $warmMs[0] } else { $null }
    warmMaxMilliseconds = if ($warmMs.Count) { $warmMs[$warmMs.Count - 1] } else { $null }
}
$summary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outputRoot 'summary.json')
$summary.GetEnumerator() | ForEach-Object { Write-Output ('{0}: {1}' -f $_.Key, $_.Value) }
Write-Output "Output: $outputRoot"
