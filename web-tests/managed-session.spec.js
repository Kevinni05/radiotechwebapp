import {test,expect} from '@playwright/test';
import {session} from './fixtures/control-room-session.js';

test('restoring a managed session removes legacy credentials from both product stores',async({page})=>{
 await page.addInitScript(()=>{
  for(const storage of [localStorage,sessionStorage]) {
   storage.setItem('radiotech_control_token','obsolete-id');
   storage.setItem('radiotech_control_refresh','obsolete-refresh');
   storage.setItem('radiotech_control_user','{"tenantId":"obsolete-tenant"}');
  }
 });
 await session(page);
 expect(await page.evaluate(()=>[localStorage,sessionStorage].every(storage=>
  ['radiotech_control_token','radiotech_control_refresh','radiotech_control_user'].every(key=>storage.getItem(key)===null)))).toBe(true);
 await page.reload();
 await expect(page.locator('#app')).toHaveClass(/ready/);
 await expect(page.locator('#profileEmail')).toHaveText('admin@example.test');
});

test('an SDK session-end event clears the active workspace and requires login',async({page})=>{
 await session(page);
 await page.locator('#sessionNotesEditor').fill('Account-private draft');
 await page.evaluate(()=>window.dispatchEvent(new Event('radiotech:auth-ended')));
 await expect(page.locator('#loginScreen')).toBeVisible();
 await expect(page.locator('#app')).not.toHaveClass(/ready/);
 await expect(page.locator('#sessionNotesEditor')).toHaveValue('');
});
