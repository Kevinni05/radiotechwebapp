import {test,expect} from '@playwright/test';
import {session} from './fixtures/control-room-session.js';

const notes = page => page.locator('#sessionNotesEditor');
const saved = page => expect(page.locator('#sessionNotesStatus')).toHaveText('Salvato nella sessione');
const noteKeys = () => Object.keys(sessionStorage).filter(key=>key.startsWith('radiotech_session_notes_v1:'));

test('notes debounce, persist through navigation and refresh, and use sessionStorage exclusively',async({page})=>{
  await session(page);
  const writes=[];
  await page.exposeFunction('recordNoteWrite',key=>writes.push(key));
  await page.evaluate(()=>{
    const original=Storage.prototype.setItem;
    Storage.prototype.setItem=function(key,value){if(key.startsWith('radiotech_session_notes_v1:'))window.recordNoteWrite(key);return original.call(this,key,value);};
  });
  await notes(page).pressSequentially('Promemoria RF',{delay:10});
  await saved(page);expect(writes).toHaveLength(1);
  await page.locator('.nav [data-view="inventory"]').click();
  await page.locator('#brandHome').click();await expect(notes(page)).toHaveValue('Promemoria RF');
  await page.reload();await expect(notes(page)).toHaveValue('Promemoria RF');
  await expect(page.locator('#sessionNotesCount')).toHaveText('13 / 8000');
  await expect(page.locator('#sessionNotesUpdated')).toContainText('Ultima modifica');
  expect(await page.evaluate(()=>Object.keys(localStorage).some(key=>key.startsWith('radiotech_session_notes_v1:')))).toBe(false);
});

test('notes clear confirms, cancels, restores focus, and removes the session key',async({page})=>{
  await session(page);await notes(page).fill('Verifica connettore');await saved(page);
  await page.locator('#sessionNotesClear').click();
  await expect(page.getByRole('dialog',{name:'Svuotare le note rapide?'})).toBeVisible();
  await page.locator('#sessionNotesCancel').click();await expect(notes(page)).toHaveValue('Verifica connettore');
  await expect(notes(page)).toBeFocused();
  await page.locator('#sessionNotesClear').click();await page.locator('#sessionNotesDelete').click();
  await expect(notes(page)).toHaveValue('');await expect(page.locator('#sessionNotesClear')).toBeDisabled();
  expect(await page.evaluate(noteKeys)).toHaveLength(0);
});

test('storage errors preserve the editor and announce that persistence failed',async({page})=>{
  await session(page);
  await page.evaluate(()=>{const original=Storage.prototype.setItem;Storage.prototype.setItem=function(key,value){if(key.startsWith('radiotech_session_notes_v1:'))throw new DOMException('Full','QuotaExceededError');return original.call(this,key,value);};});
  await notes(page).fill('Appunto non salvato');
  await expect(page.locator('#sessionNotesStatus')).toContainText('Salvataggio non riuscito');
  await page.locator('.nav [data-view="inventory"]').click();await page.locator('#brandHome').click();
  await expect(notes(page)).toHaveValue('Appunto non salvato');
});

test('logout cancels pending note writes, clears the owner state and closes account menu',async({page})=>{
  await session(page);await notes(page).fill('Privato');await saved(page);
  await notes(page).fill('Modifica pendente');
  await page.locator('#workspaceAccountBtn').click();await page.locator('#topbarLogoutBtn').click();
  await expect(page.locator('#loginScreen')).toBeVisible();
  await expect(notes(page)).toHaveValue('');
  expect(await page.evaluate(noteKeys)).toHaveLength(0);
  await page.waitForTimeout(450);expect(await page.evaluate(noteKeys)).toHaveLength(0);
});

test('token refresh with a changed tenant isolates notes',async({page})=>{
  await session(page);await notes(page).fill('Tenant originale');await saved(page);
  await page.evaluate(()=>sessionStorage.setItem('radiotech_control_refresh','fixture-refresh'));
  let denied=false;
  await page.route('**/api/v1/auth/refresh',route=>route.fulfill({json:{token:'new-token',refreshToken:'new-refresh',user:{email:'admin@example.test',role:'ADMIN',tenantId:'other-tenant',name:'Secondo tenant'}}}));
  await page.route('**/api/v1/dashboard/stats',route=>{
    if(!denied){denied=true;return route.fulfill({status:401,json:{message:'Expired'}});}
    return route.fulfill({json:{antennas:0,operators:0}});
  });
  await page.locator('#refreshBtn').click();await expect(notes(page)).toHaveValue('');
  await notes(page).fill('Secondo tenant');await saved(page);
  const entries=await page.evaluate(()=>Object.entries(sessionStorage).filter(([key])=>key.startsWith('radiotech_session_notes_v1:')).map(([key,value])=>[JSON.parse(key.split(':').slice(1).join(':')),JSON.parse(value).text]));
  expect(entries).toContainEqual([['test-tenant','admin@example.test'],'Tenant originale']);
  expect(entries).toContainEqual([['other-tenant','admin@example.test'],'Secondo tenant']);
});

test('a different account in the same tenant never sees the previous account notes',async({page})=>{
  await session(page);await notes(page).fill('Solo per il primo account');await saved(page);
  await page.addInitScript(()=>sessionStorage.setItem('radiotech_control_user',JSON.stringify({role:'ADMIN',name:'Altro account',email:'second@example.test',tenantId:'test-tenant'})));
  await page.reload();await expect(notes(page)).toHaveValue('');
  await notes(page).fill('Secondo account');await saved(page);
  const entries=await page.evaluate(()=>Object.entries(sessionStorage).filter(([key])=>key.startsWith('radiotech_session_notes_v1:')).map(([key,value])=>[key,JSON.parse(value).text]));
  expect(entries).toHaveLength(2);expect(entries.map(([,text])=>text)).toEqual(expect.arrayContaining(['Solo per il primo account','Secondo account']));
});

test('unavailable session storage is announced on hydration without displaying stale notes',async({page})=>{
  await page.addInitScript(()=>{const get=Storage.prototype.getItem;Storage.prototype.getItem=function(key){if(key.startsWith('radiotech_session_notes_v1:'))throw new DOMException('Denied','SecurityError');return get.call(this,key);};});
  await session(page);await expect(notes(page)).toHaveValue('');
  await expect(page.locator('#sessionNotesStatus')).toContainText('Storage non disponibile');
});

test('command palette searches real navigation and implements keyboard shortcuts',async({page})=>{
  await session(page);await page.keyboard.press('Control+k');
  await expect(page.locator('#workspaceCommandDialog')).toBeVisible();
  await expect(page.locator('#workspaceCommandInput')).toBeFocused();
  await page.locator('#workspaceCommandInput').fill('Centro report');await page.keyboard.press('Enter');
  await expect(page.locator('#view-reports')).toHaveClass(/active/);await expect(page.locator('#workspaceCommandDialog')).not.toBeVisible();
  await page.locator('#workspaceSearchBtn').click();await page.locator('#workspaceCommandInput').fill('inesistente');
  await expect(page.locator('#workspaceCommandResults')).toContainText('Nessuna sezione');
  await page.keyboard.press('Escape');await expect(page.locator('#workspaceSearchBtn')).toBeFocused();
});

test('viewer command palette excludes mutations and account menu supports keyboard profile and logout',async({page})=>{
  await session(page,'VIEWER');await page.locator('#workspaceSearchBtn').click();
  await expect(page.locator('#workspaceCommandResults')).not.toContainText('Nuova antenna');
  await expect(page.locator('#workspaceCommandResults')).not.toContainText('Invia notifica');
  await page.keyboard.press('Escape');await page.locator('#workspaceAccountBtn').click();
  await expect(page.locator('#workspaceProfileBtn')).toBeFocused();await page.keyboard.press('Enter');
  await expect(page.locator('#view-profile')).toHaveClass(/active/);
  await page.locator('#workspaceAccountBtn').click();await page.keyboard.press('End');
  await expect(page.locator('#topbarLogoutBtn')).toBeFocused();await page.keyboard.press('Escape');
  await expect(page.locator('#workspaceAccountBtn')).toBeFocused();
});

test('signature bar remains one compact row and offers all actions at nine viewports',async({page})=>{
  await session(page);
  for(const [width,height] of [[1920,1080],[1440,900],[1280,800],[1024,768],[768,1024],[430,932],[390,844],[375,812],[320,720]]){
    await page.setViewportSize({width,height});
    const box=await page.locator('.signature-topbar').boundingBox();expect(box.height).toBeLessThanOrEqual(90);
    await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
    await expect.poll(()=>page.locator('.map-shell').evaluate(node=>node.getBoundingClientRect().height)).toBeGreaterThanOrEqual(width<=700?280:320);
    if(width<=650)await expect.poll(()=>page.locator('.calendar-day').first().evaluate(node=>node.getBoundingClientRect().width)).toBeGreaterThanOrEqual(70);
    await expect(page.getByRole('button',{name:'Cerca sezioni e azioni',exact:true})).toBeVisible();
    await expect(page.locator('#notificationBell')).toBeVisible();
    await page.locator('#workspaceActionsBtn').click();await expect(page.locator('#quickAntennaBtn')).toBeVisible();await expect(page.locator('#quickNotificationBtn')).toBeVisible();
    await page.keyboard.press('Escape');await page.locator('#workspaceSearchBtn').click();
    await page.locator('#workspaceCommandInput').fill('Aggiorna dati');await expect(page.locator('#workspaceCommandResults button')).toHaveText(/Aggiorna dati/);await page.keyboard.press('Escape');
  }
});
