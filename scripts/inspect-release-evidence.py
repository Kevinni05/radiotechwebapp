"""Read-only GitHub checks and backup evidence; credentials never leave api.github.com."""
import json
import subprocess
import urllib.error
import urllib.request
from urllib.parse import urlparse

class SameOriginRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, newurl):
        if urlparse(newurl).hostname != 'api.github.com':
            raise RuntimeError('Unexpected GitHub redirect.')
        return super().redirect_request(request, response, code, message, headers, newurl)

def main():
    # Reuse the existing repository credential helper, without logging its output.
    credential = subprocess.run(['git', 'credential', 'fill'], input='protocol=https\nhost=github.com\n\n',
                                capture_output=True, text=True, check=True)
    fields = dict(line.split('=', 1) for line in credential.stdout.splitlines() if '=' in line)
    token = fields.get('password')
    if not token:
        raise RuntimeError('Existing GitHub credential required.')
    opener = urllib.request.build_opener(SameOriginRedirect())
    def read(path):
        request = urllib.request.Request('https://api.github.com/repos/Kevinni05/' + path,
            headers={'Authorization': 'Bearer ' + token, 'Accept': 'application/vnd.github+json'})
        with opener.open(request, timeout=30) as response:
            return json.load(response)
    for workflow in ['cloud-backup.yml', 'availability-monitor.yml']:
        runs = read('radiotechwebapp/actions/workflows/' + workflow + '/runs?per_page=3')['workflow_runs']
        print(json.dumps({'workflow': workflow, 'runs': [
            {key: run.get(key) for key in ['id', 'status', 'conclusion', 'created_at', 'updated_at', 'html_url', 'head_sha']}
            for run in runs]}))
    job = read('gestionale_radio/actions/jobs/115292011396')
    print(json.dumps({'nativeJob': {key: job.get(key) for key in ['id', 'status', 'conclusion', 'started_at', 'completed_at']},
        'steps': [{key: step.get(key) for key in ['name', 'conclusion', 'started_at', 'completed_at']} for step in job.get('steps', [])]}))

if __name__ == '__main__':
    try:
        main()
    except urllib.error.HTTPError as error:
        print(json.dumps({'error': 'GitHub read failed', 'status': error.code}))
        raise SystemExit(1)
