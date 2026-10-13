param([string]$Origin = 'https://radiotech-identity-654979616749.europe-west1.run.app')
$ErrorActionPreference = 'Stop'
$approvalOrigin = [Uri]$Origin
if ($approvalOrigin.Scheme -ne 'https' -or $approvalOrigin.UserInfo -or $approvalOrigin.Query -or $approvalOrigin.Fragment -or $approvalOrigin.AbsolutePath -ne '/') {
    throw 'Use the independent HTTPS identity origin.'
}
$approvalToken = (& gcloud auth print-identity-token --account=kcagnazzo@gmail.com --quiet)
if ($LASTEXITCODE -ne 0 -or !$approvalToken) { throw 'Accedi a Google Cloud con kcagnazzo@gmail.com prima di aprire le approvazioni.' }
$approvalPayload = $approvalToken.Split('.')[1].Replace('-','+').Replace('_','/')
while ($approvalPayload.Length % 4) { $approvalPayload += '=' }
$approvalClaims = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($approvalPayload)) | ConvertFrom-Json
if ($approvalClaims.email -ne 'kcagnazzo@gmail.com' -or $approvalClaims.sub -ne '101939638982626665189' -or $approvalClaims.aud -ne '32555940559.apps.googleusercontent.com') {
    throw 'Identità Google non corrispondente al proprietario configurato.'
}
# Fragment tokens are never sent in HTTP requests; the independent page removes it immediately.
$approvalFragment = 'owner=' + [Uri]::EscapeDataString($approvalToken)
$approvalSetupPath = Join-Path $env:USERPROFILE '.radiotech\identity\owner-setup.txt'
if (Test-Path -LiteralPath $approvalSetupPath) {
    $approvalSetupCode = [IO.File]::ReadAllText($approvalSetupPath).Trim()
    $approvalFragment += '&setup=' + [Uri]::EscapeDataString($approvalSetupCode)
}
Start-Process -FilePath ($Origin.TrimEnd('/') + '/?intent=owner#' + $approvalFragment)
$approvalToken = $null
$approvalSetupCode = $null
$approvalFragment = $null
Write-Output 'Pagina di approvazione aperta. Ogni modifica ai privilegi richiede la tua passkey.'
