# Runs every applicable gate for a phase. Usage: scripts/gate-all.ps1 -Phase N [-SkipDependencyCheck]
param([Parameter(Mandatory = $true)][int]$Phase, [switch]$SkipDependencyCheck)
. "$PSScriptRoot/lib.ps1"
$root = Get-Root
$gates = @(
    @{ name = 'TESTS';    script = 'gate-tests.ps1' },
    @{ name = 'SECURITY'; script = 'gate-security.ps1' },
    @{ name = 'REVIEW';   script = 'gate-review.ps1' },
    @{ name = 'PERF';     script = 'gate-perf.ps1' },
    @{ name = 'EVAL';     script = 'gate-eval.ps1' }
)
$resultsFile = Join-Path $root "docs/gates/phase-$Phase.json"
if (Test-Path $resultsFile) { Remove-Item $resultsFile -Force }
Set-PhaseStatus -Phase $Phase -Status 'GATES_RUNNING'
$summary = @()
$allGreen = $true
foreach ($g in $gates) {
    $argsList = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $PSScriptRoot $g.script), '-Phase', $Phase)
    if ($SkipDependencyCheck -and $g.name -eq 'SECURITY') { $argsList += '-SkipDependencyCheck' }
    & powershell @argsList | Out-Host
    $code = $LASTEXITCODE
    $label = switch ($code) { 0 { 'PASS' } 1 { 'FAIL' } 2 { 'NOT_RUN' } 3 { 'SKIPPED' } default { 'FAIL' } }
    if ($label -ne 'PASS' -and $label -ne 'SKIPPED') { $allGreen = $false }
    $summary += [pscustomobject]@{ Gate = $g.name; Outcome = $label }
}
Write-Host ''
Write-Host "Gate summary for phase $Phase"
$summary | Format-Table -AutoSize | Out-Host
Set-PhaseStatus -Phase $Phase -Status $(if ($allGreen) { 'COMPLETE' } else { 'BLOCKED' })
exit $(if ($allGreen) { 0 } else { 1 })
