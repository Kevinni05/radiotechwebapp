param()
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
if (!(Test-Path -LiteralPath '.env.production')) { throw 'Compile .env.production from the example with the real HTTPS origin and independent secrets.' }
if (!$env:FIREBASE_CREDENTIALS_FILE -or !(Test-Path -LiteralPath $env:FIREBASE_CREDENTIALS_FILE)) { throw 'Set FIREBASE_CREDENTIALS_FILE to an external credential file.' }
Get-Command docker -ErrorAction Stop | Out-Null
docker compose -f compose.yaml -f compose.enterprise.yaml config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Invalid deployment configuration.' }
docker compose -f compose.yaml -f compose.enterprise.yaml up --build -d --wait --wait-timeout 1800
if ($LASTEXITCODE -ne 0) { throw 'Enterprise startup failed: inspect private container logs.' }
Write-Output 'Backend and central AI started. The model is not exposed publicly.'
Write-Output 'Configure Caddy/DNS/TLS and run scripts/verify-release.ps1 against the public HTTPS origin.'
