// Grants only Monitoring Viewer to the existing server service account.
// Uses an existing local Firebase CLI login; credentials never enter output.
const fs = require('node:fs'), path = require('node:path'), {execFileSync} = require('node:child_process');
async function main() {
  const runtimePath = path.resolve('.dist/secrets/local-runtime.json');
  const credentialPath = process.env.FIREBASE_SERVICE_ACCOUNT_PATH || JSON.parse(fs.readFileSync(runtimePath, 'utf8')).serviceAccountPath;
  const account = JSON.parse(fs.readFileSync(credentialPath, 'utf8'));
  if (account.project_id !== 'gestionale-radio' || !account.client_email?.endsWith('@gestionale-radio.iam.gserviceaccount.com')) throw new Error('Unexpected service account project.');
  const globalModules = execFileSync(process.platform === 'win32' ? 'npm.cmd' : 'npm', ['root','-g'], {encoding:'utf8', shell:process.platform === 'win32'}).trim();
  const auth = require(path.join(globalModules, 'firebase-tools/lib/auth.js'));
  const login = auth.getGlobalDefaultAccount();
  if (!login?.tokens?.refresh_token) throw new Error('An existing project-owner Firebase CLI login is required.');
  const refreshed = await auth.getAccessToken(login.tokens.refresh_token, ['https://www.googleapis.com/auth/cloud-platform']);
  const token = refreshed.access_token;
  if (!token) throw new Error('Could not obtain a local project-owner token.');
  async function request(url, body) {
    const response = await fetch(url, {method:body ? 'POST' : 'GET', headers:{Authorization:`Bearer ${token}`, 'Content-Type':'application/json'}, ...(body ? {body:JSON.stringify(body)} : {}), signal:AbortSignal.timeout(30000)});
    if (!response.ok) throw new Error(`Monitoring configuration HTTP ${response.status}; project IAM permissions are required.`);
    return response.json();
  }
  const iam = 'https://cloudresourcemanager.googleapis.com/v1/projects/gestionale-radio';
  const policy = await request(`${iam}:getIamPolicy`, {options:{requestedPolicyVersion:3}});
  const member = `serviceAccount:${account.client_email}`;
  let binding = (policy.bindings || []).find(item => item.role === 'roles/monitoring.viewer' && !item.condition);
  if (!binding?.members?.includes(member)) {
    policy.bindings ||= [];
    if (!binding) {binding = {role:'roles/monitoring.viewer', members:[]}; policy.bindings.push(binding);}
    binding.members.push(member);
    await request(`${iam}:setIamPolicy`, {policy});
    console.log('Existing server service account granted Monitoring Viewer; all other policy bindings preserved.');
  } else console.log('Monitoring Viewer is already configured.');
  const serviceUrl = 'https://serviceusage.googleapis.com/v1/projects/gestionale-radio/services/monitoring.googleapis.com';
  const service = await request(serviceUrl);
  if (service.state !== 'ENABLED') {await request(`${serviceUrl}:enable`, {}); console.log('Cloud Monitoring API enablement requested.');}
  else console.log('Cloud Monitoring API already enabled.');
}
main().catch(error => {console.error(error.message); process.exitCode=1;});
