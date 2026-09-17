[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Executable,
    [Parameter(Mandatory)][string]$FixtureDirectory,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [ValidateRange(1, 3600)][int]$DurationSeconds = 1800
)
$ErrorActionPreference = 'Stop'
$executablePath = (Resolve-Path -LiteralPath $Executable).Path
$fixturePath = (Resolve-Path -LiteralPath $FixtureDirectory).Path
$outputPath = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $outputPath) { throw 'Use a new output directory for each run' }
if ([IO.Path]::GetFileName($executablePath) -ne 'mihondesk.exe') { throw 'Expected packaged mihondesk.exe' }
New-Item -ItemType Directory -Path $outputPath | Out-Null
$appJar = @(Get-ChildItem (Join-Path (Split-Path $executablePath) 'app') -Filter 'desktop-app-*.jar')
if ($appJar.Count -ne 1) { throw 'Expected one packaged application JAR' }
$archive = [IO.Compression.ZipFile]::OpenRead($appJar[0].FullName)
try {
    $reader = [IO.StreamReader]::new($archive.GetEntry('mihon-build-info.properties').Open())
    try { $metadata = $reader.ReadToEnd() } finally { $reader.Dispose() }
} finally { $archive.Dispose() }
$identity = [ordered]@{
    executable = $executablePath
    executableSha256 = (Get-FileHash -LiteralPath $executablePath -Algorithm SHA256).Hash
    appJar = $appJar[0].FullName
    appJarSha256 = (Get-FileHash -LiteralPath $appJar[0].FullName -Algorithm SHA256).Hash
    buildMetadata = $metadata
    fixtureSha256 = (Get-FileHash -LiteralPath (Join-Path $fixturePath 'reader-fixture-manifest.json')).Hash
    requestedSeconds = $DurationSeconds
    startedUtc = [DateTime]::UtcNow.ToString('o')
    operatingSystem = (Get-CimInstance Win32_OperatingSystem | Select-Object Caption,Version,BuildNumber)
    processor = @(Get-CimInstance Win32_Processor | Select-Object Name,NumberOfCores,NumberOfLogicalProcessors)
    physicalMemoryBytes = (Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory
}
$identity | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $outputPath 'identity.json')
$oldGate = $env:MIHON_W_READER_VERIFY
$oldDuration = $env:MIHON_W_READER_SOAK_SECONDS
$oldSamples = $env:MIHON_W_READER_SOAK_OUTPUT
function Quote-Argument([string]$Value) {
    if ($Value.Contains('"')) { throw 'Quotes in verifier paths are not supported' }
    '"' + $Value + '"'
}
try {
    $env:MIHON_W_READER_VERIFY = '1'
    $env:MIHON_W_READER_SOAK_SECONDS = [string]$DurationSeconds
    $env:MIHON_W_READER_SOAK_OUTPUT = Join-Path $outputPath 'reader-samples.jsonl'
    $arguments = @(
        (Quote-Argument "--verify-reader=$fixturePath"),
        (Quote-Argument "--data-dir=$(Join-Path $outputPath 'profile')")
    )
    $process = Start-Process -FilePath $executablePath -ArgumentList $arguments -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $outputPath 'stdout.json') `
        -RedirectStandardError (Join-Path $outputPath 'stderr.log')
    $samplerArguments = @(
        '-NoProfile', '-File', (Quote-Argument (Join-Path $PSScriptRoot 'measure-desktop-performance.ps1')),
        '-ProcessId', [string]$process.Id, '-OutputDirectory', (Quote-Argument $outputPath),
        '-DurationMinutes', [string]([Math]::Ceiling($DurationSeconds / 60) + 2), '-IntervalSeconds', '5'
    )
    $sampler = Start-Process -FilePath (Get-Command pwsh -ErrorAction Stop).Source `
        -ArgumentList $samplerArguments -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $outputPath 'sampler.stdout') `
        -RedirectStandardError (Join-Path $outputPath 'sampler.stderr')
    [pscustomobject]@{ launcherPid = $process.Id; samplerPid = $sampler.Id; executable = $executablePath } |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outputPath 'processes.json')
    Write-Output "Reader soak started: launcher=$($process.Id), sampler=$($sampler.Id), evidence=$outputPath"
    while (-not $process.WaitForExit(30000)) {
        $last = Get-Content -LiteralPath $env:MIHON_W_READER_SOAK_OUTPUT -Tail 1 -ErrorAction SilentlyContinue
        if ($last) { Write-Output $last }
    }
    $process.Refresh()
    if (-not $sampler.WaitForExit(15000)) { throw 'Memory sampler has not finished; inspect its recorded PID' }
    $sampler.Refresh()
    if ($process.ExitCode -ne 0) { throw "Reader verification exited $($process.ExitCode); inspect stderr.log" }
    if ($sampler.ExitCode -ne 0) { throw "Memory sampler exited $($sampler.ExitCode); inspect sampler.stderr" }
    if ((Get-Item (Join-Path $outputPath 'stderr.log')).Length -ne 0) { throw 'Reader emitted stderr; soak is not accepted' }
    $result = Get-Content -LiteralPath (Join-Path $outputPath 'stdout.json') -Raw | ConvertFrom-Json
    if ($result.status -ne 'SUCCEEDED' -or $result.soak.elapsedSeconds -lt $DurationSeconds -or
        $result.soak.cycles -lt 1 -or $result.soak.decodedTiles -lt 38) {
        throw 'Reader did not complete the requested active workload'
    }
    [pscustomobject]@{
        accepted = $true; exitCode = $process.ExitCode; samplerExitCode = $sampler.ExitCode
        soak = $result.soak
        scope = 'Headless decoding/session workload; excludes Compose rendering and UI frame times'
    } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $outputPath 'result.json')
    Get-Content -LiteralPath (Join-Path $outputPath 'result.json')
} finally {
    $env:MIHON_W_READER_VERIFY = $oldGate
    $env:MIHON_W_READER_SOAK_SECONDS = $oldDuration
    $env:MIHON_W_READER_SOAK_OUTPUT = $oldSamples
}
