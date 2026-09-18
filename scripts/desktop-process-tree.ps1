function Test-DesktopProcessIdentity {
    param([DateTime]$ExpectedUtc, [DateTime]$ActualUtc)
    # Win32_Process CreationDate retains microseconds; Get-Process retains 100 ns.
    $expectedTicks = $ExpectedUtc.ToUniversalTime().Ticks
    $actualTicks = $ActualUtc.ToUniversalTime().Ticks
    ($expectedTicks - ($expectedTicks % 10)) -eq ($actualTicks - ($actualTicks % 10))
}

function Get-DesktopProcessTree {
    param([int]$RootProcessId, [DateTime]$RootCreatedUtc, [object[]]$Rows)
    $byId = @{}
    foreach ($row in $Rows) {
        if ($null -eq $row.CreationDate) { continue }
        $rowId = [int]$row.ProcessId
        if ($byId.ContainsKey($rowId)) { throw "Duplicate process identity in snapshot: $rowId" }
        $byId[$rowId] = $row
    }
    $root = $byId[$RootProcessId]
    if (-not $root -or -not (Test-DesktopProcessIdentity $RootCreatedUtc $root.CreationDate)) { return }
    $treeIds = [Collections.Generic.HashSet[int]]::new()
    $null = $treeIds.Add($RootProcessId)
    do {
        $added = $false
        foreach ($row in $byId.Values) {
            $parentId = [int]$row.ParentProcessId
            if ($treeIds.Contains($parentId) -and
                $row.CreationDate.ToUniversalTime() -ge $byId[$parentId].CreationDate.ToUniversalTime() -and
                $treeIds.Add([int]$row.ProcessId)) {
                $added = $true
            }
        }
    } while ($added)
    $byId.Values | Where-Object { $treeIds.Contains([int]$_.ProcessId) }
}
