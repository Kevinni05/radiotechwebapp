"""Read-only identity/IAM inventory. Never print access tokens, password hashes or raw user exports."""
import json
import subprocess
import urllib.request
import urllib.error

project = "gestionale-radio"
credential = subprocess.run(["gcloud.cmd", "auth", "print-access-token"], capture_output=True, text=True, check=True).stdout.strip()

def get(url):
    request = urllib.request.Request(url, headers={"Authorization": "Bearer " + credential, "X-Goog-User-Project": project})
    with urllib.request.urlopen(request, timeout=25) as response:
        return json.load(response)

def output(label, fn):
    try:
        print(json.dumps({label: fn()}, ensure_ascii=False))
    except urllib.error.HTTPError as error:
        details = json.load(error).get("error", {})
        reasons = [item.get("reason") for item in details.get("details", []) if item.get("reason")]
        metadata = [{key: value for key, value in item.get("metadata", {}).items() if key in {"consumer", "service"}} for item in details.get("details", []) if item.get("metadata")]
        print(json.dumps({label: {"httpStatus": error.code, "reasons": reasons, "metadata": metadata}}))

def identity_inventory():
    data = get(f"https://identitytoolkit.googleapis.com/v1/projects/{project}/accounts:batchGet?maxResults=1000")
    selected = []
    for user in data.get("users", []):
        claims = json.loads(user.get("customAttributes", "{}"))
        if claims.get("role") in {"CAPO", "CHIEF_EXECUTIVE", "ADMIN", "SUPER_ADMIN", "NETWORK_MANAGER", "ENGINEER"} or claims.get("admin") or user.get("email") == "kcagnazzo@gmail.com":
            selected.append({"uid": user.get("localId"), "email": user.get("email"), "emailVerified": user.get("emailVerified", False),
                "disabled": user.get("disabled", False), "role": claims.get("role"), "tenantId": claims.get("tenantId"),
                "providers": [p.get("providerId") for p in user.get("providerUserInfo", [])], "mfaFactors": len(user.get("mfaInfo", []))})
    return {"selectedAccounts": selected, "morePages": bool(data.get("nextPageToken"))}

output("identities", identity_inventory)
def provider_configuration():
    data = get(f"https://identitytoolkit.googleapis.com/admin/v2/projects/{project}/config")
    return {"mfa": data.get("mfa"), "authorizedDomains": data.get("authorizedDomains"),
        "emailPasswordEnabled": data.get("signIn", {}).get("email", {}).get("enabled", False)}
output("identityPlatform", provider_configuration)

def permissions():
    request = urllib.request.Request(f"https://cloudresourcemanager.googleapis.com/v1/projects/{project}:testIamPermissions",
        data=json.dumps({"permissions": ["run.services.create", "run.services.update", "resourcemanager.projects.setIamPolicy", "iam.serviceAccounts.create", "serviceusage.services.enable", "firebaseauth.configs.update"]}).encode(),
        headers={"Authorization": "Bearer " + credential, "Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=25) as response:
        return json.load(response).get("permissions", [])
output("cloudUserPermissions", permissions)
