param(
  [Parameter(Mandatory=$true)][ValidatePattern('^v[0-9]+\.[0-9]+\.[0-9]+-[0-9]+$')][string]$Tag,
  [Parameter(Mandatory=$true)][string]$ApkSigner,
  [switch]$ValidateOnly
)
$ErrorActionPreference = 'Stop'
$expectedCertificate = '73005990b6bad93b1bb3383121f6dcc2293b3e5e3a60e1ae7eaf09a7d97c4fa4'
$root = Split-Path -Parent $PSScriptRoot
$release = Invoke-RestMethod -Uri "https://api.github.com/repos/Kevinni05/gestionale_radio/releases/tags/$Tag" -Headers @{Accept='application/vnd.github+json'} -TimeoutSec 30
if ($release.draft -or $release.prerelease -or $release.tag_name -ne $Tag) { throw 'A published production release is required.' }
$metadataAsset = @($release.assets | Where-Object name -eq 'mobile-release.json')
if ($metadataAsset.Count -ne 1) { throw 'The signed release manifest is missing or ambiguous.' }
$manifest = Invoke-RestMethod -Uri $metadataAsset[0].browser_download_url -TimeoutSec 30
if ($manifest.applicationId -ne 'com.example.gestionale_radio' -or $manifest.channel -ne 'GITHUB_RELEASES' -or $manifest.signingSha256 -ne $expectedCertificate -or $manifest.sha256 -notmatch '^[a-f0-9]{64}$' -or $manifest.versionCode -le 2020 -or "v$($manifest.versionName)-$($manifest.versionCode)" -ne $Tag) { throw 'Unexpected release identity, version or signature.' }
$expectedName = "RadioTech-$($manifest.versionName)-$($manifest.versionCode).apk"
$expectedUrl = "https://github.com/Kevinni05/gestionale_radio/releases/download/$Tag/$expectedName"
$asset = @($release.assets | Where-Object { $_.name -eq $expectedName -and $_.browser_download_url -eq $expectedUrl })
if ($asset.Count -ne 1 -or $manifest.downloadUrl -ne $expectedUrl) { throw 'The verified APK asset is missing or points outside the release.' }
$temporaryApk = Join-Path ([IO.Path]::GetTempPath()) ("radiotech-release-" + [Guid]::NewGuid().ToString('N') + '.apk')
try {
  Invoke-WebRequest -Uri $expectedUrl -OutFile $temporaryApk -TimeoutSec 180
  if ((Get-FileHash -LiteralPath $temporaryApk -Algorithm SHA256).Hash.ToLowerInvariant() -ne $manifest.sha256) { throw 'The published APK checksum differs from its manifest.' }
  $certificateReport = (& $ApkSigner verify --verbose --print-certs $temporaryApk 2>&1 | Out-String)
  if ($LASTEXITCODE -ne 0 -or $certificateReport -notmatch '(?:Signer #1|V[234](?:\.\d+)? Signer):? certificate SHA-256 digest: ([a-f0-9]+)' -or $Matches[1] -ne $expectedCertificate -or $certificateReport -match 'Android Debug') { throw 'The published APK signature is invalid.' }
  $manifest.available = $true
  if (!$ValidateOnly) { $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $root 'src/main/resources/mobile-release.json') -Encoding utf8 }
  Write-Output "Published APK verified: $Tag. Commit and push the backend manifest to activate mobile updates."
} finally { if (Test-Path -LiteralPath $temporaryApk) { Remove-Item -LiteralPath $temporaryApk } }
