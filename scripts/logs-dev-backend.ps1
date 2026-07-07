# Tail delivery-backend logs (dev).
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ProjectRoot = Split-Path $PSScriptRoot -Parent
if (-not (Test-Path (Join-Path $ProjectRoot 'docker-compose.dev.yml'))) {
    throw "docker-compose.dev.yml not found in $ProjectRoot"
}

Set-Location $ProjectRoot

Write-Host "Command: docker compose -f .\docker-compose.dev.yml logs -f --tail=100 delivery-backend" -ForegroundColor Cyan
& docker compose -f '.\docker-compose.dev.yml' logs -f --tail=100 delivery-backend
exit $LASTEXITCODE
