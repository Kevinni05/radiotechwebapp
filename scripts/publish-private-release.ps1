param([Parameter(Mandatory=$true)][string]$Tenant,[Parameter(Mandatory=$true)][string]$Apk,[Parameter(Mandatory=$true)][string]$Manifest,[Parameter(Mandatory=$true)][string]$Project)
$ErrorActionPreference='Stop'
$taskSdk=Join-Path $env:LOCALAPPDATA 'Android/Sdk/build-tools/36.0.0'
$taskReport=(& (Join-Path $taskSdk 'apksigner.bat') verify --verbose --print-certs $Apk 2>&1 | Out-String)
if($LASTEXITCODE -ne 0 -or $taskReport -match 'Android Debug' -or $taskReport -notmatch 'Signer #1 certificate SHA-256 digest: ([a-f0-9]+)'){throw 'Production APK verification failed.'}
$taskCertificate=$Matches[1]
$taskMetadata=Get-Content -LiteralPath $Manifest -Raw | ConvertFrom-Json
$taskExpected=(Get-Content -LiteralPath (Join-Path $env:USERPROFILE '.radiotech/signing/certificate.json') -Raw | ConvertFrom-Json)
$taskIdentity=(& (Join-Path $taskSdk 'aapt.exe') dump badging $Apk | Select-Object -First 1)
if($taskCertificate -ne $taskMetadata.signingSha256 -or $taskCertificate -ne $taskExpected.sha256){throw 'Unexpected production signing identity.'}
if($taskIdentity -notmatch "name='com.example.gestionale_radio'" -or $taskMetadata.applicationId -ne 'com.example.gestionale_radio' -or $taskIdentity -notmatch "versionCode='$($taskMetadata.versionCode)'" -or $taskIdentity -notmatch "versionName='$($taskMetadata.versionName)'" -or $taskMetadata.channel -ne 'PRIVATE_SITE'){throw 'APK/manifest identity mismatch.'}
$env:FIREBASE_PROJECT_ID=$Project
$env:GOOGLE_APPLICATION_CREDENTIALS=Join-Path $env:USERPROFILE '.radiotech/cloud/cli-adc.json'
node (Join-Path $PSScriptRoot '../functions/publish-private-release.cjs') $Tenant $Apk $Manifest
if($LASTEXITCODE -ne 0){throw 'Private release publication failed.'}
