param([Parameter(Mandatory)][string]$ConfigPath)
$ErrorActionPreference = 'Stop'
$backupRoot = Split-Path -Parent $PSScriptRoot
$backupConfig = Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json
$env:FIREBASE_PROJECT_ID = $backupConfig.projectId
$env:FIREBASE_SERVICE_ACCOUNT_PATH = $backupConfig.serviceAccountPath
if ($backupConfig.storageBucket) { $env:FIREBASE_STORAGE_BUCKET = $backupConfig.storageBucket }
$backupStamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupArchive = Join-Path $backupConfig.destination "radiotech-$backupStamp.rtbackup"
Push-Location $backupRoot
try {
    & (Join-Path $backupRoot 'gradlew.bat') backupTool --console=plain "-PbackupMode=export" "-PbackupArchive=$backupArchive" "-PbackupKey=$($backupConfig.keyPath)"
    if ($LASTEXITCODE -ne 0) { throw 'Backup export failed.' }
    & (Join-Path $backupRoot 'gradlew.bat') backupTool --console=plain "-PbackupMode=verify" "-PbackupArchive=$backupArchive" "-PbackupKey=$($backupConfig.keyPath)"
    if ($LASTEXITCODE -ne 0) { throw 'Backup authentication/verification failed.' }
} finally { Pop-Location }
