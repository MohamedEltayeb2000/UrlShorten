# Performance gate. Usage: scripts/gate-perf.ps1 -Phase N
param([Parameter(Mandatory = $true)][int]$Phase)
. "$PSScriptRoot/lib.ps1"
$root = Get-Root
$budgets = Get-Content (Join-Path $root 'docs/perf/budgets.json') -Raw | ConvertFrom-Json
$findings = @()
$resultsFile = Join-Path $root 'docs/perf/results-latest.jsonl'
if (Test-Path $resultsFile) { Remove-Item $resultsFile -Force }

# Backend perf tests (JUnit @Tag("perf")) append metrics to docs/perf/results-latest.jsonl and assert budgets.
Push-Location (Join-Path $root 'backend')
& mvn -q -B test '-Dgroups=perf' 2>&1 | Out-Host
$perfCode = $LASTEXITCODE
Pop-Location
if ($perfCode -ne 0) { $findings += New-Finding 'HIGH' 'Backend performance tests exceeded a budget or failed' 'backend/target/surefire-reports' }

# Frontend: render-time tests and production bundle size.
Push-Location (Join-Path $root 'frontend')
& npx vitest run tests/perf 2>&1 | Out-Host
if ($LASTEXITCODE -ne 0) { $findings += New-Finding 'HIGH' 'Frontend render-time budget exceeded' 'frontend/tests/perf' }
& npm run build 2>&1 | Out-Null
$buildCode = $LASTEXITCODE
$gzipKb = 0
if ($buildCode -eq 0) {
    $total = 0
    Get-ChildItem 'dist/assets' -File | ForEach-Object {
        $bytes = [IO.File]::ReadAllBytes($_.FullName)
        $ms = New-Object IO.MemoryStream
        $gz = New-Object IO.Compression.GZipStream($ms, [IO.Compression.CompressionMode]::Compress)
        $gz.Write($bytes, 0, $bytes.Length); $gz.Close()
        $total += $ms.ToArray().Length
    }
    $gzipKb = [math]::Round($total / 1024, 1)
    if ($gzipKb -gt $budgets.bundle_gzip_kb) { $findings += New-Finding 'HIGH' "Bundle $gzipKb KB gzip exceeds budget $($budgets.bundle_gzip_kb) KB" 'frontend/dist' }
} else { $findings += New-Finding 'HIGH' 'Frontend build failed' 'frontend' }
Pop-Location

$lines = @("# Performance results - phase $Phase", "", "Generated $((Get-Date).ToUniversalTime().ToString('s'))Z", "",
    "## Environment", "- OS: $([Environment]::OSVersion.VersionString)", "- CPU cores: $([Environment]::ProcessorCount)",
    "- JDK: $((& java -version 2>&1 | Select-Object -First 1))", "- Node: $((& node --version))", "",
    "## Results", "| Metric | Budget | Measured | Pass |", "|---|---|---|---|")
if (Test-Path $resultsFile) {
    foreach ($l in Get-Content $resultsFile) { if ($l.Trim()) { $r = $l | ConvertFrom-Json; $lines += "| $($r.metric) | $($r.budget) | $($r.measured) | $($r.passed) |" } }
}
$lines += "| frontend bundle gzip KB | $($budgets.bundle_gzip_kb) | $gzipKb | $($gzipKb -le $budgets.bundle_gzip_kb) |"
New-Item -ItemType Directory -Force (Join-Path $root 'docs/perf') | Out-Null
Set-Content -Path (Join-Path $root "docs/perf/phase-$Phase.md") -Value $lines -Encoding utf8
$outcome = if ($findings.Count -eq 0) { 'PASS' } else { 'FAIL' }
Complete-Gate -Phase $Phase -Gate 'PERF' -Outcome $outcome -Findings $findings
