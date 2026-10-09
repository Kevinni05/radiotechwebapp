param(
    [string]$EnvFile = ".env.production",
    [string]$FirebaseCredentialsFile = $env:FIREBASE_CREDENTIALS_FILE
)

$ErrorActionPreference = "Stop"

if (!(Test-Path -LiteralPath $EnvFile)) {
    throw "Environment file not found: $EnvFile"
}

$values = @{}
Get-Content -LiteralPath $EnvFile | ForEach-Object {
    $line = $_.Trim()
    if (!$line -or $line.StartsWith("#")) { return }
    $parts = $line.Split("=", 2)
    if ($parts.Count -eq 2) {
        $values[$parts[0].Trim()] = $parts[1].Trim().Trim('"').Trim("'")
    }
}

$required = @(
    "FIREBASE_PROJECT_ID",
    "RADIOTECH_FIREBASE_WEB_API_KEY",
    "RADIOTECH_PUBLIC_BASE_URL",
    "RADIOTECH_CORS_ORIGINS",
    "RADIOTECH_REPORT_VERIFICATION_SECRET",
    "RADIOTECH_TENANT_INVITE_SECRET",
    "RADIOTECH_BRAND_NAME"
)

foreach ($name in $required) {
    if (!$values.ContainsKey($name) -or [string]::IsNullOrWhiteSpace($values[$name])) {
        throw "Missing required customer setting: $name"
    }
}

foreach ($name in @("RADIOTECH_PUBLIC_BASE_URL")) {
    $uri = $null
    if (![Uri]::TryCreate($values[$name], [UriKind]::Absolute, [ref]$uri) -or
        $uri.Scheme -ne "https" -or
        $uri.Host.EndsWith(".invalid")) {
        throw "$name must be a real HTTPS URL."
    }
}

$origins = $values["RADIOTECH_CORS_ORIGINS"].Split(",") | ForEach-Object { $_.Trim() } | Where-Object { $_ }
if ($origins.Count -eq 0) { throw "RADIOTECH_CORS_ORIGINS must contain at least one HTTPS origin." }
foreach ($origin in $origins) {
    $uri = $null
    if (![Uri]::TryCreate($origin, [UriKind]::Absolute, [ref]$uri) -or $uri.Scheme -ne "https") {
        throw "Invalid production CORS origin: $origin"
    }
}

foreach ($name in @("RADIOTECH_REPORT_VERIFICATION_SECRET", "RADIOTECH_TENANT_INVITE_SECRET")) {
    if ($values[$name].Length -lt 32) {
        throw "$name must contain at least 32 characters of independent random material."
    }
}

if ($values["RADIOTECH_REPORT_VERIFICATION_SECRET"] -eq $values["RADIOTECH_TENANT_INVITE_SECRET"]) {
    throw "Report verification and tenant invitation secrets must be independent."
}

if ($values["FIREBASE_PROJECT_ID"] -match "demo|example|your-company") {
    throw "FIREBASE_PROJECT_ID still contains a placeholder value."
}

if ([string]::IsNullOrWhiteSpace($FirebaseCredentialsFile) -or !(Test-Path -LiteralPath $FirebaseCredentialsFile)) {
    throw "Provide FIREBASE_CREDENTIALS_FILE pointing to the buyer-owned service-account / ADC JSON outside the repository."
}

$credentialJson = Get-Content -LiteralPath $FirebaseCredentialsFile -Raw | ConvertFrom-Json
if ($credentialJson.project_id -and $credentialJson.project_id -ne $values["FIREBASE_PROJECT_ID"]) {
    throw "Firebase credentials belong to '$($credentialJson.project_id)', not '$($values["FIREBASE_PROJECT_ID"])'."
}

Write-Host "Customer configuration validated."
Write-Host "Firebase project: $($values["FIREBASE_PROJECT_ID"])"
Write-Host "Public URL: $($values["RADIOTECH_PUBLIC_BASE_URL"])"
Write-Host "Brand: $($values["RADIOTECH_BRAND_NAME"])"
Write-Host "Credentials: external file verified (secret contents not printed)."
