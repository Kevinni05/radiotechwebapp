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
$testRuntimePath = Join-Path $testRoot '.dist/secrets/local-runtime.json'
if (Test-Path -LiteralPath $testRuntimePath) {
    $testRuntime = Get-Content -LiteralPath $testRuntimePath -Raw | ConvertFrom-Json
    $testBackend = Get-CimInstance Win32_Process -Filter "ProcessId=$([int]$testRuntime.pid)"
    $testListener = Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue
    $testExpectedJar = [IO.Path]::GetFullPath((Join-Path $testRoot 'build\libs\radiotech.jar'))
    if ($Port -ne 8080 -or $testRuntime.workspace -ne $testRoot -or !$testBackend -or
        $testListener.OwningProcess -ne [int]$testRuntime.pid -or
        $testBackend.CommandLine -notmatch [regex]::Escape($testExpectedJar) -or
        $testBackend.Name -ne 'java.exe' -or !(Test-Path -LiteralPath $testRuntime.serviceAccountPath)) {
        throw 'Il backend attivo non coincide con quello registrato da start-local.ps1. Nessun backend è stato arrestato.'
    }
    Write-Output 'Aggiornamento del backend con il nuovo indirizzo HTTPS (CORS e QR)...'
    Stop-Process -Id ([int]$testRuntime.pid)
    & (Join-Path $PSScriptRoot 'start-local.ps1') -ServiceAccountPath $testRuntime.serviceAccountPath -PublicBaseUrl $testUrl
    $testReady = $false
    for ($testReadyAttempt = 0; $testReadyAttempt -lt 45; $testReadyAttempt++) {
        try {
            $testReadyResponse = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/api/v1/health" -TimeoutSec 2
            if ($testReadyResponse.status -eq 'UP') { $testReady = $true; break }
        } catch { }
        Start-Sleep -Seconds 1
    }
    if (!$testReady) { throw 'Il backend non è pronto. Controlla .dist/local-backend.log.' }
    $testCors = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/api/v1/auth/login" -Method Post -Headers @{ Origin = $testUrl } -ContentType 'application/json' -Body '{"email":"","password":""}' -SkipHttpErrorCheck -TimeoutSec 15
    if ($testCors.StatusCode -ne 401) { throw 'Il nuovo indirizzo HTTPS non ha superato la verifica del login.' }
} else {
    Write-Warning 'Backend avviato fuori da start-local.ps1: configura RADIOTECH_PUBLIC_BASE_URL e RADIOTECH_CORS_ORIGINS con questo nuovo link e riavvia il backend prima del login.'
}
Write-Output "Web: $testUrl"
Write-Output "Customer portal: $testUrl/portal"
Write-Output "Mobile backend: $testUrl"
Write-Output "Temporary free test. Keep this PC and backend running. Tunnel PID: $($testProcess.Id)."
