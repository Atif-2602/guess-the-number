# ============================================================
# End-to-end test for the Guess the Number web server.
#
# Starts the Java server on a spare port, checks the pages and the
# JSON API, then stops the server again.
#
# Usage:  test.bat            (or)  powershell -File tests/api-test.ps1
# ============================================================

param(
    [int]$Port = 8099
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$base = "http://localhost:$Port"

$pass = 0
$fail = 0

function Check($name, $condition, $detail) {
    if ($condition) {
        $script:pass++
        Write-Host "PASS  $name" -ForegroundColor Green
    } else {
        $script:fail++
        Write-Host "FAIL  $name  ($detail)" -ForegroundColor Red
    }
}

function PostJson($url, $payload) {
    try {
        $body = ($payload | ConvertTo-Json -Compress)
        return Invoke-RestMethod -Method Post -Uri $url -ContentType 'application/json' -Body $body
    } catch {
        return @{
            __error = $true
            status  = [int]$_.Exception.Response.StatusCode
            message = $_.ErrorDetails.Message
        }
    }
}

# --- build if needed -----------------------------------------
if (-not (Test-Path (Join-Path $root 'out'))) {
    Write-Host 'Compiling...'
    & (Join-Path $root 'build.bat') | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Build failed.'
    }
}

# --- start the server ----------------------------------------
$log = Join-Path $env:TEMP "guess-the-number-$Port.log"
$server = Start-Process -FilePath 'java' `
    -ArgumentList '-Dgtn.noBrowser=true', '-cp', 'out', 'guessnumber.web.WebServer', "$Port" `
    -PassThru -WindowStyle Hidden `
    -RedirectStandardOutput $log -RedirectStandardError "$log.err"

$ready = $false
try {
    for ($i = 0; $i -lt 60; $i++) {
        if ($server.HasExited) {
            break
        }
        try {
            $null = Invoke-WebRequest "$base/" -UseBasicParsing -TimeoutSec 2
            $ready = $true
            break
        } catch {
            Start-Sleep -Milliseconds 250
        }
    }

    if (-not $ready) {
        if ($server.HasExited) {
            Write-Host "Server stopped immediately. Is port $Port already in use?" -ForegroundColor Red
        }
        throw 'Server did not start.'
    }

    Write-Host "Server started on $base" -ForegroundColor Cyan
    Write-Host ''

    # --- static pages ----------------------------------------
    $index = Invoke-WebRequest "$base/" -UseBasicParsing
    Check 'index.html served' ($index.StatusCode -eq 200 -and $index.Content -match 'Guess the') "status=$($index.StatusCode)"
    Check 'index content type' ($index.Headers['Content-Type'] -like 'text/html*') $index.Headers['Content-Type']

    $css = Invoke-WebRequest "$base/styles.css" -UseBasicParsing
    Check 'styles.css served' ($css.StatusCode -eq 200 -and $css.Headers['Content-Type'] -like 'text/css*') $css.Headers['Content-Type']

    $js = Invoke-WebRequest "$base/app.js" -UseBasicParsing
    Check 'app.js served' ($js.StatusCode -eq 200 -and $js.Headers['Content-Type'] -like 'application/javascript*') $js.Headers['Content-Type']

    try {
        $traversal = Invoke-WebRequest "$base/../build.bat" -UseBasicParsing
        Check 'path traversal blocked' ($traversal.StatusCode -eq 404) "status=$($traversal.StatusCode)"
    } catch {
        $code = [int]$_.Exception.Response.StatusCode
        Check 'path traversal blocked' ($code -eq 404 -or $code -eq 400) "status=$code"
    }

    # --- new game --------------------------------------------
    $game = PostJson "$base/api/new-game" @{}
    Check 'new game returns id' (-not $game.__error -and $game.gameId -match '^[0-9a-f-]{36}$') ($game | ConvertTo-Json -Compress)
    Check 'new game range is 1-100' ($game.min -eq 1 -and $game.max -eq 100) "$($game.min)-$($game.max)"

    # --- validation ------------------------------------------
    $tooBig = PostJson "$base/api/guess" @{ gameId = $game.gameId; guess = 250 }
    Check 'guess 250 rejected' ($tooBig.__error -and $tooBig.status -eq 400) ($tooBig.message)
    Check 'rejected guess says out of range' ($tooBig.message -match 'Out of range') $tooBig.message

    $alpha = PostJson "$base/api/guess" @{ gameId = $game.gameId; guess = 'abc' }
    Check 'text guess rejected' ($alpha.__error -and $alpha.status -eq 400) ($alpha.message)

    $unknown = PostJson "$base/api/guess" @{ gameId = '00000000-0000-0000-0000-000000000000'; guess = 50 }
    Check 'unknown game rejected' ($unknown.__error -and $unknown.status -eq 404) ($unknown.message)

    # --- play a full round (binary search) --------------------
    $lo = 1
    $hi = 100
    $validGuesses = 0
    $solved = $null

    while ($lo -le $hi) {
        $mid = [math]::Floor(($lo + $hi) / 2)
        $result = PostJson "$base/api/guess" @{ gameId = $game.gameId; guess = $mid }
        $validGuesses++

        if ($result.status -eq 'CORRECT') { $solved = $result; break }
        if ($result.status -eq 'TOO_LOW') { $lo = $mid + 1 } else { $hi = $mid - 1 }
    }

    Check 'round solved via API' ($null -ne $solved) 'no CORRECT result'
    if ($null -ne $solved) {
        Check 'secret inside 1-100' ($solved.secretNumber -ge 1 -and $solved.secretNumber -le 100) $solved.secretNumber
        Check 'attempts count valid guesses only' ($solved.attempts -eq $validGuesses) "api=$($solved.attempts) sent=$validGuesses"
        Check 'finished flag set' ($solved.finished -eq $true) $solved.finished
        Write-Host "      solved: secret=$($solved.secretNumber) attempts=$($solved.attempts)"
    }

    $again = PostJson "$base/api/guess" @{ gameId = $game.gameId; guess = 50 }
    Check 'finished round rejects more guesses' ($again.__error -and $again.status -eq 409) ($again.message)

    Write-Host ''
    Write-Host "RESULT: $pass passed, $fail failed" -ForegroundColor Cyan
}
finally {
    if ($server -and -not $server.HasExited) {
        Stop-Process -Id $server.Id -Force
        Write-Host 'Server stopped.' -ForegroundColor DarkGray
    }
}

if ($fail -gt 0) {
    exit 1
}
exit 0
