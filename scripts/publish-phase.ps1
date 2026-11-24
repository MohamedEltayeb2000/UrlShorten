# Publishes a completed phase. Usage: scripts/publish-phase.ps1 -Phase N [-DryRun]
#  -DryRun: verify recorded gate results and report what would happen; commits/pushes nothing.
param([Parameter(Mandatory = $true)][int]$Phase, [switch]$DryRun, [switch]$SkipDependencyCheck)
. "$PSScriptRoot/lib.ps1"
$root = Get-Root
function Stop-Publish([string]$reason) { Write-Host "REFUSED: $reason"; exit 1 }

if (-not $DryRun) {
    $ga = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $PSScriptRoot 'gate-all.ps1'), '-Phase', $Phase)
    if ($SkipDependencyCheck) { $ga += '-SkipDependencyCheck' }
    & powershell @ga | Out-Host
    if ($LASTEXITCODE -ne 0) { Stop-Publish "gates are not all green for phase $Phase" }
}

# Verify recorded gate results
$resultFile = Join-Path $root "docs/gates/phase-$Phase.json"
if (-not (Test-Path $resultFile)) { Stop-Publish "no gate results recorded for phase $Phase (run gate-all first)" }
$res = Get-Content $resultFile -Raw | ConvertFrom-Json
$required = @('TESTS', 'SECURITY', 'REVIEW', 'PERF', 'EVAL')
foreach ($g in $required) {
    $entry = $res.gates.$g
    if (-not $entry) { Stop-Publish "gate $g has no result for phase $Phase" }
    if ($entry.outcome -ne 'PASS' -and $entry.outcome -ne 'SKIPPED') { Stop-Publish "gate $g is $($entry.outcome)" }
}

# Publish target
$cfgFile = Join-Path $root 'docs/publish-config.json'
$cfg = $null
if (Test-Path $cfgFile) { $cfg = Get-Content $cfgFile -Raw | ConvertFrom-Json }
$hasRemote = $false
Push-Location $root
$remotes = (& git remote 2>$null)
Pop-Location
if ($remotes -contains 'origin') { $hasRemote = $true }
$confirmed = ($cfg -and $cfg.confirmedByOwner -eq $true -and $cfg.branch)
$state = if ($hasRemote -and $confirmed) { 'PUBLISHED' } else { 'PUBLISH_PENDING' }

if ($DryRun) {
    Write-Host "DRY RUN: all required gates for phase $Phase are green. Would commit + tag phase-$Phase and record $state."
    exit 0
}

Push-Location $root
$name = (Get-Content (Join-Path $root 'docs/phases.json') -Raw | ConvertFrom-Json).phases | Where-Object { $_.id -eq "P$Phase" } | Select-Object -First 1
# Secret scan of everything about to be committed
$scan = Invoke-SecretScan -Path $root -ConfigPath (Join-Path $root '.gitleaks.toml')
if (-not $scan.ran -or $scan.leaks -gt 0) { Pop-Location; Stop-Publish "secret scan did not pass: $($scan.output)" }
& git add -A
$msg = "phase-${Phase}: $($name.name)`n`nAll gates passed (tests, security, review, performance, evaluation).`n`nCo-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>"
& git commit -m $msg 2>&1 | Out-Host
& git tag -f "phase-$Phase" 2>&1 | Out-Host
$commit = (& git rev-parse --short HEAD)
$branch = (& git rev-parse --abbrev-ref HEAD)
if ($state -eq 'PUBLISHED') {
    & git push -u origin $branch 2>&1 | Out-Host
    if ($LASTEXITCODE -ne 0) { $state = 'PUBLISH_PENDING' }
    else { & git push -f origin "phase-$Phase" 2>&1 | Out-Host }
}
Pop-Location
Set-PhaseStatus -Phase $Phase -Status 'COMPLETE' -Publish $state
$logFile = Join-Path $root 'docs/publish-log.md'
if (-not (Test-Path $logFile)) { Set-Content $logFile "# Publish log`n`n| Phase | Branch | Commit | State | Time (UTC) |`n|---|---|---|---|---|" -Encoding utf8 }
Add-Content $logFile "| P$Phase | $branch | $commit | $state | $((Get-Date).ToUniversalTime().ToString('s')) |" -Encoding utf8
Write-Host "Phase $Phase recorded as $state (commit $commit, tag phase-$Phase)."
exit 0
