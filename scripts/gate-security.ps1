# Security gate. Usage: scripts/gate-security.ps1 -Phase N [-SkipDependencyCheck]
param([Parameter(Mandatory = $true)][int]$Phase, [switch]$SkipDependencyCheck)
. "$PSScriptRoot/lib.ps1"
$root = Get-Root
$findings = @()
$notRun = @()
$tools = @{}

# 1. Self-test: the scanner MUST detect a planted fake key, otherwise it cannot be trusted.
$tmp = Join-Path $root 'docs/gates/tmp/selftest'
New-Item -ItemType Directory -Force $tmp | Out-Null
$alnum = [char[]](48..57 + 65..90 + 97..122)
$fake = 'ghp_' + (-join (1..36 | ForEach-Object { $alnum | Get-Random }))
Set-Content -Path (Join-Path $tmp 'planted.txt') -Value "token = `"$fake`"" -Encoding utf8
$selfCfg = Join-Path $tmp 'gitleaks-selftest.toml'
Set-Content -Path $selfCfg -Value "[extend]`nuseDefault = true" -Encoding utf8
$self = Invoke-SecretScan -Path $tmp -ConfigPath $selfCfg
Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
if (-not $self.ran) { $notRun += "gitleaks: $($self.output)" }
elseif ($self.leaks -eq 0) { $findings += New-Finding 'HIGH' 'Secret scanner self-test failed: planted key not detected' 'scripts/gate-security.ps1' }
$tools['gitleaksSelfTest'] = $(if ($self.ran -and $self.leaks -gt 0) { 'detected-planted-key' } else { 'not-verified' })

# 2. Repository secret scan (fake fixtures are allow-listed in .gitleaks.toml)
if ($self.ran) {
    $scan = Invoke-SecretScan -Path $root -ConfigPath (Join-Path $root '.gitleaks.toml')
    if (-not $scan.ran) { $notRun += "gitleaks scan: $($scan.output)" }
    elseif ($scan.leaks -gt 0) { $findings += New-Finding 'HIGH' 'Secrets detected in repository' $scan.output.Trim() }
    $gl = Get-GitleaksPath
    if ($gl) { $tools['gitleaks'] = (& $gl version 2>&1 | Out-String).Trim() }
}

# 3. Hostile-input and no-bypass tests (JUnit @Tag("security"))
Push-Location (Join-Path $root 'backend')
& mvn -q -B test '-Dgroups=security' 2>&1 | Out-Host
$secCode = $LASTEXITCODE
Pop-Location
if ($secCode -ne 0) { $findings += New-Finding 'HIGH' 'Security test group failed' 'backend/target/surefire-reports' }

# 4. npm audit (high+)
Push-Location (Join-Path $root 'frontend')
$audit = & npm audit --audit-level=high 2>&1 | Out-String
$auditCode = $LASTEXITCODE
Pop-Location
if ($auditCode -ne 0) {
    if ($audit -match 'vulnerabilit') { $findings += New-Finding 'HIGH' 'npm audit found high or critical vulnerabilities' $audit.Trim() }
    else { $notRun += "npm audit: $($audit.Trim())" }
}

# 5. OWASP Dependency-Check (CVSS >= 7 fails). The first run downloads NVD data and can take a long time.
if ($SkipDependencyCheck) {
    $notRun += 'dependency-check skipped by flag'
} else {
    Push-Location (Join-Path $root 'backend')
    $dc = & mvn -B -Psecurity org.owasp:dependency-check-maven:check 2>&1 | Out-String
    $dcCode = $LASTEXITCODE
    Pop-Location
    if ($dcCode -ne 0) {
        if ($dc -match 'CVSS') { $findings += New-Finding 'HIGH' 'Dependency-Check found vulnerabilities with CVSS >= 7' 'docs/gates/tmp/dependency-check-report.json' }
        else { $notRun += 'dependency-check could not complete (NVD data unavailable/slow); set NVD_API_KEY and re-run' }
    }
}

foreach ($n in $notRun) { $findings += New-Finding 'BLOCKING' "NOT_RUN: $n" }
$outcome = if ($findings | Where-Object { $_.severity -ne 'BLOCKING' }) { 'FAIL' } elseif ($notRun.Count -gt 0) { 'NOT_RUN' } else { 'PASS' }
Complete-Gate -Phase $Phase -Gate 'SECURITY' -Outcome $outcome -Findings $findings -Extra @{ toolVersions = $tools }
