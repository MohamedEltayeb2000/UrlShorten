# Gate self-tests: prove the gates actually fail on bad input. Exit 0 only if all checks behave.
# Writes docs/gates/selftest.json. Cheap: does not run Maven or npm.
. "$PSScriptRoot/../lib.ps1"
$root = Get-Root
$results = [ordered]@{}
$tmp = Join-Path $root 'docs/gates/tmp/selftest-fixtures'
New-Item -ItemType Directory -Force $tmp | Out-Null

# 1. Planted secret must be detected
$alnum = [char[]](48..57 + 65..90 + 97..122)
$fake = 'ghp_' + (-join (1..36 | ForEach-Object { $alnum | Get-Random }))
$secretDir = Join-Path $tmp 'secret'
New-Item -ItemType Directory -Force $secretDir | Out-Null
Set-Content (Join-Path $secretDir 'planted.txt') "token = `"$fake`"" -Encoding utf8
$cfg = Join-Path $tmp 'gl.toml'
Set-Content $cfg "[extend]`nuseDefault = true" -Encoding utf8
$scan = Invoke-SecretScan -Path $secretDir -ConfigPath $cfg
$results['plantedSecretDetected'] = ($scan.ran -and $scan.leaks -gt 0)

# 2. Review with an open BLOCKER must fail; clean review must pass; waiver without approver must fail
$bad = Join-Path $tmp 'review-bad.md'
Set-Content $bad "- [x] Correctness`n| R1 | BLOCKER | something broke | OPEN |" -Encoding utf8
$good = Join-Path $tmp 'review-good.md'
Set-Content $good "- [x] Correctness`n| R1 | HIGH | fixed | FIXED |" -Encoding utf8
$waiver = Join-Path $tmp 'review-waiver.md'
Set-Content $waiver "- [x] Correctness`n| R1 | HIGH | x | WAIVED (reason: demo) |" -Encoding utf8
$results['openBlockerRejected'] = ((Test-ReviewDocument $bad).Count -gt 0)
$results['cleanReviewAccepted'] = ((Test-ReviewDocument $good).Count -eq 0)
$results['incompleteWaiverRejected'] = ((Test-ReviewDocument $waiver).Count -gt 0)

# 3. Walkthrough over budget must fail
$wt = Join-Path $tmp 'wt.md'
Set-Content $wt "- Seconds: 999`n- Completed without instructions: Y`n- [x] ok" -Encoding utf8
$results['slowWalkthroughRejected'] = ((Test-WalkthroughDocument $wt 180).Count -gt 0)

# 4. Publishing is refused for a phase with no gate results
$out = & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root 'scripts/publish-phase.ps1') -Phase 99 -DryRun 2>&1 | Out-String
$results['publishRefusedWithoutGates'] = ($LASTEXITCODE -ne 0 -and $out -match 'REFUSED')

Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
$ok = -not ($results.Values -contains $false)
$report = [ordered]@{ timestamp = (Get-Date).ToUniversalTime().ToString('o'); passed = $ok; checks = $results }
($report | ConvertTo-Json -Depth 5) | Set-Content (Join-Path $root 'docs/gates/selftest.json') -Encoding utf8
$results.GetEnumerator() | ForEach-Object { Write-Host ("{0,-30} {1}" -f $_.Key, $_.Value) }
exit $(if ($ok) { 0 } else { 1 })
