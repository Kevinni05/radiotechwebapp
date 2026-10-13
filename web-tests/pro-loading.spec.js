import {test,expect} from '@playwright/test';
import {session} from './fixtures/control-room-session.js';

test('new record waits for the authorized module catalog instead of losing an early click',async({page})=>{
 await session(page);
 let release;
 const pending=new Promise(resolve=>{release=resolve;});
 await page.route('**/api/v1/pro/catalog',async route=>{
  await pending;
  await route.fulfill({contentType:'application/json',body:JSON.stringify([{id:'clients',title:'Clienti',writable:true,fields:[{key:'name',label:'Nome',type:'text',required:true}],actions:[]}])});
 });
 await page.locator('[data-view="pro"]').click();
 try {
  await expect(page.locator('#proStatus')).toContainText('Caricamento');
  await expect(page.locator('#proNew')).toBeDisabled();
  await expect(page.locator('#proForm')).toBeHidden();
 } finally {release();}
 await expect(page.locator('#proNew')).toBeEnabled();
 await page.locator('#proNew').click();
 await expect(page.locator('#proForm')).toBeVisible();
 await expect(page.locator('#proField-name')).toBeVisible();
});
