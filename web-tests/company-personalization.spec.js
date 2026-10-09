import {test,expect} from '@playwright/test';
import {session} from './fixtures/control-room-session.js';
test.use({reducedMotion:'reduce'});
const branding={name:'Acme Radio',colors:{accent:'#42aaff'},labels:{'nav-antennas':'Impianti'}};
test('dashboard geometry and weather placement are consistent on desktops',async({page})=>{
 await page.setViewportSize({width:1440,height:1000});await session(page,'ADMIN',{'/branding':{}});
 await expect(page.locator('.environment-console')).toBeVisible();
 const geometry=await page.evaluate(()=>{const box=s=>{const r=document.querySelector(s).getBoundingClientRect();return {x:r.x,y:r.y,w:r.width,h:r.height,b:r.bottom};};return {hero:box('.overview-head'),weather:box('.environment-console'),stats:box('#view-dashboard>.stats'),links:box('.dashboard-tools .workspace-links'),notes:box('.session-notes'),map:box('.dashboard-network>.panel:first-child'),network:box('.dashboard-network>.panel:last-child')};});
 expect(geometry.weather.y).toBeGreaterThanOrEqual(geometry.hero.b);expect(geometry.stats.y).toBeGreaterThanOrEqual(geometry.weather.b);
 for(const [a,b] of [['links','notes'],['map','network']]){expect(Math.abs(geometry[a].w-geometry[b].w)).toBeLessThan(2);expect(Math.abs(geometry[a].h-geometry[b].h)).toBeLessThan(2);}
 for(const width of [1366,1920,768,390]){await page.setViewportSize({width,height:1000});await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth+2)).toBeTruthy();}
});
test('company settings persist through reload; accessibility and palettes remain personal',async({page})=>{
 let saved={};const respond=(path,method,request)=>{if(path==='/branding'){if(method==='PUT')saved=JSON.parse(request.postData());return saved;}};
 await session(page,'ADMIN',{respond});await expect(page.locator('#brandStatus')).toHaveText(/caricata/);
 await page.locator('#brandingNav').click();await page.locator('#brandNameInput').fill('Acme Radio');
 await page.locator('#brandWelcomeInput').fill('La rete Acme');await page.locator('#brandColor-accent').fill('#42aaff');
 await page.locator('#brandPaletteMode').selectOption('light');await page.locator('#brandColor-accent').fill('#123456');
 await page.locator('#brandLabelSearch').fill('Infrastruttura');await page.locator('#label-nav-antennas').fill('Impianti');await page.locator('#brandSave').click();
 await expect(page.locator('#brandStatus')).toHaveText(/salvata/);expect(saved.name).toBe('Acme Radio');expect(saved.colors.lightAccent).toBe('#123456');
 await page.locator('#themeMenuBtn').click();await page.locator('[data-mode=light]').click();await page.locator('#highContrast').check();await page.locator('#reduceMotion').check();await page.locator('#textScale').selectOption('125');
 await expect(page.locator('html')).toHaveAttribute('data-appearance','light');await expect(page.locator('html')).toHaveAttribute('data-contrast','high');
 await page.reload();await expect(page.locator('.brand strong')).toHaveText('Acme Radio');await expect(page.locator('[data-view=antennas]')).toContainText('Impianti');await expect(page.locator('html')).toHaveAttribute('data-appearance','light');
});
test('profile crop is circular, saved through profile API and displayed in all avatars',async({page})=>{
 let photo='';await session(page,'ADMIN',{'/branding':{},respond:(path,method,request)=>{if(path==='/capo/profile'&&method==='PUT'){const body=JSON.parse(request.postData());photo=body.photoUrl;return {success:true,data:body};}}});
 await page.locator('[data-view=profile]').click();await page.locator('#editProfileBtn').click();
 const png=await page.evaluate(()=>{const c=document.createElement('canvas');c.width=600;c.height=400;const g=c.getContext('2d');g.fillStyle='#42aaff';g.fillRect(0,0,600,400);return c.toDataURL('image/png').split(',')[1];});
 await page.locator('#profilePhotoFile').setInputFiles({name:'portrait.png',mimeType:'image/png',buffer:Buffer.from(png,'base64')});await expect(page.locator('#profileCropDialog')).toBeVisible();
 await page.locator('#cropZoom').fill('1.5');await page.locator('#cropSave').click();await page.locator('#profileForm button[type=submit]').click();
 await expect(page.locator('#profileModal')).not.toHaveClass(/open/);expect(photo).toMatch(/^data:image\/webp;base64,/);await expect(page.locator('#profileAvatar img')).toBeVisible();
 const alpha=await page.evaluate(async src=>{const i=new Image();i.src=src;await i.decode();const c=document.createElement('canvas');c.width=320;c.height=320;const g=c.getContext('2d');g.drawImage(i,0,0);return g.getImageData(0,0,1,1).data[3];},photo);expect(alpha).toBe(0);
});
test('viewer sees company branding but cannot modify it',async({page})=>{
 await session(page,'VIEWER',{'/branding':branding});await expect(page.locator('.brand strong')).toHaveText('Acme Radio');await expect(page.locator('#brandingNav')).toBeHidden();await expect(page.locator('#brandSave')).toBeDisabled();
});
test('light appearance and enlarged text remain contained across core workspaces',async({page})=>{
 await page.setViewportSize({width:1366,height:900});await session(page,'ADMIN',{'/branding':{}});await expect(page.locator('#brandStatus')).toHaveText(/caricata/);
 if(process.env.RADIOTECH_VISUAL_QA)await page.screenshot({path:'/tmp/radiotech-dashboard-dark.png',fullPage:true});
 await page.locator('#themeMenuBtn').click();await page.locator('[data-mode=light]').click();await page.locator('#textScale').selectOption('125');await page.keyboard.press('Escape');await expect(page.locator('#themeMenuBtn')).toBeFocused();
 for(const view of ['dashboard','antennas','operations','planning','reports','inventory','profile','branding']){
  const menu=page.locator('#mobileMenu');if(await menu.isVisible()&&await menu.getAttribute('aria-expanded')!=='true')await menu.click();await page.locator(`.nav [data-view="${view}"]`).click();await expect(page.locator('#view-'+view)).toBeVisible();await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+2)).toBeTruthy();
 }
 if(process.env.RADIOTECH_VISUAL_QA){await page.locator('.nav [data-view="dashboard"]').click();await page.screenshot({path:'/tmp/radiotech-dashboard-light.png',fullPage:true});}
});
