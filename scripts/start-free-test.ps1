param([int]$Port = 8080)
$ErrorActionPreference = 'Stop'
$testRoot = Split-Path -Parent $PSScriptRoot
$testTools = Join-Path $testRoot '.dist\tools'
New-Item -ItemType Directory -Force -Path $testTools | Out-Null
$testRelease = Invoke-RestMethod -Uri 'https://api.github.com/repos/cloudflare/cloudflared/releases/latest' -Headers @{ 'User-Agent' = 'RadioTech-test-setup' }
$testAsset = $testRelease.assets | Where-Object name -eq 'cloudflared-windows-amd64.exe' | Select-Object -First 1
if (!$testAsset -or $testAsset.digest -notmatch '^sha256:[a-f0-9]{64}$') { throw 'Official release has no verifiable SHA256 digest.' }
$testBinary = Join-Path $testTools ('cloudflared-' + $testRelease.tag_name + '.exe')
if (!(Test-Path -LiteralPath $testBinary)) { Invoke-WebRequest -Uri $testAsset.browser_download_url -OutFile $testBinary }
if ((Get-FileHash -LiteralPath $testBinary -Algorithm SHA256).Hash.ToLowerInvariant() -ne $testAsset.digest.Substring(7)) { throw 'cloudflared SHA256 mismatch.' }
$testHealth = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/api/v1/health" -TimeoutSec 15
if ($testHealth.status -ne 'UP') { throw 'Backend not ready.' }
$testPrivate = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/api/v1/pro/catalog" -SkipHttpErrorCheck -TimeoutSec 15
if ($testPrivate.StatusCode -ne 401) { throw 'Authenticated API must reject anonymous requests before opening a tunnel.' }
$testStamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$testLog = Join-Path $testTools "tunnel-$testStamp.log"
$testOutput = Join-Path $testTools "tunnel-$testStamp.stdout.log"
$testProcess = Start-Process -FilePath $testBinary -ArgumentList @('tunnel','--no-autoupdate','--url',"http://127.0.0.1:$Port") -WindowStyle Hidden -RedirectStandardError $testLog -RedirectStandardOutput $testOutput -PassThru
$testProcess.Id | Set-Content -LiteralPath (Join-Path $testTools 'tunnel.pid')
$testUrl = $null
for ($testAttempt = 0; $testAttempt -lt 45; $testAttempt++) {
    if ($testProcess.HasExited) { throw "Tunnel exited. Read $testLog" }
    $testText = Get-Content -LiteralPath $testLog -Raw -ErrorAction SilentlyContinue
    if ($testText -match 'https://[a-z0-9-]+\.trycloudflare\.com') { $testUrl = $Matches[0]; break }
    Start-Sleep -Seconds 1
}
if (!$testUrl) { throw "Tunnel did not report a URL. Read $testLog" }
$testUrl | Set-Content -LiteralPath (Join-Path $testTools 'test-url.txt')
Write-Output "Web: $testUrl"
Write-Output "Customer portal: $testUrl/portal"
Write-Output "Mobile backend: $testUrl"
Write-Output "Temporary free test. Keep this PC and backend running. Tunnel PID: $($testProcess.Id)."
