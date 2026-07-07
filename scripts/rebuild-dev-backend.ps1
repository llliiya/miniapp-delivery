# Rebuild and restart only delivery-backend (dev).
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "=== $Message ===" -ForegroundColor Cyan
}

$ProjectRoot = Split-Path $PSScriptRoot -Parent
if (-not (Test-Path (Join-Path $ProjectRoot 'docker-compose.dev.yml'))) {
    throw "docker-compose.dev.yml not found in $ProjectRoot"
}

Set-Location $ProjectRoot
$BuildStopwatch = [System.Diagnostics.Stopwatch]::StartNew()

Write-Step "Project root: $ProjectRoot"
Write-Step "Build delivery-backend (this service only)"
Write-Host "Command: docker compose --progress plain -f .\docker-compose.dev.yml build delivery-backend"

& docker compose --progress plain -f '.\docker-compose.dev.yml' build delivery-backend
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "BUILD FAILED (exit code $LASTEXITCODE)" -ForegroundColor Red
    exit $LASTEXITCODE
}

$BuildStopwatch.Stop()
$BuildSeconds = [math]::Round($BuildStopwatch.Elapsed.TotalSeconds, 1)
Write-Host ""
Write-Host "Build finished in $BuildSeconds s" -ForegroundColor Green

Write-Step "Restart delivery-backend (--no-deps)"
Write-Host "Command: docker compose -f .\docker-compose.dev.yml up -d --no-deps delivery-backend"

& docker compose -f '.\docker-compose.dev.yml' up -d --no-deps delivery-backend
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "RESTART FAILED (exit code $LASTEXITCODE)" -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Step "Container status: delivery-backend"
& docker compose -f '.\docker-compose.dev.yml' ps delivery-backend
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Step "Last 100 log lines: delivery-backend"
& docker compose -f '.\docker-compose.dev.yml' logs --tail=100 delivery-backend
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "Done. Build: $BuildSeconds s. Only delivery-backend was restarted." -ForegroundColor Green
