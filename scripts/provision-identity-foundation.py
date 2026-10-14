"""Provision the isolated service identities and stores. Secrets enter gcloud only through files."""
import argparse
import json
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--apply', action='store_true')
args = parser.parse_args()
app_project = 'gestionale-radio'
registry_project = 'radiotech-id-1051055442339'
region = 'europe-west1'
private = Path.home() / '.radiotech' / 'identity'
root = Path(__file__).resolve().parent.parent

def run(arguments, allow_not_found=False):
    result = subprocess.run(['gcloud.cmd', *arguments, '--quiet'], capture_output=True, text=True)
    if result.returncode:
        if allow_not_found and ('NOT_FOUND' in result.stderr or 'does not exist' in result.stderr): return None
        raise RuntimeError(result.stderr.strip())
    return result.stdout.strip()

def account(project, name):
    address = f'{name}@{project}.iam.gserviceaccount.com'
    if run(['iam', 'service-accounts', 'describe', address, '--project=' + project, '--format=value(email)'], True) is None:
        run(['iam', 'service-accounts', 'create', name, '--project=' + project, '--display-name=' + name])
    return address

def role(project, principal, role_name):
    run(['projects', 'add-iam-policy-binding', project, '--member=serviceAccount:' + principal, '--role=' + role_name, '--condition=None', '--format=value(etag)'])

def secret(project, name, file, principal):
    if run(['secrets', 'describe', name, '--project=' + project, '--format=value(name)'], True) is None:
        run(['secrets', 'create', name, '--project=' + project, '--replication-policy=automatic', '--format=value(name)'])
        run(['secrets', 'versions', 'add', name, '--project=' + project, '--data-file=' + str(file), '--format=value(name)'])
    run(['secrets', 'add-iam-policy-binding', name, '--project=' + project, '--member=serviceAccount:' + principal,
         '--role=roles/secretmanager.secretAccessor', '--format=value(etag)'])

if not args.apply:
    print(json.dumps({'apply': False, 'appProject': app_project, 'registryProject': registry_project, 'region': region,
        'runtimeAuthRole': 'roles/firebaseauth.viewer', 'privilegedAuthRole': 'isolated authority only', 'privateRuntimeKeys': False}))
    raise SystemExit(0)

if run(['config', 'get-value', 'account']) != 'kcagnazzo@gmail.com':
    raise RuntimeError('Use the confirmed Google Cloud owner account.')
for file in [private / 'app-secret.txt', root / '.dist/secrets/RADIOTECH_REPORT_VERIFICATION_SECRET.txt', root / '.dist/secrets/RADIOTECH_TENANT_INVITE_SECRET.txt']:
    if not file.is_file() or len(file.read_text().strip()) < 32: raise RuntimeError('Required protected secret file is missing; no replacement keys are generated.')

print('Enabling service APIs', flush=True)
run(['services', 'enable', 'run.googleapis.com', 'cloudbuild.googleapis.com', 'artifactregistry.googleapis.com', 'secretmanager.googleapis.com', 'iamcredentials.googleapis.com', '--project=' + app_project])
authority = account(registry_project, 'radiotech-identity')
signer = account(app_project, 'radiotech-identity-signer')
signing_role = 'radiotechIdentitySignBlob'
existing_role = run(['iam', 'roles', 'describe', signing_role, '--project=' + app_project, '--format=json'], True)
if existing_role is None:
    run(['iam', 'roles', 'create', signing_role, '--project=' + app_project,
         '--title=RadioTech identity token signing', '--permissions=iam.serviceAccounts.signBlob', '--stage=GA'])
elif set(json.loads(existing_role).get('includedPermissions', [])) != {'iam.serviceAccounts.signBlob'}:
    raise RuntimeError('The dedicated signing role has unexpected permissions.')
run(['iam', 'service-accounts', 'add-iam-policy-binding', signer, '--project=' + app_project,
     '--member=serviceAccount:' + authority, '--role=projects/' + app_project + '/roles/' + signing_role, '--format=value(etag)'])
builder = account(registry_project, 'radiotech-image-builder')
runtime = account(app_project, 'radiotech-runtime')
web_builder = account(app_project, 'radiotech-web-builder')
print('Applying independent IAM bindings', flush=True)
role(registry_project, authority, 'roles/datastore.user')
role(app_project, authority, 'roles/firebaseauth.admin')
role(app_project, authority, 'roles/serviceusage.serviceUsageConsumer')
role(app_project, runtime, 'roles/firebaseauth.viewer')
role(app_project, runtime, 'roles/datastore.user')
role(app_project, runtime, 'roles/serviceusage.serviceUsageConsumer')
role(app_project, runtime, 'roles/firebasecloudmessaging.admin')
for project, principal in [(registry_project, builder), (app_project, web_builder)]:
    for permission in ['roles/artifactregistry.writer', 'roles/logging.logWriter', 'roles/storage.objectAdmin']:
        role(project, principal, permission)
run(['iam', 'service-accounts', 'add-iam-policy-binding', authority, '--project=' + registry_project,
     '--member=serviceAccount:' + authority, '--role=roles/iam.serviceAccountTokenCreator', '--format=value(etag)'])
for project in [registry_project, app_project]:
    if run(['artifacts', 'repositories', 'describe', 'radiotech', '--location=' + region, '--project=' + project, '--format=value(name)'], True) is None:
        run(['artifacts', 'repositories', 'create', 'radiotech', '--repository-format=docker', '--location=' + region, '--project=' + project, '--format=value(name)'])
print('Preparing the isolated authorization database', flush=True)
if run(['firestore', 'databases', 'describe', '--project=' + registry_project, '--database=(default)', '--format=value(name)'], True) is None:
    run(['firestore', 'databases', 'create', '--project=' + registry_project, '--database=(default)', '--location=eur3', '--type=firestore-native', '--delete-protection'])
for collection in ['nonces', 'challenges']:
    run(['firestore', 'fields', 'ttls', 'update', 'deleteAt', '--collection-group=' + collection, '--project=' + registry_project, '--enable-ttl', '--format=value(name)'])
print('Writing protected secrets without exposing their values', flush=True)
secret(registry_project, 'radiotech-app-secret', private / 'app-secret.txt', authority)
secret(app_project, 'radiotech-app-secret', private / 'app-secret.txt', runtime)
secret(app_project, 'radiotech-report-secret', root / '.dist/secrets/RADIOTECH_REPORT_VERIFICATION_SECRET.txt', runtime)
secret(app_project, 'radiotech-tenant-secret', root / '.dist/secrets/RADIOTECH_TENANT_INVITE_SECRET.txt', runtime)
print(json.dumps({'configured': True, 'authority': authority, 'runtime': runtime, 'runtimeCanGrantPrivileges': False}))
