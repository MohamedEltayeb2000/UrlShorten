# Code review + walkthrough gate. Usage: scripts/gate-review.ps1 -Phase N
param([Parameter(Mandatory = $true)][int]$Phase)
. "$PSScriptRoot/lib.ps1"
$root = Get-Root
$budgets = Get-Content (Join-Path $root 'docs/perf/budgets.json') -Raw | ConvertFrom-Json
$findings = @()
foreach ($p in (Test-ReviewDocument -Path (Join-Path $root "docs/reviews/phase-$Phase.md"))) { $findings += New-Finding 'HIGH' $p "docs/reviews/phase-$Phase.md" }
foreach ($p in (Test-WalkthroughDocument -Path (Join-Path $root "docs/walkthroughs/phase-$Phase.md") -MaxSeconds $budgets.walkthrough_seconds)) { $findings += New-Finding 'HIGH' $p "docs/walkthroughs/phase-$Phase.md" }
$outcome = if ($findings.Count -eq 0) { 'PASS' } else { 'FAIL' }
Complete-Gate -Phase $Phase -Gate 'REVIEW' -Outcome $outcome -Findings $findings
