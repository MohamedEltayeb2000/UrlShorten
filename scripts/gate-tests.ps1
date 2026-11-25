# Tests + traceability gate. Usage: scripts/gate-tests.ps1 -Phase N
param([Parameter(Mandatory = $true)][int]$Phase)
. "$PSScriptRoot/lib.ps1"
$root = Get-Root
$findings = @()

Push-Location (Join-Path $root 'backend')
& mvn -q -B test 2>&1 | Out-Host
$backendCode = $LASTEXITCODE
Pop-Location
if ($backendCode -ne 0) { $findings += New-Finding 'HIGH' 'Backend tests failed' 'backend/target/surefire-reports' }

Push-Location (Join-Path $root 'frontend')
& npx vitest run --reporter=json --outputFile=test-results.json 2>&1 | Out-Host
$frontCode = $LASTEXITCODE
Pop-Location
if ($frontCode -ne 0) { $findings += New-Finding 'HIGH' 'Frontend tests failed' 'frontend/test-results.json' }

# Real-browser journeys against the running backend and frontend (Playwright); needs ports 8080 and 5173 free.
if ($Phase -ge 7) {
    Push-Location (Join-Path $root 'frontend')
    & npx playwright test 2>&1 | Out-Host
    $e2eCode = $LASTEXITCODE
    Pop-Location
    if ($e2eCode -ne 0) { $findings += New-Finding 'HIGH' 'Browser end-to-end tests failed' 'frontend/e2e/results.json' }
}

$trace = & (Join-Path $PSScriptRoot 'traceability.ps1') -Phase $Phase
foreach ($b in $trace.Blocking) {
    $findings += New-Finding 'HIGH' "Requirement $($b.Requirement) (owner $($b.Owner)) is $($b.Result)" 'docs/traceability.md'
}
$outcome = if ($findings.Count -eq 0) { 'PASS' } else { 'FAIL' }
Complete-Gate -Phase $Phase -Gate 'TESTS' -Outcome $outcome -Findings $findings -Extra @{ requirementsPassing = $trace.Passing; requirementsTotal = $trace.Total }
