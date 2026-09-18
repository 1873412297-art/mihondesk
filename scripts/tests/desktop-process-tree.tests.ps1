$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../desktop-process-tree.ps1')
$epoch = [DateTime]::Parse('2026-09-18T00:00:00Z').ToUniversalTime()
function Row([int]$Id, [int]$Parent, [DateTime]$Created) {
    [pscustomobject]@{ ProcessId = $Id; ParentProcessId = $Parent; CreationDate = $Created }
}
function Assert-Ids($Rows, [DateTime]$RootTime, [int[]]$Expected, [string]$Case) {
    $actual = @(Get-DesktopProcessTree -RootProcessId 100 -RootCreatedUtc $RootTime -Rows $Rows |
        ForEach-Object { [int]$_.ProcessId } | Sort-Object)
    if (($actual -join ',') -ne (($Expected | Sort-Object) -join ',')) {
        throw "$Case expected [$($Expected -join ',')] but got [$($actual -join ',')]"
    }
    Write-Output "PASS: $Case"
}

$root = Row 100 99 $epoch
$child = Row 200 100 $epoch.AddSeconds(20)
$staleChild = Row 300 200 $epoch.AddSeconds(10)
$staleGrandchild = Row 400 300 $epoch.AddSeconds(11)
Assert-Ids @($staleGrandchild, $staleChild, $child, $root) $epoch @(100, 200) 'Reused parent PID rejects older unrelated descendants'
Assert-Ids @((Row 300 200 $epoch.AddSeconds(21)), $child, $root) $epoch @(100, 200, 300) 'Out-of-order legitimate descendants remain included'
Assert-Ids @($root, $child) $epoch.AddSeconds(-1) @() 'Reused root PID ends tree ownership'
Assert-Ids @($root, $child) $epoch.AddTicks(5) @(100, 200) 'CIM microsecond precision preserves actual identity'
Assert-Ids @($root, $child) $epoch.AddTicks(10) @() 'Distinct creation microsecond rejects identity'
Assert-Ids @($root, (Row 500 777 $epoch), (Row 600 100 $epoch)) $epoch @(100, 600) 'Orphans excluded and equal-time children accepted'
Assert-Ids @($root, [pscustomobject]@{ProcessId=700;ParentProcessId=100;CreationDate=$null}) $epoch @(100) 'Missing creation identity is excluded'
