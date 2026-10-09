const {spawn} = require('node:child_process');
const path = require('node:path');
const archive = process.env.RADIOTECH_BACKUP_ARCHIVE, key = process.env.RADIOTECH_BACKUP_KEY_FILE;
if (!archive || !key || !process.env.FIRESTORE_EMULATOR_HOST || !process.env.FIREBASE_AUTH_EMULATOR_HOST) throw new Error('Archive, key and Firestore/Auth emulators required.');
const windows = process.platform === 'win32';
const gradle = windows ? 'gradlew.bat' : 'bash';
const args = ['backupDrill', '--console=plain', '-PbackupArchive=' + archive, '-PbackupKey=' + key];
const child = spawn(gradle, windows ? args : ['gradlew', ...args], {
  cwd: path.resolve(__dirname, '..'), shell: process.platform === 'win32', stdio: 'inherit',
  env: {...process.env, FIREBASE_PROJECT_ID: 'demo-radiotech-backup'},
});
child.on('error', error => {process.stderr.write(`Could not start restore verification: ${error.code || 'PROCESS_START_FAILED'}\n`); process.exitCode = 1;});
child.on('exit', code => {process.exitCode = code ?? 1;});
