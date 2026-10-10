const { test } = require('node:test');
const assert = require('node:assert/strict');
const { validateBackupConfiguration } = require('./prepare-backup-credentials.cjs');

function configuration(projectId = 'buyer-project') {
    return {
        BACKUP_ACCOUNT_JSON: JSON.stringify({
            type: 'service_account', project_id: projectId,
            private_key: 'synthetic-test-value', client_email: 'backup@example.invalid',
        }),
        BACKUP_KEY_BASE64: Buffer.alloc(32, 7).toString('base64'),
    };
}

test('missing repository project variable uses the dedicated approved account project', () => {
    assert.equal(validateBackupConfiguration(configuration()).projectId, 'buyer-project');
});
test('matching explicit project succeeds and mismatched project fails closed', () => {
    assert.equal(validateBackupConfiguration({ ...configuration(), FIREBASE_PROJECT_ID: 'buyer-project' }).projectId, 'buyer-project');
    assert.throws(() => validateBackupConfiguration({ ...configuration(), FIREBASE_PROJECT_ID: 'another-project' }), /does not match/);
});
test('invalid JSON never includes credential content in the error', () => {
    assert.throws(() => validateBackupConfiguration({ ...configuration(), BACKUP_ACCOUNT_JSON: 'SECRET-invalid-json' }),
        error => error.message === 'Backup service account JSON is invalid.');
});
test('missing account, invalid project and noncanonical or short keys are rejected', () => {
    assert.throws(() => validateBackupConfiguration({ ...configuration(), BACKUP_ACCOUNT_JSON: '' }));
    assert.throws(() => validateBackupConfiguration(configuration('bad\nproject')));
    assert.throws(() => validateBackupConfiguration({ ...configuration(), BACKUP_KEY_BASE64: Buffer.alloc(31).toString('base64') }));
    assert.throws(() => validateBackupConfiguration({ ...configuration(), BACKUP_KEY_BASE64: configuration().BACKUP_KEY_BASE64 + '!' }));
});
