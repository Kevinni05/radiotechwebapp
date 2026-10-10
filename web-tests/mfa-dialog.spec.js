import {test, expect} from '@playwright/test';

async function open(page, {password = false, fail = false} = {}) {
  await page.route('**/api/v1/auth/public-config', route => route.fulfill({json:{enabled:false}}));
  await page.goto('/');
  await page.evaluate(({password, fail}) => {
    window.mfaAttempts = 0;
    window.mfaResult = null;
    const trigger = document.getElementById('loginButton'); trigger.focus();
    window.RadioTechMfaDialog({title:'Verifica di sicurezza', description:'Prova di verifica', password,
      label:password ? 'Password' : 'Codice a 6 cifre',
      setup:container => { const key=document.createElement('code');key.textContent='TEST-SECRET';container.append(key); },
      submit:async value => { window.mfaAttempts++; if(fail && window.mfaAttempts===1)throw new Error('Codice non valido. Riprova.');return value.length; }
    }).then(result => window.mfaResult=result).catch(error => window.mfaResult=error.name);
  }, {password, fail});
  await expect(page.getByRole('dialog')).toBeVisible();
}

test('MFA validates a code, keeps retry available and clears secrets after success', async ({page}) => {
  await open(page, {fail:true});
  const input = page.getByLabel('Codice a 6 cifre');
  await expect(input).toBeFocused();
  await input.fill('123'); await input.press('Enter');
  expect(await page.evaluate(() => window.mfaAttempts)).toBe(0);
  await input.fill('123456'); await input.press('Enter');
  await expect(page.getByRole('status').filter({hasText:'Codice non valido'})).toBeVisible();
  await expect(input).toHaveValue('');
  await input.fill('654321'); await input.press('Enter');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(await page.evaluate(() => window.mfaResult)).toBe(6);
  await expect(page.locator('body')).not.toContainText('TEST-SECRET');
  await expect(page.locator('#loginButton')).toBeFocused();
});

test('MFA cancellation never submits and restores keyboard focus', async ({page}) => {
  await open(page, {password:true});
  await page.getByLabel('Password', {exact:true}).fill('sample-password');
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(await page.evaluate(() => [window.mfaResult,window.mfaAttempts])).toEqual(['AbortError',0]);
  await expect(page.locator('body')).not.toContainText('TEST-SECRET');
  await expect(page.locator('#loginButton')).toBeFocused();
});

test('MFA recovery and setup fit a narrow viewport without native prompts', async ({page}) => {
  await page.setViewportSize({width:320,height:568});
  page.on('dialog', () => { throw new Error('Unexpected native authentication prompt'); });
  await open(page);
  await page.getByText('Non riesco ad accedere', {exact:true}).click();
  await expect(page.getByRole('dialog')).toContainText('contatta il responsabile aziendale');
  expect(await page.getByRole('dialog').evaluate(el => el.scrollWidth<=el.clientWidth)).toBe(true);
  await page.getByRole('button', {name:'Annulla',exact:true}).click();
});
