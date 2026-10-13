"""Deploy the isolated authority with keyless IAM and file-based configuration. No secret output."""
import argparse
import json
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--image', required=True)
args = parser.parse_args()
project = 'radiotech-id-1051055442339'
if not args.image.startswith(f'europe-west1-docker.pkg.dev/{project}/radiotech/identity:'):
    raise ValueError('Use the reviewed image in the isolated project.')
private = Path.home() / '.radiotech' / 'identity'
settings = json.loads((Path.home() / '.radiotech/cloud/acceptance-settings.json').read_text())
env = {
    'IDENTITY_ORIGIN': 'https://radiotech-identity-654979616749.europe-west1.run.app',
    'IDENTITY_APP_ORIGIN': 'https://radiotechwebapp.onrender.com',
    'IDENTITY_FIREBASE_PROJECT': 'gestionale-radio',
    'IDENTITY_REGISTRY_PROJECT': project,
    'IDENTITY_FIREBASE_API_KEY': settings['webApiKey'],
    'IDENTITY_OWNER_EMAIL': 'kcagnazzo@gmail.com',
    'IDENTITY_OWNER_GOOGLE_SUB': '101939638982626665189',
    'IDENTITY_OWNER_GOOGLE_AUDIENCE': '32555940559.apps.googleusercontent.com',
    'IDENTITY_MAIL_PROVIDER': 'firebase',
    'GOOGLE_CLOUD_QUOTA_PROJECT': 'gestionale-radio',
}
env_file = private / 'authority-env.json'
env_file.write_text(json.dumps(env))
result = subprocess.run(['gcloud.cmd', 'run', 'deploy', 'radiotech-identity', '--project=' + project,
    '--region=europe-west1', '--image=' + args.image, '--service-account=radiotech-identity@' + project + '.iam.gserviceaccount.com',
    '--env-vars-file=' + str(env_file), '--set-secrets=IDENTITY_APP_SECRET=radiotech-app-secret:1',
    '--allow-unauthenticated', '--min=0', '--max=3', '--memory=512Mi', '--cpu=1', '--concurrency=40',
    '--timeout=60', '--port=8080', '--quiet', '--format=value(status.url)'], capture_output=True, text=True)
if result.returncode:
    raise RuntimeError(result.stderr.strip())
print(json.dumps({'deployed': True, 'service': 'radiotech-identity', 'url': result.stdout.strip(),
    'ownerEmail': env['IDENTITY_OWNER_EMAIL'], 'privilegedAccountsChanged': False}))
