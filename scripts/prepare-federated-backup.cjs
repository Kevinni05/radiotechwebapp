'use strict';
const fs = require('node:fs');
const path = require('node:path');
const env = process.env;
if (!env.GOOGLE_APPLICATION_CREDENTIALS || !env.FIREBASE_PROJECT_ID || !env.RUNNER_TEMP || !env.GITHUB_ENV) {
  throw new Error('Configure workload identity before starting a backup.');
}
const encoded = (env.BACKUP_KEY_BASE64 || '').trim();
const key = Buffer.from(encoded, 'base64');
if (key.length !== 32 || key.toString('base64') !== encoded) throw new Error('The existing backup encryption key must encode exactly 32 bytes.');
const keyFile = path.join(env.RUNNER_TEMP, 'backup.key');
fs.writeFileSync(keyFile, key, { mode: 0o600 });
fs.appendFileSync(env.GITHUB_ENV, `FIREBASE_SERVICE_ACCOUNT_PATH=${env.GOOGLE_APPLICATION_CREDENTIALS}\nRADIOTECH_BACKUP_KEY_FILE=${keyFile}\n`);
