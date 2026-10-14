"""Prepare a keyless Cloud Run backend. Does not switch the public Render service."""
import argparse
import json
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--image', required=True)
args = parser.parse_args()
project = 'gestionale-radio'
if not args.image.startswith(f'europe-west1-docker.pkg.dev/{project}/radiotech/web:'):
    raise ValueError('Use the reviewed web image in its operational project.')
private = Path.home() / '.radiotech/identity'
settings = json.loads((Path.home() / '.radiotech/cloud/acceptance-settings.json').read_text())
env = {'SPRING_PROFILES_ACTIVE': 'production', 'FIREBASE_PROJECT_ID': project,
    'FIREBASE_DATABASE_URL': 'https://gestionale-radio-default-rtdb.europe-west1.firebasedatabase.app',
    'RADIOTECH_FIREBASE_WEB_API_KEY': settings['webApiKey'],
    'RADIOTECH_IDENTITY_REQUIRED': 'true',
    'RADIOTECH_IDENTITY_ORIGIN': 'https://radiotech-identity-654979616749.europe-west1.run.app',
    'RADIOTECH_CAPO_EMAIL': 'capo.radiotechwebapp@gmail.com',
    'RADIOTECH_PUBLIC_BASE_URL': 'https://radiotechwebapp.onrender.com',
    'RADIOTECH_CORS_ORIGINS': 'https://radiotechwebapp.onrender.com',
    'RADIOTECH_FIRESTORE_SEED': 'false', 'RADIOTECH_TENANT_MIGRATION_ENABLED': 'false',
    'GOOGLE_CLOUD_QUOTA_PROJECT': project, 'JAVA_TOOL_OPTIONS': '-XX:MaxRAMPercentage=70.0'}
env_file = private / 'web-env.json'
env_file.write_text(json.dumps(env))
result = subprocess.run(['gcloud.cmd', 'run', 'deploy', 'radiotech-web', '--project=' + project,
    '--region=europe-west1', '--image=' + args.image,
    '--service-account=radiotech-runtime@' + project + '.iam.gserviceaccount.com',
    '--env-vars-file=' + str(env_file),
    '--set-secrets=RADIOTECH_IDENTITY_APP_SECRET=radiotech-app-secret:1,RADIOTECH_REPORT_VERIFICATION_SECRET=radiotech-report-secret:1,RADIOTECH_TENANT_INVITE_SECRET=radiotech-tenant-secret:1',
    '--allow-unauthenticated', '--min=0', '--max=2', '--memory=1Gi', '--cpu=1', '--concurrency=40',
    '--timeout=180', '--port=8080', '--quiet', '--format=value(status.url)'], capture_output=True, text=True)
if result.returncode: raise RuntimeError(result.stderr.strip())
print(json.dumps({'deployed': True, 'service': 'radiotech-web', 'url': result.stdout.strip(),
    'publicRenderChanged': False, 'privateAdministrativeKey': False}))
