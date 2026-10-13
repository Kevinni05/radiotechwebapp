import {test,expect} from '@playwright/test';
import {session} from './fixtures/control-room-session.js';
test.use({reducedMotion:'reduce'});

test('push failures are visible and retry keeps the existing notification identity',async({page})=>{
 const notice={id:'notice-a',title:'Avviso QA',message:'Messaggio dalla centrale',deliveryStatus:'PARTIAL',delivered:0,failedRecipients:1,errorCodes:{PERMISSION_DENIED:1},createdAt:'2026-10-13T12:00:00Z'};
 let retries=0;
 await session(page,'ADMIN',{'/dashboard/notifications':[notice],respond:(path,method)=>{
   if(path==='/notifications/notice-a/retry'&&method==='POST'){retries++;notice.deliveryStatus='SENT';notice.delivered=1;notice.failedRecipients=0;notice.errorCodes={};return notice;}
 }});
 await page.locator('.nav [data-view="notifications"]').click();
 await expect(page.locator('.notification-delivery')).toContainText('Configurazione invio push non autorizzata');
 await page.locator('[data-notification-retry="notice-a"]').click();
 await expect(page.locator('.notification-delivery')).toContainText('Accettata dal provider');
 await expect(page.locator('.notification-delivery')).toContainText('Ricezione sul telefono e lettura da verificare');
 expect(retries).toBe(1);
});

test('timeout replay uses the same operation and zero devices never appears delivered',async({page})=>{
 await session(page);
 const requests=[];
 await page.route('**/api/v1/notifications',async route=>{
   if(route.request().method()!=='POST')return route.fallback();
   requests.push(JSON.parse(route.request().postData()));
   if(requests.length===1)return route.abort('failed');
   return route.fulfill({contentType:'application/json',body:JSON.stringify({success:true,persisted:true,deliveryStatus:'NO_TOKENS',providerAccepted:0,operationId:requests.at(-1).operationId})});
 });
 await page.locator('.nav [data-view="notifications"]').click();
 await page.locator('#notificationTitle').fill('Avviso QA');
 await page.locator('#notificationMessage').fill('Messaggio QA');
 await page.locator('#sendNotificationBtn').click();
 await expect(page.locator('#sendNotificationBtn')).toBeEnabled();
 await page.locator('#sendNotificationBtn').click();
 await expect(page.locator('#toastStack')).toContainText('Nessun dispositivo registrato');
 expect(requests).toHaveLength(2);expect(requests[0].operationId).toBe(requests[1].operationId);
});

test('viewer can inspect push outcomes without retry controls',async({page})=>{
 await session(page,'VIEWER',{'/dashboard/notifications':[{id:'notice-a',title:'Avviso',message:'Test',deliveryStatus:'NO_TOKENS'}]});
 await page.locator('.nav [data-view="notifications"]').click();
 await expect(page.locator('.notification-delivery')).toContainText('Nessun dispositivo registrato');
 await expect(page.locator('[data-notification-retry]')).toBeHidden();
});
