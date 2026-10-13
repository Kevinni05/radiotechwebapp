"""Owner workstation launcher. Credentials and setup codes never enter command output."""
import os
import json
import subprocess
import sys
from pathlib import Path

private = Path.home() / '.radiotech/identity'
environment = dict(os.environ)
environment.update(json.loads((private / 'authority-env.json').read_text()))
environment['IDENTITY_APP_SECRET'] = (private / 'app-secret.txt').read_text().strip()
environment['GOOGLE_APPLICATION_CREDENTIALS'] = str(Path.home() / '.radiotech/cloud/cli-adc.json')
root = Path(__file__).resolve().parent.parent
result = subprocess.run(['node', 'src/admin.js', *sys.argv[1:]], cwd=root / 'identity-service', env=environment)
raise SystemExit(result.returncode)
