"""Retire the legacy Render/CI administrator only after the keyless replacement is healthy."""
import argparse
import json
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--apply', action='store_true')
args = parser.parse_args()
legacy_file = Path.home() / 'Downloads/gestionale-radio-firebase-adminsdk-fbsvc-4a6a291aa0.json'
legacy = json.loads(legacy_file.read_text())
assert legacy['project_id'] == 'gestionale-radio'
account = legacy['client_email']
assert account.startswith('firebase-adminsdk-') and account.endswith('@gestionale-radio.iam.gserviceaccount.com')
def run(command):
    result = subprocess.run(['gcloud.cmd', *command, '--quiet'], capture_output=True, text=True)
    if result.returncode: raise RuntimeError(result.stderr.strip())
    return result.stdout.strip()
policy = json.loads(run(['projects', 'get-iam-policy', 'gestionale-radio', '--format=json']))
member = 'serviceAccount:' + account
roles = [binding['role'] for binding in policy.get('bindings', []) if member in binding.get('members', [])]
keys = json.loads(run(['iam', 'service-accounts', 'keys', 'list', '--iam-account=' + account,
    '--project=gestionale-radio', '--managed-by=user', '--format=json']))
if args.apply:
    # Removing IAM bindings also invalidates administrative use of previously issued access tokens.
    for role in roles:
        run(['projects', 'remove-iam-policy-binding', 'gestionale-radio', '--member=' + member,
            '--role=' + role, '--all', '--format=value(etag)'])
    for key in keys:
        if not key.get('disabled'):
            run(['iam', 'service-accounts', 'keys', 'disable', key['name'].split('/')[-1], '--iam-account=' + account, '--project=gestionale-radio'])
    run(['iam', 'service-accounts', 'disable', account, '--project=gestionale-radio'])
    remaining = json.loads(run(['projects', 'get-iam-policy', 'gestionale-radio', '--format=json']))
    assert not any(member in b.get('members', []) for b in remaining.get('bindings', []))
    disabled = json.loads(run(['iam', 'service-accounts', 'describe', account, '--project=gestionale-radio', '--format=json']))
    assert disabled.get('disabled') is True
print(json.dumps({'dryRun': not args.apply, 'legacyIdentity': account, 'removedProjectRoles': roles,
    'userManagedKeys': len(keys), 'administratorRetired': args.apply, 'nativeUserAccountsChanged': False}))
