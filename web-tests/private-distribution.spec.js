import {test,expect} from '@playwright/test';
import {session} from './fixtures/control-room-session.js';
import {createHash} from 'node:crypto';
test('private APK download is authenticated and its digest is checked before saving',async({page})=>{
 const bytes=Buffer.from('PK synthetic APK fixture'),filename='RadioTech-1.5.3-11001.apk',sha256=createHash('sha256').update(bytes).digest('hex');let authorized=false;
 await session(page,'ADMIN',{'/distribution/latest.json':{available:true,versionName:'1.5.3',versionCode:11001,size:bytes.length,filename,sha256}});
 await page.route(`**/api/v1/distribution/${filename}`,route=>{authorized=route.request().headers().authorization==='Bearer test-token';return route.fulfill({status:200,body:bytes,contentType:'application/octet-stream',headers:{'Content-Disposition':`attachment; filename="${filename}"`}});});
 await page.locator('[data-view="system"]').click();await expect(page.locator('#privateDistribution')).toContainText('1.5.3');
 const download=page.waitForEvent('download');await page.locator('[data-private-apk]').click();expect((await download).suggestedFilename()).toBe(filename);expect(authorized).toBe(true);await expect(page.locator('[data-private-status]')).toHaveText('APK verificato e scaricato.');
});
test('a corrupted private APK is rejected and download can be retried',async({page})=>{
 const filename='RadioTech-1.5.3-11001.apk';await session(page,'ADMIN',{'/distribution/latest.json':{available:true,versionName:'1.5.3',versionCode:11001,size:2,filename,sha256:'a'.repeat(64)}});
 await page.route(`**/api/v1/distribution/${filename}`,route=>route.fulfill({status:200,body:Buffer.from('PK'),contentType:'application/octet-stream'}));
 await page.locator('[data-view="system"]').click();await page.locator('[data-private-apk]').click();await expect(page.locator('[data-private-status]')).toContainText('Integrità APK non valida');await expect(page.locator('[data-private-apk]')).toBeEnabled();
});
