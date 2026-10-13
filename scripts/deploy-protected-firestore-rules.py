"""Publish reviewed client rules without touching operational data or printing credentials."""
import json
import subprocess
import urllib.request
from pathlib import Path

project = 'gestionale-radio'
credential = subprocess.run(['gcloud.cmd', 'auth', 'print-access-token'], capture_output=True, text=True, check=True).stdout.strip()
base = 'https://firebaserules.googleapis.com/v1/'
def request(path, body=None, method=None):
    req = urllib.request.Request(base + path, data=json.dumps(body).encode() if body is not None else None, method=method,
        headers={'Authorization': 'Bearer ' + credential, 'Content-Type': 'application/json', 'X-Goog-User-Project': project})
    with urllib.request.urlopen(req, timeout=25) as response: return json.load(response)
release = 'projects/' + project + '/releases/cloud.firestore'
previous = request(release)
backup = Path.home() / '.radiotech/identity/firestore-rules-before-security.json'
if not backup.exists(): backup.write_text(json.dumps(previous))
source = (Path(__file__).resolve().parent.parent / 'firestore.rules').read_text(encoding='utf-8')
assert 'function manager() { return false; }' in source and 'allow read, write: if false;' in source
ruleset = request('projects/' + project + '/rulesets', {'source': {'files': [{'name': 'firestore.rules', 'content': source}]}}, 'POST')
result = request(release, {'release': {'name': release, 'rulesetName': ruleset['name']}, 'updateMask': 'rulesetName'}, 'PATCH')
print(json.dumps({'rulesPublished': True, 'projectId': project, 'ruleset': result['rulesetName'], 'businessDocumentsChanged': False}))
