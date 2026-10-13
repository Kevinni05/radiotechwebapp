"""Configure only the managed providers/domain policy; never read or print password-hash configuration."""
import argparse
import json
import subprocess
import urllib.request
import urllib.error
from urllib.parse import urlparse

parser = argparse.ArgumentParser()
parser.add_argument("--origin", required=True)
parser.add_argument("--apply", action="store_true")
parser.add_argument("--upgrade", action="store_true", help="Enable Identity Platform on the already billing-enabled project.")
args = parser.parse_args()
project = "gestionale-radio"
origin = urlparse(args.origin)
if origin.scheme != "https" or not origin.hostname or origin.path not in {"", "/"} or origin.query or origin.fragment or origin.username:
    raise ValueError("An explicit HTTPS identity origin is required")
token = subprocess.run(["gcloud.cmd", "auth", "print-access-token"], capture_output=True, text=True, check=True).stdout.strip()

def request(path, method="GET", data=None):
    payload = json.dumps(data).encode() if data is not None else None
    variant = 'v2' if path == '/identityPlatform:initializeAuth' else 'admin/v2'
    req = urllib.request.Request("https://identitytoolkit.googleapis.com/" + variant + "/projects/" + project + path,
        data=payload, method=method, headers={"Authorization": "Bearer " + token, "X-Goog-User-Project": project, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            body = response.read()
            return json.loads(body) if body else {}
    except urllib.error.HTTPError as error:
        detail = json.load(error).get('error', {})
        message = detail.get('message', '')
        # Provider errors, not the configuration object; never print config or hash parameters.
        if error.code != 404 and isinstance(message, str) and len(message) < 250:
            print(json.dumps({'providerError': message, 'httpStatus': error.code}))
        raise

current = request("/config")
try:
    google = request("/defaultSupportedIdpConfigs/google.com")
except urllib.error.HTTPError as error:
    if error.code != 404:
        raise
    google = {}
domains = list(dict.fromkeys(current.get("authorizedDomains", []) + [origin.hostname]))
print(json.dumps({"dryRun": not args.apply, "identityHost": origin.hostname, "totpAdjacentIntervals": 1,
    "googleConfigured": bool(google.get("clientId")), "googleEnabled": google.get("enabled", False),
    "currentMfaState": current.get("mfa", {}).get("state"), "accountChanges": False}))
if args.apply:
    if args.upgrade:
        request('/identityPlatform:initializeAuth', 'POST', {})
    patch = {"authorizedDomains": domains, "mfa": {"state": "ENABLED", "providerConfigs": [{"state": "ENABLED", "totpProviderConfig": {"adjacentIntervals": 1}}]}}
    result = request("/config?updateMask=authorizedDomains,mfa", "PATCH", patch)
    if google.get("clientId") and not google.get("enabled"):
        request("/defaultSupportedIdpConfigs/google.com?updateMask=enabled", "PATCH", {"enabled": True})
    print(json.dumps({"configured": True, "mfaState": result.get("mfa", {}).get("state"), "identityHostAuthorized": origin.hostname in result.get("authorizedDomains", []), "existingAccountsUnchanged": True}))
