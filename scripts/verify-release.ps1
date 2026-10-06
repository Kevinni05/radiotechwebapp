param([Parameter(Mandatory = $true)][string]$BaseUrl)
$ErrorActionPreference = 'Stop'
$target = [Uri]$BaseUrl
if (!$target.IsAbsoluteUri -or $target.Scheme -ne 'https' -or $target.UserInfo -or $target.Query -or $target.Fragment -or $target.AbsolutePath -ne '/') {
    throw 'Provide the canonical HTTPS API origin, without path, credentials or query.'
}
$origin = $BaseUrl.TrimEnd('/')
$health = Invoke-RestMethod -Uri "$origin/api/v1/health" -TimeoutSec 15
if ($health.service -ne 'radiotech-backend' -or $health.status -ne 'UP') { throw 'The target is not a healthy RadioTech API.' }
$ready = Invoke-RestMethod -Uri "$origin/actuator/health/readiness" -TimeoutSec 15
if ($ready.status -ne 'UP') { throw 'Production dependencies are not ready.' }
$page = Invoke-WebRequest -Uri "$origin/dashboard" -TimeoutSec 15
if (!$page.Headers['Content-Security-Policy'] -or !$page.Headers['Strict-Transport-Security']) { throw 'Missing CSP or HTTPS security headers.' }
try {
    Invoke-WebRequest -Uri "$origin/api/v1/tasks" -TimeoutSec 15 | Out-Null
    throw 'Unauthenticated business API unexpectedly succeeded.'
} catch {
    if ([int]$_.Exception.Response.StatusCode -ne 401) { throw }
}
Write-Output 'PASS: API identity, readiness, HTTPS headers and authentication boundary.'
Write-Output 'Run authenticated web/mobile acceptance tests with the staging tenant before production traffic.'
