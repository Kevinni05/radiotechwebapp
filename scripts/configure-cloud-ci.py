"""Configure private backup/signing secrets; values never enter command arguments or output.

Requires PyNaCl and a cached git credential with Actions secret permissions.
Run locally as the repository owner. No access token is persisted to disk.
"""
import base64
import json
import os
from pathlib import Path
import subprocess
import urllib.request
import urllib.error
from nacl.public import PublicKey, SealedBox

root = Path(__file__).resolve().parent.parent

def github_credentials(repository):
    result = subprocess.run(['git', 'credential', 'fill'], input=f'protocol=https\nhost=github.com\npath={repository}.git\n\n', cwd=root, text=True, capture_output=True, check=True)
    values = dict(line.split('=', 1) for line in result.stdout.splitlines() if '=' in line)
    if not values.get('password'): raise RuntimeError('A GitHub credential with secret permissions is required.')
    return values['password']

def api(repository, token, suffix, payload=None):
    request = urllib.request.Request(f'https://api.github.com/repos/{repository}/{suffix}', data=None if payload is None else json.dumps(payload).encode(), method='GET' if payload is None else 'PUT', headers={'Authorization': f'Bearer {token}', 'Accept': 'application/vnd.github+json', 'Content-Type': 'application/json', 'User-Agent': 'RadioTech-CI-configuration'})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            data = response.read()
            return json.loads(data) if data else {}
    except urllib.error.HTTPError as failure:
        raise RuntimeError(f'GitHub configuration failed with HTTP {failure.code}; check repository/Actions permissions.') from None

def configure(repository, secrets):
    token = github_credentials(repository)
    key = api(repository, token, 'actions/secrets/public-key')
    cipher = SealedBox(PublicKey(base64.b64decode(key['key'])))
    for name, value in secrets.items():
        encrypted = base64.b64encode(cipher.encrypt(value.encode())).decode()
        api(repository, token, f'actions/secrets/{name}', {'key_id': key['key_id'], 'encrypted_value': encrypted})
        print(f'{repository}: configured {name}')

def main():
    runtime = json.loads((root / '.dist/secrets/local-runtime.json').read_text())
    account = Path(runtime['serviceAccountPath']).read_text()
    if json.loads(account)['project_id'] != 'gestionale-radio': raise RuntimeError('Unexpected Firebase project.')
    private = Path.home() / '.radiotech'
    key_path = private / 'backups/cloud-backup.key'
    key_path.parent.mkdir(parents=True, exist_ok=True)
    if not key_path.exists(): key_path.write_bytes(os.urandom(32))
    if len(key_path.read_bytes()) != 32: raise RuntimeError('Invalid backup key; do not replace an existing key.')
    tenants = json.loads((root / '.dist/operations-monitor-tenants.json').read_text())
    if not isinstance(tenants, list) or not tenants or not all(isinstance(item, str) and item.strip() and '/' not in item for item in tenants): raise RuntimeError('Configure the monitored company IDs before enabling scheduled checks.')
    configure('Kevinni05/radiotechwebapp', {'FIREBASE_BACKUP_SERVICE_ACCOUNT': account, 'FIREBASE_BACKUP_KEY_BASE64': base64.b64encode(key_path.read_bytes()).decode(), 'RADIOTECH_MONITOR_TENANT_IDS': json.dumps(tenants)})
    properties = dict(line.split('=', 1) for line in (private / 'signing/key.properties').read_text().splitlines() if '=' in line)
    certificate = json.loads((private / 'signing/certificate.json').read_text())
    symbols_key = private / 'signing/symbols.key'
    if not symbols_key.exists(): symbols_key.write_bytes(os.urandom(32))
    if len(symbols_key.read_bytes()) != 32: raise RuntimeError('Invalid symbols encryption key; preserve the existing key.')
    configure('Kevinni05/gestionale_radio', {'ANDROID_KEYSTORE_BASE64': base64.b64encode(Path(properties['storeFile']).read_bytes()).decode(),
        'ANDROID_KEYSTORE_PASSWORD': properties['storePassword'], 'ANDROID_KEY_ALIAS': properties['keyAlias'], 'ANDROID_KEY_PASSWORD': properties['keyPassword'], 'ANDROID_SIGNING_SHA256': certificate['sha256'], 'ANDROID_SYMBOLS_KEY_BASE64': base64.b64encode(symbols_key.read_bytes()).decode()})

if __name__ == '__main__': main()
