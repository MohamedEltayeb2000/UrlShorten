# AI evaluation gate (P4+). Usage: scripts/gate-eval.ps1 -Phase N
param([Parameter(Mandatory = $true)][int]$Phase)
. "$PSScriptRoot/lib.ps1"
$root = Get-Root
if ($Phase -lt 4) { Complete-Gate -Phase $Phase -Gate 'EVAL' -Outcome 'SKIPPED' -Findings @() -Extra @{ reason = 'No agents before P4' } }
$findings = @()
Push-Location (Join-Path $root 'backend')
& mvn -q -B test '-Dgroups=eval' 2>&1 | Out-Host
$code = $LASTEXITCODE
Pop-Location
if ($code -ne 0) { $findings += New-Finding 'HIGH' 'Evaluation tests failed (score below threshold, degraded set not caught, or non-deterministic)' 'backend/target/surefire-reports' }
$report = Join-Path $root 'docs/evals/latest.json'
if (-not (Test-Path $report)) { $findings += New-Finding 'HIGH' 'Evaluation report was not produced' 'docs/evals/latest.json' }
else { Copy-Item $report (Join-Path $root "docs/evals/phase-$Phase.json") -Force }
$outcome = if ($findings.Count -eq 0) { 'PASS' } else { 'FAIL' }
Complete-Gate -Phase $Phase -Gate 'EVAL' -Outcome $outcome -Findings $findings
