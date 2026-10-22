# ============================================================
# Publishes the contents of the web/ folder to the gh-pages branch.
# GitHub Pages serves that branch at:
#   https://atif-2602.github.io/guess-the-number/
#
# Usage:  deploy.bat
#         deploy.bat "my commit message"
# ============================================================

param(
    [string]$Message = "Deploy website"
)

# git writes progress to stderr; do not treat that as a fatal error.
$ErrorActionPreference = 'Continue'

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$tmp = Join-Path $env:TEMP ("gtn-pages-" + [guid]::NewGuid().ToString('N'))

function Fail($text) {
    Write-Host $text -ForegroundColor Red
    exit 1
}

Write-Host 'Preparing gh-pages branch...'

git fetch origin gh-pages 2>&1 | Out-Null

$hasBranch = -not [string]::IsNullOrWhiteSpace((git branch --list gh-pages))
if ($hasBranch) {
    git worktree add --force $tmp gh-pages 2>&1 | Out-Null
} else {
    git worktree add --force -b gh-pages $tmp origin/gh-pages 2>&1 | Out-Null
}

if ($LASTEXITCODE -ne 0 -or -not (Test-Path $tmp)) {
    Fail 'Could not create a temporary worktree for gh-pages.'
}

try {
    Push-Location $tmp

    git reset --hard origin/gh-pages 2>&1 | Out-Null

    # Replace the branch contents with the current web/ folder.
    Get-ChildItem -Force -Exclude '.git' | Remove-Item -Recurse -Force
    Copy-Item (Join-Path $root 'web\*') -Destination $tmp -Recurse -Force
    New-Item -ItemType File -Name '.nojekyll' -Force | Out-Null

    git add -A
    $changes = git status --porcelain

    if ([string]::IsNullOrWhiteSpace($changes)) {
        Write-Host 'Website is already up to date.' -ForegroundColor Yellow
    } else {
        git commit -m $Message 2>&1 | Out-Null
        git push origin gh-pages 2>&1 | Out-Null

        if ($LASTEXITCODE -ne 0) {
            Fail 'Could not push the gh-pages branch. Are you logged in with "gh auth login"?'
        }
        Write-Host 'Website pushed to gh-pages.' -ForegroundColor Green
    }

    Pop-Location
}
finally {
    Set-Location $root
    git worktree remove --force $tmp 2>&1 | Out-Null
    if (Test-Path $tmp) {
        Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Write-Host ''
Write-Host 'GitHub Pages will refresh in about a minute:' -ForegroundColor Cyan
Write-Host '  https://atif-2602.github.io/guess-the-number/' -ForegroundColor Cyan
exit 0
