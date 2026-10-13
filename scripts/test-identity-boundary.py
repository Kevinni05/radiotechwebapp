"""Read-only tests with the actual runtime identity, including effective inherited IAM."""
import json
import subprocess
import urllib.request
import urllib.error

runtime = 'radiotech-runtime@gestionale-radio.iam.gserviceaccount.com'
token = subprocess.run(['gcloud.cmd', 'auth', 'print-access-token', '--impersonate-service-account=' + runtime,
    '--quiet'], capture_output=True, text=True, check=True).stdout.strip()

def request(url, body=None):
    headers = {'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json', 'X-Goog-User-Project': 'gestionale-radio'}
    req = urllib.request.Request(url, data=json.dumps(body).encode() if body is not None else None, headers=headers)
    with urllib.request.urlopen(req, timeout=25) as response:
        return json.load(response)

dangerous = ['firebaseauth.users.create', 'firebaseauth.users.update', 'firebaseauth.users.delete',
    'firebaseauth.configs.update', 'firebaseauth.configs.getHashConfig', 'resourcemanager.projects.setIamPolicy',
    'run.services.create', 'run.services.update', 'secretmanager.versions.access']
app = request('https://cloudresourcemanager.googleapis.com/v1/projects/gestionale-radio:testIamPermissions',
    {'permissions': dangerous + ['firebaseauth.users.get', 'datastore.entities.get']}).get('permissions', [])
assert not set(app).intersection(dangerous), 'Unexpected elevated project permission.'
assert {'firebaseauth.users.get', 'datastore.entities.get'}.issubset(app), 'Missing operational read permissions.'
registry = request('https://cloudresourcemanager.googleapis.com/v1/projects/radiotech-id-1051055442339:testIamPermissions',
    {'permissions': dangerous + ['datastore.entities.get', 'datastore.entities.create', 'datastore.entities.update']}).get('permissions', [])
assert not registry, 'The application runtime can access the isolated project.'
signing = ['iam.serviceAccounts.signBlob', 'iam.serviceAccounts.signJwt', 'iam.serviceAccounts.getAccessToken',
    'iam.serviceAccounts.actAs', 'iam.serviceAccounts.setIamPolicy']
for account in [runtime, 'radiotech-identity@radiotech-id-1051055442339.iam.gserviceaccount.com']:
    result = request('https://iam.googleapis.com/v1/projects/-/serviceAccounts/' + account + ':testIamPermissions',
        {'permissions': signing}).get('permissions', [])
    assert not result, 'The runtime can sign or impersonate a service account.'
try:
    request('https://firestore.googleapis.com/v1/projects/radiotech-id-1051055442339/databases/(default)/documents/owners/main')
    raise AssertionError('The runtime read the isolated owner registry.')
except urllib.error.HTTPError as error:
    assert error.code == 403, 'Expected a real IAM denial, not a missing document.'
print(json.dumps({'runtime': runtime, 'authMutationsDenied': True, 'privateTokenSigningDenied': True,
    'authorityImpersonationDenied': True, 'registryReadAndWriteDenied': True, 'iamAndDeploymentDenied': True,
    'operationalReadsAllowed': True}))
