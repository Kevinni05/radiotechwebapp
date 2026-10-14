"""Review or apply the existing runtime's FCM permission and RTDB URL.

Default is read-only. No resources or billing are created.
With --apply the current runtime may resume its existing notification outbox.
"""
import argparse
import json
import subprocess
from pathlib import Path
from datetime import datetime, timezone

PROJECT = 'gestionale-radio'
SERVICE = 'radiotech-web'
REGION = 'europe-west1'
RUNTIME = f'radiotech-runtime@{PROJECT}.iam.gserviceaccount.com'
ROLE = 'roles/firebasecloudmessaging.admin'
DATABASE = 'https://gestionale-radio-default-rtdb.europe-west1.firebasedatabase.app'

def gcloud(*args):
    result = subprocess.run(['gcloud.cmd', *args, '--project=' + PROJECT, '--format=json'],
                            capture_output=True, text=True, check=True)
    return json.loads(result.stdout or '{}')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--evidence', type=Path)
    args = parser.parse_args()
    policy = gcloud('projects', 'get-iam-policy', PROJECT)
    service = gcloud('run', 'services', 'describe', SERVICE, '--region=' + REGION)
    spec = service['spec']['template']['spec']
    if spec.get('serviceAccountName') != RUNTIME:
        raise RuntimeError('Unexpected runtime identity; no changes applied.')
    env = {item['name']: item.get('value') for item in spec['containers'][0].get('env', [])}
    previous_url = env.get('FIREBASE_DATABASE_URL')
    if previous_url not in (None, '', DATABASE):
        raise RuntimeError('Unexpected existing database URL; no changes applied.')
    had_role = any(binding.get('role') == ROLE and not binding.get('condition')
                   and f'serviceAccount:{RUNTIME}' in binding.get('members', [])
                   for binding in policy.get('bindings', []))
    evidence = {'timestamp': datetime.now(timezone.utc).isoformat(), 'project': PROJECT,
                'runtime': RUNTIME, 'previousRevision': service['status'].get('latestReadyRevisionName'),
                'hadMessagingPermission': had_role, 'previousDatabaseUrl': previous_url,
                'plannedRole': ROLE, 'plannedDatabaseUrl': DATABASE, 'applied': False}
    if args.apply:
        if args.evidence is None:
            raise ValueError('--apply requires --evidence for the rollback baseline.')
        args.evidence.parent.mkdir(parents=True, exist_ok=True)
        args.evidence.write_text(json.dumps(evidence, indent=2), encoding='utf-8')
        if previous_url != DATABASE:
            gcloud('run', 'services', 'update', SERVICE, '--region=' + REGION,
                   '--update-env-vars=FIREBASE_DATABASE_URL=' + DATABASE, '--quiet')
        if not had_role:
            gcloud('projects', 'add-iam-policy-binding', PROJECT,
                   '--member=serviceAccount:' + RUNTIME, '--role=' + ROLE, '--condition=None', '--quiet')
        evidence['applied'] = True
        evidence['readyRevision'] = gcloud('run', 'services', 'describe', SERVICE,
                                           '--region=' + REGION)['status'].get('latestReadyRevisionName')
        args.evidence.write_text(json.dumps(evidence, indent=2), encoding='utf-8')
    print(json.dumps(evidence, indent=2))

if __name__ == '__main__':
    main()
