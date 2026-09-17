[CmdletBinding()]
param([switch]$KeepArtifacts)
$ErrorActionPreference = 'Stop'
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$root = Join-Path $repository ('build/portable-updater-tests-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root | Out-Null
$updater = Join-Path $repository 'scripts/mihondesk-updater.ps1'
$results = [Collections.Generic.List[object]]::new()
$compiler = Join-Path $env:WINDIR 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
$source = Join-Path $root 'Probe.cs'
$probe = Join-Path $root 'mihondesk.exe'
@'
using System;
using System.IO;
using System.Diagnostics;
using System.Threading;
public class Probe {
    public static int Main(string[] args) {
        string root = AppDomain.CurrentDomain.BaseDirectory;
        if (args.Length > 0 && args[0] == "--hold") {
            using (var profile = new FileStream(Path.Combine(root, "data", ".mihon-profile.lock"), FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.ReadWrite)) {
                profile.Lock(0, Int64.MaxValue);
                File.WriteAllText(Path.Combine(root, "caller-ready"), "ready");
                while (!File.Exists(Path.Combine(root, "shutdown-code"))) Thread.Sleep(50);
                return Int32.Parse(File.ReadAllText(Path.Combine(root, "shutdown-code")));
            }
        }
        if (args.Length == 0) File.WriteAllText(Path.Combine(root, "restarted"), "yes");
        string token = Environment.GetEnvironmentVariable("MIHON_PORTABLE_UPDATE_TOKEN");
        string guard = Path.Combine(root, ".mihon-update-in-progress");
        if (File.Exists(guard) && File.ReadAllText(guard).Trim() != token) {
            Console.WriteLine("{\"category\":\"PORTABLE_UPDATE_PENDING\"}");
            return 75;
        }
        if (!String.IsNullOrEmpty(token)) {
            string mode = Path.Combine(root, "probe-mode.txt");
            string value = File.Exists(mode) ? File.ReadAllText(mode).Trim() : "";
            if (value == "fail") return 7;
            if (value == "wait") {
                File.WriteAllText(Path.Combine(root, "probe-pid.txt"), Process.GetCurrentProcess().Id.ToString());
                Thread.Sleep(Timeout.Infinite);
            }
        }
        Console.WriteLine("mihondesk 0.2.18 (Windows x64)");
        return 0;
    }
}
'@ | Set-Content -LiteralPath $source -Encoding UTF8
& $compiler /nologo /target:exe "/out:$probe" $source
if ($LASTEXITCODE -ne 0) { throw 'Probe compilation failed' }

function New-Fixture([string]$Name, [string]$Mode = '') {
    $directory = Join-Path $root $Name
    $target = Join-Path $directory ("installed " + [char]0x4e2d + [char]0x6587 + "'s app")
    $payload = Join-Path $directory 'payload/mihondesk'
    New-Item -ItemType Directory -Path $target,$payload,(Join-Path $target 'data/nested') -Force | Out-Null
    [IO.File]::WriteAllText((Join-Path $target '.portable'), '')
    [IO.File]::WriteAllText((Join-Path $target 'MihonW.exe'), 'original application')
    [IO.File]::WriteAllText((Join-Path $target 'data/nested/keep.txt'), 'private profile')
    [IO.File]::WriteAllText((Join-Path $target 'data/.mihon-profile.lock'), '')
    Copy-Item -LiteralPath $probe -Destination $payload
    if ($Mode) { [IO.File]::WriteAllText((Join-Path $payload 'probe-mode.txt'), $Mode) }
    $zip = Join-Path $directory 'update.zip'
    Compress-Archive -LiteralPath $payload -DestinationPath $zip
    return @{ Target = $target; Payload = $payload; Zip = $zip; Hash = (Get-FileHash -LiteralPath $zip).Hash; Directory = $directory }
}
function Assert-Original($Fixture) {
    if ([IO.File]::ReadAllText((Join-Path $Fixture.Target 'MihonW.exe')) -ne 'original application') { throw 'Original executable changed' }
    if ([IO.File]::ReadAllText((Join-Path $Fixture.Target 'data/nested/keep.txt')) -ne 'private profile') { throw 'Profile content changed' }
}
function Assert-Fails([scriptblock]$Action, [string]$Pattern) {
    $caught = $null
    try { & $Action | Out-Null } catch { $caught = $_.Exception.Message }
    if (-not $caught -or $caught -notmatch $Pattern) { throw "Expected failure /$Pattern/, received: $caught" }
}
function Test-Case([string]$Name, [scriptblock]$Action) {
    try { & $Action; $results.Add(@{ name = $Name; status = 'PASS' }); Write-Output "PASS: $Name" }
    catch { $results.Add(@{ name = $Name; status = 'FAIL'; error = $_.Exception.Message }); Write-Output "FAIL: $Name : $($_.Exception.Message)" }
}
function Repack-Fixture($Fixture) {
    Remove-Item -LiteralPath $Fixture.Zip
    Compress-Archive -LiteralPath $Fixture.Payload -DestinationPath $Fixture.Zip
    $Fixture.Hash = (Get-FileHash -LiteralPath $Fixture.Zip).Hash
}
try {
    foreach ($scenario in @('success', 'cancel', 'bad-identity', 'failed-exit', 'rollback', 'timeout', 'invalid-commit')) {
    Test-Case "application handoff $scenario" {
        $fixture = New-Fixture "handoff-$scenario" $(if ($scenario -eq 'rollback') { 'fail' } else { '' })
        Copy-Item -LiteralPath $probe -Destination (Join-Path $fixture.Target 'mihondesk.exe')
        $caller = Start-Process -FilePath (Join-Path $fixture.Target 'mihondesk.exe') -ArgumentList '--hold' -PassThru -WindowStyle Hidden
        $runner = $null
        try {
            $deadline = [DateTime]::UtcNow.AddSeconds(10)
            while (-not (Test-Path -LiteralPath (Join-Path $fixture.Target 'caller-ready'))) {
                if ($caller.HasExited -or [DateTime]::UtcNow -gt $deadline) { throw 'Caller did not acquire the profile' }
                Start-Sleep -Milliseconds 50
            }
            $token = [Guid]::NewGuid().ToString('N')
            $handoff = Join-Path $fixture.Directory ".mihon-handoff-$token"
            New-Item -ItemType Directory -Path $handoff | Out-Null
            $started = ([DateTimeOffset]::new($caller.StartTime.ToUniversalTime())).ToUnixTimeMilliseconds()
            if ($scenario -eq 'bad-identity') { $started-- }
            $command = "& '{0}' -ZipPath '{1}' -TargetDir '{2}' -ExpectedSha256 '{3}' -CallerPid {4} -CallerStartMillis {5} -HandoffToken '{6}'" -f $updater.Replace("'", "''"), $fixture.Zip.Replace("'", "''"), $fixture.Target.Replace("'", "''"), $fixture.Hash, $caller.Id, $started, $token
            $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($command))
            $runner = Start-Process -FilePath (Get-Process -Id $PID).Path -ArgumentList @('-NoProfile', '-EncodedCommand', $encoded) -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $handoff 'stdout') -RedirectStandardError (Join-Path $handoff 'stderr')
            if ($scenario -ne 'bad-identity') {
                $deadline = [DateTime]::UtcNow.AddSeconds(20)
                while (-not (Test-Path -LiteralPath (Join-Path $handoff 'ready'))) {
                    if ($runner.HasExited -or [DateTime]::UtcNow -gt $deadline) { throw "Updater did not prepare: $([IO.File]::ReadAllText((Join-Path $handoff 'stderr')))" }
                    Start-Sleep -Milliseconds 50
                }
                if ($caller.HasExited) { throw 'Caller exited before readiness' }
                Assert-Original $fixture
                if ($scenario -eq 'cancel') { [IO.File]::WriteAllText((Join-Path $handoff 'abort'), $token) }
                elseif ($scenario -eq 'invalid-commit') { [IO.File]::WriteAllText((Join-Path $handoff 'commit'), 'invalid') }
                elseif ($scenario -ne 'timeout') {
                    [IO.File]::WriteAllText((Join-Path $handoff 'commit'), $token)
                    [IO.File]::WriteAllText((Join-Path $fixture.Target 'shutdown-code'), $(if ($scenario -eq 'failed-exit') { '7' } else { '0' }))
                }
            }
            if (-not $runner.WaitForExit($(if ($scenario -eq 'timeout') { 130000 } else { 20000 }))) { throw 'Handoff did not terminate' }
            $expected = if ($scenario -eq 'success') { 'updated' } else { 'failed' }
            if ([IO.File]::ReadAllText((Join-Path $handoff 'result')) -ne $expected) { throw 'Wrong handoff result' }
            if ($scenario -ne 'success') { Assert-Original $fixture }
            if ($scenario -in @('cancel', 'bad-identity', 'timeout', 'invalid-commit') -and $caller.HasExited) { throw 'Preparation failure closed caller' }
            if ($scenario -in @('success', 'rollback', 'failed-exit')) {
                $deadline = [DateTime]::UtcNow.AddSeconds(5)
                while (-not (Test-Path -LiteralPath (Join-Path $fixture.Target 'restarted'))) {
                    if ([DateTime]::UtcNow -gt $deadline) { throw 'Available application did not restart' }
                    Start-Sleep -Milliseconds 50
                }
            }
            if (@(Get-ChildItem -LiteralPath $fixture.Directory -Force -Filter '.mihon-stage-*').Count -ne 0) { throw 'Handoff stage was not cleaned' }
        } finally {
            if ($runner -and -not $runner.HasExited) { $runner.Kill(); $runner.WaitForExit(5000) | Out-Null }
            if (-not $caller.HasExited) { $caller.Kill(); $caller.WaitForExit(5000) | Out-Null }
            $caller.Dispose()
            if ($runner) { $runner.Dispose() }
        }
    }
    }
    Test-Case 'checksum rejection preserves original' {
        $fixture = New-Fixture 'checksum'
        Assert-Fails { & $updater -ZipPath $fixture.Zip -TargetDir $fixture.Target -ExpectedSha256 ('0' * 64) -NoRestart } 'SHA-256'
        Assert-Original $fixture
    }
    Test-Case 'busy profile rejects update before replacement' {
        $fixture = New-Fixture 'busy'
        $lock = [IO.FileStream]::new((Join-Path $fixture.Target 'data/.mihon-profile.lock'), [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete)
        $lock.Lock(0, [long]::MaxValue)
        try {
            Assert-Fails { & $updater -ZipPath $fixture.Zip -TargetDir $fixture.Target -ExpectedSha256 $fixture.Hash -NoRestart } 'profile.*in use'
            Assert-Original $fixture
        } finally { $lock.Dispose() }
    }
    Test-Case 'successful replacement preserves complete old installation and data' {
        $fixture = New-Fixture 'success'
        & $updater -ZipPath $fixture.Zip -TargetDir $fixture.Target -ExpectedSha256 $fixture.Hash -NoRestart
        if ((Get-FileHash -LiteralPath (Join-Path $fixture.Target 'mihondesk.exe')).Hash -ne (Get-FileHash -LiteralPath $probe).Hash) { throw 'New executable not installed' }
        if ([IO.File]::ReadAllText((Join-Path $fixture.Target 'data/nested/keep.txt')) -ne 'private profile') { throw 'New installation lost profile' }
        $backups = @(Get-ChildItem -LiteralPath $fixture.Directory -Directory -Force -Filter '.mihon-rollback-*')
        if ($backups.Count -ne 1) { throw 'Missing full rollback directory' }
        Assert-Original @{ Target = $backups[0].FullName }
        if (@(Get-ChildItem -LiteralPath $fixture.Directory -Force -Filter '.mihon-update-*.json').Count -ne 0) { throw 'Committed journal not removed' }
        if (Test-Path -LiteralPath (Join-Path $fixture.Target '.mihon-update-in-progress')) { throw 'Committed update is still guarded' }
    }
    Test-Case 'post-swap validation failure restores old data and retains failed copy' {
        $fixture = New-Fixture 'failed-probe' 'fail'
        Assert-Fails { & $updater -ZipPath $fixture.Zip -TargetDir $fixture.Target -ExpectedSha256 $fixture.Hash -NoRestart } 'failed validation'
        Assert-Original $fixture
        $failed = @(Get-ChildItem -LiteralPath $fixture.Directory -Directory -Force -Filter '.mihon-failed-*')
        if ($failed.Count -ne 1 -or -not (Test-Path -LiteralPath (Join-Path $failed[0].FullName 'data/nested/keep.txt'))) { throw 'Failed copy was not retained' }
        if (Test-Path -LiteralPath (Join-Path $fixture.Target '.mihon-update-in-progress')) { throw 'Restored application remains guarded' }
    }
    Test-Case 'archive cannot supply user data' {
        $fixture = New-Fixture 'bundled-data'
        New-Item -ItemType Directory -Path (Join-Path $fixture.Payload 'data') | Out-Null
        [IO.File]::WriteAllText((Join-Path $fixture.Payload 'data/private.txt'), 'bundled')
        Repack-Fixture $fixture
        Assert-Fails { & $updater -ZipPath $fixture.Zip -TargetDir $fixture.Target -ExpectedSha256 $fixture.Hash -NoRestart } 'must not supply user data'
        Assert-Original $fixture
    }
    Test-Case 'archive traversal is rejected before extraction' {
        $fixture = New-Fixture 'traversal'
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $archive = [IO.Compression.ZipFile]::Open($fixture.Zip, [IO.Compression.ZipArchiveMode]::Update)
        try {
            $entry = $archive.CreateEntry('mihondesk/../../escaped.txt')
            $writer = [IO.StreamWriter]::new($entry.Open())
            try { $writer.Write('escaped') } finally { $writer.Dispose() }
        } finally { $archive.Dispose() }
        $fixture.Hash = (Get-FileHash -LiteralPath $fixture.Zip).Hash
        Assert-Fails { & $updater -ZipPath $fixture.Zip -TargetDir $fixture.Target -ExpectedSha256 $fixture.Hash -NoRestart } 'Unsafe update archive path'
        if (Test-Path -LiteralPath (Join-Path $fixture.Directory 'escaped.txt')) { throw 'Archive escaped staging' }
        Assert-Original $fixture
    }
    Test-Case 'profile junction is rejected without following or changing its target' {
        $fixture = New-Fixture 'junction'
        $outside = Join-Path $fixture.Directory 'outside-profile'
        New-Item -ItemType Directory -Path $outside | Out-Null
        [IO.File]::WriteAllText((Join-Path $outside 'sentinel'), 'keep')
        $junction = Join-Path $fixture.Target 'data/linked'
        New-Item -ItemType Junction -Path $junction -Target $outside | Out-Null
        try {
            Assert-Fails { & $updater -ZipPath $fixture.Zip -TargetDir $fixture.Target -ExpectedSha256 $fixture.Hash -NoRestart } 'Reparse points'
            Assert-Original $fixture
            if ([IO.File]::ReadAllText((Join-Path $outside 'sentinel')) -ne 'keep') { throw 'Junction target changed' }
        } finally { [IO.Directory]::Delete($junction, $false) }
    }
    foreach ($interruptionPhase in @('after-swap', 'between-renames')) {
    Test-Case "terminated updater recovers $interruptionPhase using its durable journal" {
        $fixture = New-Fixture "interrupted-$interruptionPhase" 'wait'
        $command = "& '" + $updater.Replace("'", "''") + "' -ZipPath '" + $fixture.Zip.Replace("'", "''") + "' -TargetDir '" + $fixture.Target.Replace("'", "''") + "' -ExpectedSha256 '" + $fixture.Hash + "' -NoRestart"
        $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($command))
        $shell = (Get-Process -Id $PID).Path
        $runner = Start-Process -FilePath $shell -ArgumentList @('-NoProfile', '-EncodedCommand', $encoded) -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $fixture.Directory 'updater.stdout') -RedirectStandardError (Join-Path $fixture.Directory 'updater.stderr')
        $child = $null
        try {
            $pidFile = Join-Path $fixture.Target 'probe-pid.txt'
            $deadline = [DateTime]::UtcNow.AddSeconds(20)
            while (-not (Test-Path -LiteralPath $pidFile) -and -not $runner.HasExited -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
            if (-not (Test-Path -LiteralPath $pidFile)) { throw 'Post-swap validation probe did not start' }
            $child = Get-Process -Id ([int][IO.File]::ReadAllText($pidFile))
            if ($child.Path -ne (Join-Path $fixture.Target 'mihondesk.exe')) { throw 'Unexpected probe process path' }
            $journals = @(Get-ChildItem -LiteralPath $fixture.Directory -Force -Filter '.mihon-update-*.json')
            if ($journals.Count -ne 1) { throw 'No durable recovery journal before validation' }
            Assert-Fails { & $updater -TargetDir $fixture.Target -Recover } 'Another portable update'
            $runner.Kill()
            if (-not $runner.WaitForExit(5000)) { throw 'Owned updater did not stop' }
            $child.Kill()
            if (-not $child.WaitForExit(5000)) { throw 'Owned probe did not stop' }
            $savedJournal = [IO.File]::ReadAllBytes($journals[0].FullName)
            $journal = [Text.Encoding]::UTF8.GetString($savedJournal) | ConvertFrom-Json
            if ($interruptionPhase -eq 'between-renames') {
                $candidate = Join-Path $fixture.Directory ".mihon-stage-$($journal.id)/mihondesk"
                foreach ($owned in @($candidate, $fixture.Target)) {
                    if (-not [IO.Path]::GetFullPath($owned).StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe fixture move' }
                }
                # Recreate the precise on-disk state before the second rename using
                # paths/metadata emitted by a real interrupted updater invocation.
                [IO.Directory]::Move($fixture.Target, $candidate)
            }
            $journal.id = '../not-an-owned-operation'
            [IO.File]::WriteAllText($journals[0].FullName, ($journal | ConvertTo-Json), [Text.Encoding]::UTF8)
            Assert-Fails { & $updater -TargetDir $fixture.Target -Recover } 'Invalid update recovery operation ID'
            [IO.File]::WriteAllBytes($journals[0].FullName, $savedJournal)
            & $updater -TargetDir $fixture.Target -Recover
            Assert-Original $fixture
            if (Test-Path -LiteralPath $journals[0].FullName) { throw 'Recovered journal was not cleared' }
            & $updater -TargetDir $fixture.Target -Recover
            Assert-Original $fixture
        } finally {
            if (-not $runner.HasExited) { $runner.Kill(); $runner.WaitForExit(5000) | Out-Null }
            if ($child -and -not $child.HasExited) { $child.Kill(); $child.WaitForExit(5000) | Out-Null }
            $runner.Dispose()
            if ($child) { $child.Dispose() }
        }
    }
    }
} finally {
    $results | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $root 'results.json') -Encoding UTF8
    Write-Output "Evidence: $root"
    if (-not $KeepArtifacts) {
        $resolved = [IO.Path]::GetFullPath($root)
        $expectedParent = [IO.Path]::GetFullPath((Join-Path $repository 'build'))
        if ([IO.Directory]::GetParent($resolved).FullName -ne $expectedParent -or [IO.Path]::GetFileName($resolved) -notmatch '^portable-updater-tests-[a-f0-9]{32}$') { throw 'Unsafe test cleanup' }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
if ($results.Where({ $_.status -eq 'FAIL' }).Count -gt 0) { throw 'Portable updater regressions failed' }
