param(
    [Parameter(Mandatory = $true)]
    [string]$FirebaseProjectId,
    [Parameter(Mandatory = $true)]
    [string]$ConfirmProjectId
)

$ErrorActionPreference = "Stop"

if ($FirebaseProjectId -ne $ConfirmProjectId) {
    throw "Confirmation must exactly match FirebaseProjectId."
}
if ($FirebaseProjectId -match "demo|example|your-company") {
    throw "Refusing to deploy Firebase configuration to a placeholder project."
}

firebase --version | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Firebase CLI is required." }

Write-Host "Deploying reviewed Firestore rules, indexes and Storage rules to buyer project '$FirebaseProjectId'."
firebase deploy --project $FirebaseProjectId --only firestore:rules,firestore:indexes,storage
if ($LASTEXITCODE -ne 0) {
    throw "Firebase configuration deployment failed."
}

Write-Host "Firebase rules/indexes deployment completed. Auth providers and App Check enforcement must still be verified in the buyer Firebase console."
