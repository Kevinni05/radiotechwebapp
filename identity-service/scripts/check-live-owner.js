// Read-only live browser check. Never record traces, URLs, tokens or bootstrap codes.
import { chromium } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import { homedir } from 'node:os';
const proof = execFileSync('gcloud.cmd', ['auth', 'print-identity-token', '--account=kcagnazzo@gmail.com', '--quiet'], { encoding: 'utf8', shell: true }).trim();
const setup = (await readFile(`${homedir()}/.radiotech/identity/owner-setup.txt`, 'utf8')).trim();
const browser = await chromium.launch();
try {
  const page = await browser.newPage();
  await page.goto(`https://radiotech-identity-654979616749.europe-west1.run.app/?intent=owner#owner=${encodeURIComponent(proof)}&setup=${encodeURIComponent(setup)}`);
  await page.waitForFunction(() => document.querySelector('#status')?.textContent !== 'Caricamento...');
  const result = await page.evaluate(() => ({ status: document.querySelector('#status').textContent,
    setupVisible: !document.querySelector('#setupForm').hidden, loginVisible: !document.querySelector('#loginPanel').hidden,
    fragmentRemoved: !location.hash, setupPresent: !!document.querySelector('#setupCode').value }));
  process.stdout.write(JSON.stringify(result) + '\n');
} finally { await browser.close(); }
