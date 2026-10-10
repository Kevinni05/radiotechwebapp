'use strict';
const fs = require('node:fs');
const path = require('node:path');

function validateBackupConfiguration(env) {
    if (!env.BACKUP_ACCOUNT_JSON || !env.BACKUP_KEY_BASE64) {
        throw new Error('Configure FIREBASE_BACKUP_SERVICE_ACCOUNT and FIREBASE_BACKUP_KEY_BASE64.');
    }
    let account;
    try { account = JSON.parse(env.BACKUP_ACCOUNT_JSON); }
    catch { throw new Error('Backup service account JSON is invalid.'); }
    if (!account || account.type !== 'service_account' ||
        typeof account.project_id !== 'string' ||
        !/^[a-z][a-z0-9-]{4,61}[a-z0-9]$/.test(account.project_id) ||
        !account.private_key || !account.client_email) {
        throw new Error('A valid dedicated backup service account is required.');
    }
    // The approved account is authoritative when the optional repository variable is absent.
    // An explicitly configured project is always checked, never silently replaced.
    const projectId = (env.FIREBASE_PROJECT_ID || '').trim() || account.project_id;
    if (projectId !== account.project_id) {
        throw new Error('Backup service account does not match FIREBASE_PROJECT_ID.');
    }
    const encodedKey = env.BACKUP_KEY_BASE64.trim();
    const key = Buffer.from(encodedKey, 'base64');
    if (key.length !== 32 || key.toString('base64') !== encodedKey) {
        throw new Error('FIREBASE_BACKUP_KEY_BASE64 must encode exactly 32 bytes.');
    }
    return { projectId, key };
}

function prepare(env) {
    const { projectId, key } = validateBackupConfiguration(env);
    if (!env.RUNNER_TEMP || !env.GITHUB_ENV) throw new Error('GitHub runner paths are required.');
    const credential = path.join(env.RUNNER_TEMP, 'backup-account.json');
    const keyFile = path.join(env.RUNNER_TEMP, 'backup.key');
    fs.writeFileSync(credential, env.BACKUP_ACCOUNT_JSON, { mode: 0o600 });
    fs.writeFileSync(keyFile, key, { mode: 0o600 });
    fs.appendFileSync(env.GITHUB_ENV,
        `FIREBASE_PROJECT_ID=${projectId}\nFIREBASE_SERVICE_ACCOUNT_PATH=${credential}\nRADIOTECH_BACKUP_KEY_FILE=${keyFile}\n`);
}

if (require.main === module) prepare(process.env);
module.exports = { validateBackupConfiguration, prepare };
