import {test,expect} from '@playwright/test';
import {session} from './fixtures/control-room-session.js';

test('failed note storage can be retried without changing the text',async({page})=>{
  await session(page);
  await page.evaluate(()=>{window.noteSet=Storage.prototype.setItem;Storage.prototype.setItem=function(key,value){if(key.startsWith('radiotech_session_notes_v1:'))throw new DOMException('Full','QuotaExceededError');return window.noteSet.call(this,key,value);};});
  await page.locator('#sessionNotesEditor').fill('Priorità intervento');
  await expect(page.locator('#sessionNotesRetry')).toBeVisible();
  await page.evaluate(()=>{Storage.prototype.setItem=window.noteSet;});
  await page.locator('#sessionNotesRetry').click();
  await expect(page.locator('#sessionNotesStatus')).toHaveText('Salvato nella sessione');
  await expect(page.locator('#sessionNotesRetry')).toBeHidden();
  await page.reload();await expect(page.locator('#sessionNotesEditor')).toHaveValue('Priorità intervento');
});

test('command shortcut preserves an active confirmation and its focus',async({page})=>{
  await session(page);await page.locator('#sessionNotesEditor').fill('Priorità');
  await page.locator('#sessionNotesClear').click();
  await page.keyboard.press('Control+k');
  await expect(page.locator('#workspaceCommandDialog')).not.toBeVisible();
  await expect(page.locator('#sessionNotesConfirm')).toBeVisible();
  await expect(page.locator('#sessionNotesCancel')).toBeFocused();
});

test('engineer sees report actions granted by the backend without operator administration',async({page})=>{
  await session(page,'ENGINEER',{'/reports':[{id:'r1',status:'SUBMITTED',materialsUsed:[],attachments:[]}]});
  await page.locator('.nav [data-view="reports"]').click();
  await expect(page.locator('[data-report-approve="r1"]')).toBeVisible();
  await expect(page.locator('[data-report-delete="r1"]')).toBeVisible();
  await expect(page.locator('#newOperatorBtn')).toBeHidden();
});

test('command shortcut does not cover an antenna editor with unsaved input',async({page})=>{
  await session(page);await page.locator('#workspaceActionsBtn').click();await page.locator('#quickAntennaBtn').click();
  await page.locator('#antennaName').fill('Nuova antenna in modifica');
  await page.keyboard.press('Control+k');
  await expect(page.locator('#workspaceCommandDialog')).not.toBeVisible();
  await expect(page.locator('#antennaModal')).toBeVisible();
  await expect(page.locator('#antennaName')).toHaveValue('Nuova antenna in modifica');
});

test('tablet stock names remain readable while technical columns scroll inside their region',async({page})=>{
  await page.setViewportSize({width:768,height:1024});
  await session(page,'ADMIN',{'/inventory':[{id:'rf-cable',name:'Cavo coassiale a bassa perdita 50 Ω',sku:'RF-C-002',quantity:3,minimumThreshold:5,supplier:'RadioLink',unit:'m',active:true}]});
  await page.locator('#mobileMenu').click();
  await page.locator('.nav [data-view="inventory"]').click();
  const cell=page.locator('#inventoryTableBody tr td').first();
  await expect(cell).toContainText('Cavo coassiale');
  expect((await cell.boundingBox()).width).toBeGreaterThanOrEqual(180);
  const region=page.getByRole('region',{name:'Elenco articoli'});
  await region.focus();await expect(region).toBeFocused();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});
