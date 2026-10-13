"""Keep backup jobs operational without private Auth administrator keys."""
import json
import subprocess
import urllib.request
import urllib.error

project = 'gestionale-radio'
number = '1051055442339'
account = 'radiotech-backup@' + project + '.iam.gserviceaccount.com'

def run(args, missing=False):
    r = subprocess.run(['gcloud.cmd', *args, '--quiet'], capture_output=True, text=True)
    if r.returncode:
        if missing and ('NOT_FOUND' in r.stderr or 'does not exist' in r.stderr): return None
        raise RuntimeError(r.stderr.strip())
    return r.stdout.strip()

credentials = subprocess.run(['git', 'credential', 'fill'], input='protocol=https\nhost=github.com\n\n',
    capture_output=True, text=True, check=True)
secret = dict(line.split('=', 1) for line in credentials.stdout.splitlines() if '=' in line)['password']

def github(path, body=None, method=None):
    req = urllib.request.Request(('https://api.github.com/repos/Kevinni05/radiotechwebapp/' + path).rstrip('/'),
        data=json.dumps(body).encode() if body is not None else None, method=method,
        headers={'Authorization': 'Bearer ' + secret, 'Accept': 'application/vnd.github+json', 'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=25) as response:
        raw = response.read()
        return json.loads(raw) if raw else None

repo = github('')
assert repo['id'] == 1402944403 and repo['owner']['id'] == 335796463
if run(['iam', 'service-accounts', 'describe', account, '--project=' + project, '--format=value(email)'], True) is None:
    run(['iam', 'service-accounts', 'create', 'radiotech-backup', '--project=' + project])
for role in ['roles/firebaseauth.viewer', 'roles/datastore.user', 'roles/serviceusage.serviceUsageConsumer', 'roles/monitoring.viewer']:
    run(['projects', 'add-iam-policy-binding', project, '--member=serviceAccount:' + account, '--role=' + role, '--condition=None', '--format=value(etag)'])
pool = 'radiotech-backup'
if run(['iam', 'workload-identity-pools', 'describe', pool, '--location=global', '--project=' + project, '--format=value(name)'], True) is None:
    run(['iam', 'workload-identity-pools', 'create', pool, '--location=global', '--project=' + project, '--display-name=RadioTech backup jobs'])
provider = f'projects/{number}/locations/global/workloadIdentityPools/{pool}/providers/github'
condition = "assertion.repository_id == '1402944403' && assertion.repository_owner_id == '335796463' && assertion.ref == 'refs/heads/main' && (assertion.workflow_ref == 'Kevinni05/radiotechwebapp/.github/workflows/cloud-backup.yml@refs/heads/main' || assertion.workflow_ref == 'Kevinni05/radiotechwebapp/.github/workflows/availability-monitor.yml@refs/heads/main')"
if run(['iam', 'workload-identity-pools', 'providers', 'describe', 'github', '--workload-identity-pool=' + pool, '--location=global', '--project=' + project, '--format=value(name)'], True) is None:
    run(['iam', 'workload-identity-pools', 'providers', 'create-oidc', 'github', '--workload-identity-pool=' + pool,
        '--location=global', '--project=' + project, '--issuer-uri=https://token.actions.githubusercontent.com',
        '--attribute-mapping=google.subject=assertion.sub,attribute.repository_id=assertion.repository_id', '--attribute-condition=' + condition])
run(['iam', 'service-accounts', 'add-iam-policy-binding', account, '--project=' + project,
    '--member=principalSet://iam.googleapis.com/projects/' + number + '/locations/global/workloadIdentityPools/' + pool + '/attribute.repository_id/1402944403',
    '--role=roles/iam.workloadIdentityUser', '--format=value(etag)'])
for name, value in [('RADIOTECH_WORKLOAD_PROVIDER', provider), ('RADIOTECH_BACKUP_IDENTITY', account), ('FIREBASE_PROJECT_ID', project)]:
    try:
        previous = github('actions/variables/' + name)
        if name == 'FIREBASE_PROJECT_ID': assert previous['value'] in ['', project], 'Repository points to a different Firebase project.'
        github('actions/variables/' + name, {'name': name, 'value': value}, 'PATCH')
    except urllib.error.HTTPError as error:
        if error.code != 404: raise
        github('actions/variables', {'name': name, 'value': value}, 'POST')
print(json.dumps({'configured': True, 'identity': account, 'privateKeyCreated': False, 'privilegedAuthPermissions': False,
    'allowedWorkflows': ['cloud-backup.yml', 'availability-monitor.yml'], 'allowedBranch': 'main'}))
