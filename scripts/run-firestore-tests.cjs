const path = require('node:path');
const { spawn } = require('node:child_process');

const tests = [
  'com.radiotech.radiotech_backend.service.ProEmulatorTest',
  'com.radiotech.radiotech_backend.service.NotificationHistoryEmulatorTest',
  'com.radiotech.radiotech_backend.service.WorkforceEmulatorTest',
  'com.radiotech.radiotech_backend.service.AlertEngineFirestoreEmulatorTest',
  'com.radiotech.radiotech_backend.service.TaskIdempotencyEmulatorTest',
  'com.radiotech.radiotech_backend.service.TechnicianSkillServiceEmulatorTest',
  'com.radiotech.radiotech_backend.service.RicambioServiceAtomicityEmulatorTest',
  'com.radiotech.radiotech_backend.service.MaintenanceReportFirestoreEmulatorTest',
  'com.radiotech.radiotech_backend.service.ManutenzioneServiceAlertTest',
  'com.radiotech.radiotech_backend.service.OperatorFieldWorkflowFirestoreEmulatorTest',
  'com.radiotech.radiotech_backend.service.IncidentServiceEmulatorTest',
  'com.radiotech.radiotech_backend.service.OperatorServiceTenantMutationEmulatorTest',
  'com.radiotech.radiotech_backend.service.OperatorServiceQrTenantEmulatorTest',
  'com.radiotech.radiotech_backend.service.OperatorProvisioningEmulatorTest',
  'com.radiotech.radiotech_backend.security.FirebaseAuthenticationAuthEmulatorTest',
  'com.radiotech.radiotech_backend.service.OperatorApprovalAuthEmulatorTest',
  'com.radiotech.radiotech_backend.service.AntennaHistoryEmulatorTest',
];
const gradle = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';
const args = ['test', '--rerun-tasks', '--no-daemon', '--console=plain'];
for (const test of tests) args.push('--tests', test);

const child = spawn(gradle, args, {
  cwd: path.resolve(__dirname, '..'),
  stdio: 'inherit',
  shell: process.platform === 'win32',
});
child.on('error', (error) => {
  process.stderr.write(`Could not start Gradle wrapper: ${error.message}\n`);
  process.exitCode = 1;
});
child.on('exit', (code, signal) => {
  process.exitCode = code ?? (signal ? 1 : 0);
});
