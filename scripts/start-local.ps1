param([Parameter(Mandatory = $true)][string]$ServiceAccountPath, [string]$PublicBaseUrl = 'http://localhost:8080')
$ErrorActionPreference = 'Stop'
$qrRoot = Split-Path -Parent $PSScriptRoot
$qrCredentials = (Resolve-Path -LiteralPath $ServiceAccountPath).Path
$qrMetadata = Get-Content -Raw -LiteralPath $qrCredentials | ConvertFrom-Json
if ($qrMetadata.type -ne 'service_account' -or $qrMetadata.project_id -ne 'gestionale-radio') {
    throw 'Use a Firebase service account for gestionale-radio.'
}
$qrJar = Join-Path $qrRoot 'build\libs\radiotech.jar'
if (!(Test-Path -LiteralPath $qrJar)) { throw 'Build the backend with gradlew.bat bootJar first.' }
if (Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue) {
    throw 'Port 8080 is already in use. Stop the previous local RadioTech instance first.'
}
$env:FIREBASE_SERVICE_ACCOUNT_PATH = $qrCredentials
$env:FIREBASE_PROJECT_ID = 'gestionale-radio'
$env:RADIOTECH_FIREBASE_ENABLED = 'true'
if (!$env:RADIOTECH_FIREBASE_WEB_API_KEY) {
    $qrAndroidConfig = Join-Path (Split-Path -Parent $qrRoot) 'gestionale-radio\gestionale_radio\android\app\google-services.json'
    if (Test-Path -LiteralPath $qrAndroidConfig) {
        $qrFirebaseConfig = Get-Content -LiteralPath $qrAndroidConfig -Raw | ConvertFrom-Json
        if ($qrFirebaseConfig.project_info.project_id -eq 'gestionale-radio') {
            $env:RADIOTECH_FIREBASE_WEB_API_KEY = $qrFirebaseConfig.client[0].api_key[0].current_key
        }
    }
}
if (!$env:RADIOTECH_FIREBASE_WEB_API_KEY) { throw 'Set the Firebase public Web API key before starting the local backend.' }
if ($PublicBaseUrl -eq 'http://localhost:8080' -and (Test-Path -LiteralPath (Join-Path $qrRoot '.dist/tools/test-url.txt'))) { $PublicBaseUrl = (Get-Content -LiteralPath (Join-Path $qrRoot '.dist/tools/test-url.txt') -Raw).Trim() }
$qrOrigin = [Uri]$PublicBaseUrl
if (($qrOrigin.Scheme -ne 'https' -and $PublicBaseUrl -ne 'http://localhost:8080') -or $qrOrigin.UserInfo -or $qrOrigin.Query -or $qrOrigin.Fragment -or $qrOrigin.AbsolutePath -ne '/') { throw 'Use an HTTPS origin or the localhost test origin.' }
$qrSecrets = Join-Path $qrRoot '.dist/secrets'
New-Item -ItemType Directory -Force -Path $qrSecrets | Out-Null
$qrOwnerSid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& icacls.exe $qrSecrets /inheritance:r /grant:r ("*" + $qrOwnerSid + ':(OI)(CI)F') | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Unable to protect local signing keys.' }
foreach ($qrSecretName in @('RADIOTECH_REPORT_VERIFICATION_SECRET','RADIOTECH_TENANT_INVITE_SECRET')) {
    if (![Environment]::GetEnvironmentVariable($qrSecretName)) {
        $qrSecretFile = Join-Path $qrSecrets ($qrSecretName + '.txt')
        if (!(Test-Path -LiteralPath $qrSecretFile)) { $qrRandomBytes = [byte[]]::new(32); $qrRandom = [Security.Cryptography.RandomNumberGenerator]::Create(); try { $qrRandom.GetBytes($qrRandomBytes) } finally { $qrRandom.Dispose() }; [IO.File]::WriteAllText($qrSecretFile,[Convert]::ToBase64String($qrRandomBytes)) }
        [Environment]::SetEnvironmentVariable($qrSecretName,(Get-Content -LiteralPath $qrSecretFile -Raw).Trim(),'Process')
    }
}
$env:RADIOTECH_PUBLIC_BASE_URL = $PublicBaseUrl.TrimEnd('/')
$env:RADIOTECH_CORS_ORIGINS = "http://localhost:8080,http://127.0.0.1:8080,$($env:RADIOTECH_PUBLIC_BASE_URL)"
if (!$env:RADIOTECH_AI_ENABLED) {
    try {
        $qrModels = Invoke-RestMethod -Uri 'http://127.0.0.1:11434/api/tags' -TimeoutSec 3
        if ($qrModels.models.name -contains 'qwen3:4b') { $env:RADIOTECH_AI_ENABLED = 'true'; $env:RADIOTECH_OLLAMA_URL = 'http://127.0.0.1:11434'; $env:RADIOTECH_AI_MODEL = 'qwen3:4b' }
    } catch { # The optional local AI stays disabled if no installed model is available.
    }
}
$qrLogDirectory = Join-Path $qrRoot '.dist'
New-Item -ItemType Directory -Force -Path $qrLogDirectory | Out-Null
$qrJava = (Get-Command java).Source
$qrProcess = Start-Process -FilePath $qrJava -ArgumentList @(
    '-Xmx768m', '-jar', ('"' + $qrJar + '"'), '--server.port=8080',
    '--logging.file.name=.dist/local-backend.log', '--server.forward-headers-strategy=native',
    '--server.tomcat.remoteip.internal-proxies=127\.0\.0\.1|::1'
) -WorkingDirectory $qrRoot -WindowStyle Hidden -PassThru
@{ pid = $qrProcess.Id; workspace = $qrRoot; serviceAccountPath = $qrCredentials } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $qrSecrets 'local-runtime.json') -Encoding utf8
Write-Output "RadioTech local PID: $($qrProcess.Id). Log: .dist/local-backend.log"
