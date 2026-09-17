[CmdletBinding(DefaultParameterSetName = 'Update')]
param(
    [int]$CallerPid = 0,
    [Parameter(Mandatory, ParameterSetName = 'Update')][string]$ZipPath,
    [Parameter(Mandatory)][string]$TargetDir,
    [Parameter(Mandatory, ParameterSetName = 'Update')][ValidatePattern('^[a-fA-F0-9]{64}$')][string]$ExpectedSha256,
    [Parameter(Mandatory, ParameterSetName = 'Recover')][switch]$Recover,
    [string]$ExecutableName = 'mihondesk.exe',
    [switch]$NoRestart
)
$ErrorActionPreference = 'Stop'
if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) { throw 'This updater requires Windows' }
if ($ExecutableName -notmatch '^[a-zA-Z0-9._-]+\.exe$' -or $ExecutableName -ne [IO.Path]::GetFileName($ExecutableName)) { throw 'ExecutableName must be an EXE filename' }
$targetResolved = [IO.Path]::GetFullPath($TargetDir).TrimEnd('\', '/')
$targetParent = [IO.Directory]::GetParent($targetResolved)
if (-not $targetParent -or -not $targetParent.Exists) { throw 'Update target must have an existing parent directory' }
$targetParent = $targetParent.FullName
$guardName = '.mihon-update-in-progress'
$hashAlgorithm = [Security.Cryptography.SHA256]::Create()
try { $targetKey = ([BitConverter]::ToString($hashAlgorithm.ComputeHash([Text.Encoding]::UTF8.GetBytes($targetResolved.ToUpperInvariant())))).Replace('-', '').Substring(0, 24) }
finally { $hashAlgorithm.Dispose() }
$journalPath = Join-Path $targetParent ".mihon-update-$targetKey.json"
$coordinatorPath = Join-Path $targetParent ".mihon-update-$targetKey.lock"

function Assert-NoReparse([string]$Path) {
    $current = [IO.Path]::GetFullPath($Path)
    while ($current) {
        if (Test-Path -LiteralPath $current) {
            if ((Get-Item -LiteralPath $current -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Reparse points are not supported by the updater: $current" }
        }
        $parent = [IO.Directory]::GetParent($current)
        $current = if ($parent) { $parent.FullName } else { $null }
    }
}
function Assert-SafeTree([string]$Directory) {
    Assert-NoReparse $Directory
    $pending = [Collections.Generic.Queue[string]]::new()
    $pending.Enqueue($Directory)
    while ($pending.Count -gt 0) {
        foreach ($entry in Get-ChildItem -LiteralPath $pending.Dequeue() -Force) {
            if ($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Reparse points are not supported by the updater: $($entry.FullName)" }
            if ($entry.PSIsContainer) { $pending.Enqueue($entry.FullName) }
        }
    }
}
function Get-OperationPaths([string]$Id) {
    if ($Id -notmatch '^[a-f0-9]{32}$') { throw 'Invalid update recovery operation ID' }
    return @{
        Stage = Join-Path $targetParent ".mihon-stage-$Id"
        Backup = Join-Path $targetParent ".mihon-rollback-$Id"
        Failed = Join-Path $targetParent ".mihon-failed-$Id"
    }
}
function Remove-OwnedStage([string]$Id) {
    $path = (Get-OperationPaths $Id).Stage
    if ([IO.Directory]::GetParent([IO.Path]::GetFullPath($path)).FullName -ne $targetParent) { throw 'Unsafe stage cleanup path' }
    if (Test-Path -LiteralPath $path) {
        Assert-SafeTree $path
        Remove-Item -LiteralPath $path -Recurse -Force
    }
}
function Write-Journal($Journal) {
    $temporary = "$journalPath.$([Guid]::NewGuid().ToString('N')).tmp"
    try {
        $bytes = [Text.Encoding]::UTF8.GetBytes(($Journal | ConvertTo-Json -Compress))
        $stream = [IO.FileStream]::new($temporary, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
        try { $stream.Write($bytes, 0, $bytes.Length); $stream.Flush($true) } finally { $stream.Dispose() }
        if (Test-Path -LiteralPath $journalPath) { [IO.File]::Replace($temporary, $journalPath, [NullString]::Value) }
        else { [IO.File]::Move($temporary, $journalPath) }
    } finally { if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force } }
}
function Acquire-ProfileLock([string]$ApplicationDirectory) {
    $data = Join-Path $ApplicationDirectory 'data'
    Assert-NoReparse $data
    if (-not (Test-Path -LiteralPath $data)) { New-Item -ItemType Directory -Path $data | Out-Null }
    $path = Join-Path $data '.mihon-profile.lock'
    Assert-NoReparse $path
    $stream = $null
    try {
        $stream = [IO.FileStream]::new($path, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, ([IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete))
        # Same range as FileChannel.tryLock() in DesktopProfileLock; allow directory rename.
        $stream.Lock(0, [long]::MaxValue)
        return $stream
    } catch {
        if ($stream) { $stream.Dispose() }
        throw "Portable profile is in use or cannot be locked; update aborted. $($_.Exception.Message)"
    }
}
function Remove-Guard([string]$Directory, [string]$Id) {
    $path = Join-Path $Directory $guardName
    if (Test-Path -LiteralPath $path) {
        Assert-NoReparse $path
        if ([IO.File]::ReadAllText($path).Trim() -ne $Id) { throw 'Unexpected update guard; recovery files retained' }
        Remove-Item -LiteralPath $path -Force
    }
}
function Recover-PendingUpdate {
    Assert-NoReparse $journalPath
    $journal = Get-Content -LiteralPath $journalPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($journal.schema -ne 1 -or $journal.target -ine $targetResolved -or $journal.phase -notin @('prepared', 'validated')) { throw 'Invalid update recovery journal; no directories were changed' }
    $paths = Get-OperationPaths $journal.id
    foreach ($path in @($targetResolved, $paths.Stage, $paths.Backup, $paths.Failed)) { Assert-NoReparse $path }
    if ($journal.phase -eq 'prepared' -and (Test-Path -LiteralPath $paths.Backup)) {
        foreach ($path in @($paths.Backup, $targetResolved)) {
            if (Test-Path -LiteralPath $path) {
                $guard = Join-Path $path $guardName
                Assert-NoReparse $guard
                if (-not (Test-Path -LiteralPath $guard -PathType Leaf) -or [IO.File]::ReadAllText($guard).Trim() -ne $journal.id) { throw 'Recovery directory does not match the pending operation; no directories were changed' }
            }
        }
    }
    $heldLocks = [Collections.Generic.List[IDisposable]]::new()
    try {
        foreach ($path in @($targetResolved, $paths.Backup)) {
            if (Test-Path -LiteralPath $path) {
                if (-not (Test-Path -LiteralPath (Join-Path $path '.portable') -PathType Leaf)) { throw 'Recovery directory is not a portable installation' }
                $heldLocks.Add((Acquire-ProfileLock $path))
            }
        }
        # Windows cannot rename a directory containing open child handles, even with
        # FILE_SHARE_DELETE. Guards prevent new application starts during the renames.
        foreach ($held in $heldLocks) { $held.Dispose() }
        $heldLocks.Clear()
        if ($journal.phase -eq 'validated') {
            if (-not (Test-Path -LiteralPath $targetResolved -PathType Container)) { throw 'Validated installation is missing; recovery files retained' }
            Remove-Guard $targetResolved $journal.id
            Remove-Guard $paths.Backup $journal.id
        } else {
            if (Test-Path -LiteralPath $paths.Backup) {
                if (Test-Path -LiteralPath $targetResolved) {
                    if (Test-Path -LiteralPath $paths.Failed) { throw 'Failed-installation destination already exists; recovery files retained' }
                    # Preserve even the failed copy in case it contains writes made after the swap.
                    [IO.Directory]::Move($targetResolved, $paths.Failed)
                }
                [IO.Directory]::Move($paths.Backup, $targetResolved)
            } elseif (-not (Test-Path -LiteralPath $targetResolved -PathType Container)) {
                throw 'Both original and rollback installations are missing; recovery files retained'
            }
            Remove-Guard $targetResolved $journal.id
        }
        Remove-Item -LiteralPath $journalPath -Force
        Remove-OwnedStage $journal.id
        Write-Output "Recovered portable update ($($journal.phase)). Installation: $targetResolved"
    } finally { foreach ($held in $heldLocks) { $held.Dispose() } }
}
function Expand-ValidatedArchive([string]$Archive, [string]$Stage) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $packageRoot = [IO.Path]::GetFileNameWithoutExtension($ExecutableName)
    $zip = [IO.Compression.ZipFile]::OpenRead($Archive)
    try {
        $names = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
        foreach ($entry in $zip.Entries) {
            $name = $entry.FullName.Replace('\', '/').TrimEnd('/')
            $parts = $name.Split('/')
            if (-not $name -or $parts[0] -cne $packageRoot -or -not $names.Add($name)) { throw 'Unsafe or duplicate update archive path' }
            foreach ($part in $parts) {
                if (-not $part -or $part -in @('.', '..') -or $part -match '[:<>"|?*\x00-\x1f]' -or $part -match '[. ]$' -or $part -match '^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\.|$)') { throw 'Unsafe update archive path' }
            }
            if ($parts.Count -gt 1 -and $parts[1] -in @('data', $guardName)) { throw 'Update archive must not supply user data or update state' }
            $unixType = ($entry.ExternalAttributes -shr 16) -band 0xF000
            if ($unixType -eq 0xA000 -or ($entry.ExternalAttributes -band 0x400)) { throw 'Update archive must not supply links' }
        }
    } finally { $zip.Dispose() }
    [IO.Compression.ZipFile]::ExtractToDirectory($Archive, $Stage)
    $candidate = Join-Path $Stage $packageRoot
    if (-not (Test-Path -LiteralPath (Join-Path $candidate $ExecutableName) -PathType Leaf)) { throw 'Update executable missing' }
    Assert-SafeTree $candidate
    return $candidate
}
function Invoke-Validation([string]$Directory, [string]$DataDirectory, [string]$Token = '', [int]$ExpectedExit = 0) {
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = Join-Path $Directory $ExecutableName
    $info.Arguments = '--version --data-dir="' + $DataDirectory + '"'
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.EnvironmentVariables['MIHON_PORTABLE_UPDATE_TOKEN'] = $Token
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $info
    try {
        if (-not $process.Start()) { throw 'Cannot launch update validation' }
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit(30000)) {
            $process.Kill()
            if (-not $process.WaitForExit(5000)) { throw 'Updated executable did not terminate; recovery files retained' }
            throw 'Updated executable validation timed out'
        }
        if ($process.ExitCode -ne $ExpectedExit) { throw "Updated executable failed validation (exit $($process.ExitCode), expected $ExpectedExit): $($stderr.GetAwaiter().GetResult())" }
        if ($ExpectedExit -eq 75) {
            $response = $stdout.GetAwaiter().GetResult() | ConvertFrom-Json
            if ($response.category -ne 'PORTABLE_UPDATE_PENDING') { throw 'Update package does not support guarded portable updates' }
        }
    } finally { $process.Dispose() }
}

Assert-NoReparse $targetResolved
Assert-NoReparse $coordinatorPath
$coordinator = $null
$profileLock = $null
$operationId = $null
$restart = $false
try {
    try { $coordinator = [IO.FileStream]::new($coordinatorPath, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None) }
    catch { throw 'Another portable update is in progress' }
    if (Test-Path -LiteralPath $journalPath) { Recover-PendingUpdate }
    if ($Recover) { return }
    if (-not (Test-Path -LiteralPath (Join-Path $targetResolved '.portable') -PathType Leaf)) { throw 'Update target must be an existing portable application directory' }
    if (Test-Path -LiteralPath (Join-Path $targetResolved $guardName)) { throw 'Update guard has no recovery journal; preserve this directory and investigate' }
    if ($CallerPid -gt 0) {
        $caller = Get-Process -Id $CallerPid -ErrorAction SilentlyContinue
        if ($caller) {
            if ([IO.Path]::GetDirectoryName($caller.MainModule.FileName) -ne $targetResolved) { throw 'Caller PID does not belong to the portable installation' }
            if (-not $caller.WaitForExit(30000)) { throw 'Application is still running; update aborted' }
        }
    }
    $profileLock = Acquire-ProfileLock $targetResolved
    Assert-SafeTree (Join-Path $targetResolved 'data')
    $operationId = [Guid]::NewGuid().ToString('N')
    $paths = Get-OperationPaths $operationId
    New-Item -ItemType Directory -Path $paths.Stage | Out-Null
    # Copy under a non-writable/non-deletable read handle, then verify exactly what will be extracted.
    $stagedZip = Join-Path $paths.Stage 'update.zip'
    $archive = [IO.FileStream]::new((Resolve-Path -LiteralPath $ZipPath).Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::Read)
    try {
        $copy = [IO.File]::Create($stagedZip)
        try { $archive.CopyTo($copy); $copy.Flush($true) } finally { $copy.Dispose() }
    } finally { $archive.Dispose() }
    if ((Get-FileHash -LiteralPath $stagedZip -Algorithm SHA256).Hash -ne $ExpectedSha256) { throw 'Update SHA-256 mismatch' }
    $candidate = Expand-ValidatedArchive $stagedZip $paths.Stage
    [IO.File]::WriteAllText((Join-Path $candidate '.portable'), '')
    Invoke-Validation $candidate (Join-Path $paths.Stage 'probe-data')
    [IO.File]::WriteAllText((Join-Path $candidate $guardName), $operationId)
    # Do not install a package that would ignore the pending-update startup barrier.
    Invoke-Validation $candidate (Join-Path $paths.Stage 'probe-data') '' 75
    $candidateData = Join-Path $candidate 'data'
    New-Item -ItemType Directory -Path $candidateData | Out-Null
    # The lock is metadata, not profile content; copying its locked byte range would fail.
    foreach ($entry in Get-ChildItem -LiteralPath (Join-Path $targetResolved 'data') -Force) {
        if ($entry.Name -ne '.mihon-profile.lock') { Copy-Item -LiteralPath $entry.FullName -Destination $candidateData -Recurse -Force }
    }
    $journal = @{ schema = 1; target = $targetResolved; id = $operationId; phase = 'prepared' }
    Write-Journal $journal
    [IO.File]::WriteAllText((Join-Path $targetResolved $guardName), $operationId)
    [IO.File]::WriteAllText((Join-Path $candidate $guardName), $operationId)
    $profileLock.Dispose()
    $profileLock = $null
    # Each rename is atomic on this volume; the durable journal bridges the gap between them.
    [IO.Directory]::Move($targetResolved, $paths.Backup)
    [IO.Directory]::Move($candidate, $targetResolved)
    Invoke-Validation $targetResolved (Join-Path $targetResolved 'data') $operationId
    $journal.phase = 'validated'
    Write-Journal $journal
    Remove-Guard $targetResolved $operationId
    Remove-Guard $paths.Backup $operationId
    Remove-Item -LiteralPath $journalPath -Force
    Write-Output "Update installed. Previous application and data retained at $($paths.Backup)"
    $restart = -not $NoRestart
} catch {
    $failure = $_
    if ($profileLock) { $profileLock.Dispose(); $profileLock = $null }
    if ($coordinator -and (Test-Path -LiteralPath $journalPath)) {
        try { Recover-PendingUpdate }
        catch { Write-Warning "Recovery remains pending at $journalPath : $($_.Exception.Message)" }
    }
    throw $failure
} finally {
    if ($profileLock) { $profileLock.Dispose() }
    try {
        if ($operationId -and -not (Test-Path -LiteralPath $journalPath)) { Remove-OwnedStage $operationId }
    } finally { if ($coordinator) { $coordinator.Dispose() } }
}
if ($restart) { Start-Process -FilePath (Join-Path $targetResolved $ExecutableName) -WindowStyle Hidden }
