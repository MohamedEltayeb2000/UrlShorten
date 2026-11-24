# Shared helpers for gate scripts. Dot-source: . "$PSScriptRoot/lib.ps1"
$script:Root = Split-Path -Parent $PSScriptRoot
$script:Gitleaks = Join-Path $script:Root '.tools/gitleaks/gitleaks.exe'

function Get-Root { return $script:Root }

function Get-GitleaksPath {
    if (Test-Path $script:Gitleaks) { return $script:Gitleaks }
    $cmd = Get-Command gitleaks -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}

function New-Finding {
    param([string]$Severity, [string]$Summary, [string]$Location = '')
    return [pscustomobject]@{ severity = $Severity; summary = $Summary; location = $Location }
}

function Write-GateResult {
    param([int]$Phase, [string]$Gate, [string]$Outcome, $Findings = @(), [hashtable]$Extra = @{})
    $dir = Join-Path $script:Root 'docs/gates'
    New-Item -ItemType Directory -Force $dir | Out-Null
    $file = Join-Path $dir "phase-$Phase.json"
    if (Test-Path $file) {
        $obj = Get-Content $file -Raw | ConvertFrom-Json
    } else {
        $obj = [pscustomobject]@{ phase = $Phase; gates = [pscustomobject]@{} }
    }
    $entry = [ordered]@{ outcome = $Outcome; findings = @($Findings); timestamp = (Get-Date).ToUniversalTime().ToString('o') }
    foreach ($k in $Extra.Keys) { $entry[$k] = $Extra[$k] }
    $obj.gates | Add-Member -NotePropertyName $Gate -NotePropertyValue ([pscustomobject]$entry) -Force
    ($obj | ConvertTo-Json -Depth 10) | Set-Content -Path $file -Encoding utf8
}

function Get-ExitCode {
    param([string]$Outcome)
    switch ($Outcome) { 'PASS' { 0 } 'FAIL' { 1 } 'NOT_RUN' { 2 } 'SKIPPED' { 3 } default { 1 } }
}

function Complete-Gate {
    param([int]$Phase, [string]$Gate, [string]$Outcome, $Findings = @(), [hashtable]$Extra = @{})
    Write-GateResult -Phase $Phase -Gate $Gate -Outcome $Outcome -Findings $Findings -Extra $Extra
    Write-Host ("[{0}] phase {1}: {2}" -f $Gate, $Phase, $Outcome)
    foreach ($f in @($Findings)) { Write-Host ("  - {0}: {1} {2}" -f $f.severity, $f.summary, $f.location) }
    exit (Get-ExitCode $Outcome)
}

# ---- secret scanning -------------------------------------------------------
# Returns @{ ran = bool; leaks = int; output = string }
function Invoke-SecretScan {
    param([string]$Path, [string]$ConfigPath = '')
    $gl = Get-GitleaksPath
    if (-not $gl) { return @{ ran = $false; leaks = 0; output = 'gitleaks not installed' } }
    $args = @('dir', $Path, '--no-banner', '--redact', '--exit-code', '1', '--log-level', 'error')
    if ($ConfigPath) { $args += @('--config', $ConfigPath) }
    $out = & $gl @args 2>&1 | Out-String
    $code = $LASTEXITCODE
    if ($code -eq 0) { return @{ ran = $true; leaks = 0; output = $out } }
    if ($code -eq 1) { return @{ ran = $true; leaks = 1; output = $out } }
    return @{ ran = $false; leaks = 0; output = "gitleaks error (exit $code): $out" }
}

# ---- review document check -------------------------------------------------
# Returns a list of problem strings (empty = OK)
function Test-ReviewDocument {
    param([string]$Path)
    $problems = @()
    if (-not (Test-Path $Path)) { return @("review file missing: $Path") }
    $lines = Get-Content $Path
    foreach ($l in $lines) {
        if ($l -match '^\s*- \[ \]') { $problems += "unanswered checklist item: $($l.Trim())" }
        if ($l -match '^\|\s*[A-Za-z0-9-]+\s*\|\s*(BLOCKER|HIGH)\s*\|') {
            if ($l -match '\|\s*OPEN\s*\|?\s*$') { $problems += "open $($Matches[1]) finding: $($l.Trim())" }
            if ($l -match 'WAIVED' -and ($l -notmatch 'reason' -or $l -notmatch 'approver' -or $l -notmatch 'date')) {
                $problems += "waiver missing reason/approver/date: $($l.Trim())"
            }
        }
    }
    $hasChecklist = $lines | Where-Object { $_ -match '^\s*- \[[xX]\]' }
    if (-not $hasChecklist) { $problems += 'review has no answered checklist items' }
    return $problems
}

function Test-WalkthroughDocument {
    param([string]$Path, [int]$MaxSeconds)
    $problems = @()
    if (-not (Test-Path $Path)) { return @("walkthrough missing: $Path") }
    $text = Get-Content $Path -Raw
    if ($text -match '(?m)^- Seconds:\s*(\d+)') {
        $s = [int]$Matches[1]
        if ($s -le 0) { $problems += 'walkthrough has no timed result' }
        elseif ($s -gt $MaxSeconds) { $problems += "walkthrough took $s s (budget $MaxSeconds s)" }
    } else { $problems += 'walkthrough missing "- Seconds: N" line' }
    if ($text -notmatch '(?m)^- Completed without instructions:\s*Y') { $problems += 'walkthrough: not completed without instructions' }
    if ($text -match '(?m)^\s*- \[ \]') { $problems += 'walkthrough has unchecked accessibility items' }
    return $problems
}

function Set-PhaseStatus {
    param([int]$Phase, [string]$Status, [string]$Publish = '')
    $file = Join-Path $script:Root 'docs/phases.json'
    $pj = Get-Content $file -Raw | ConvertFrom-Json
    foreach ($p in $pj.phases) {
        if ($p.id -eq "P$Phase") {
            $p.status = $Status
            if ($Publish) { $p.publish = $Publish }
        }
    }
    ($pj | ConvertTo-Json -Depth 10) | Set-Content -Path $file -Encoding utf8
}

function Get-OwnedRequirements {
    param([int]$UpToPhase)
    $pj = Get-Content (Join-Path $script:Root 'docs/phases.json') -Raw | ConvertFrom-Json
    $owned = @{}
    foreach ($p in $pj.phases) {
        $n = [int]($p.id.Substring(1))
        if ($n -le $UpToPhase) { foreach ($r in $p.requirements) { $owned[$r] = $p.id } }
    }
    return $owned
}
