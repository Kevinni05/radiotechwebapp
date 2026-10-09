import {test,expect} from '@playwright/test';
import {session} from './fixtures/control-room-session.js';

const viewports=[[1920,1080],[1440,900],[1280,800],[1024,768],[768,1024],[430,932],[390,844],[375,812],[320,720]];
const views=['dashboard','antennas','operations','planning','reports','notifications','tools','inventory','profile','system'];
test.use({reducedMotion:'reduce'});
function dataFor(filled){return{
  antennas:filled?[{id:'a1',name:'Ponte radio Bari – Monte Caccia',status:'ATTIVA',lat:41.12,lng:16.87,specs:{frequenza:5800,potenza:20,ros:1.2,temperatura:34}}]:[],
  '/reports':filled?[{id:'r1',taskId:'t1',operatorId:'o1',status:'SUBMITTED',operatorNotes:'Verifica orientamento antenna e misure RF completate.',submittedAt:'2026-10-08T14:30:00Z',attachments:[]}]:[],
  '/inventory':filled?[{id:'s1',name:'Connettore N maschio per cavo coassiale RF a bassa perdita',sku:'RF-N-001',quantity:24,minimumThreshold:10,supplier:'Telecom Components Italia',location:'Scaffale B-12',active:true}]:[],
  respond(path){
    if(path==='/calendar')return[];
    if(path==='/weather')return{temperature_2m:21,weather_code:0,apparent_temperature:20,relative_humidity_2m:60,wind_speed_10m:12,daily:{time:[]}};
    if(path==='/auth/public-config')return{enabled:false};
    if(!filled&&['/tasks','/operators'].includes(path))return{success:true,data:[]};
    if(!filled&&['/incidents','/alerts','/workforce/shifts'].includes(path))return[];
  }
};}
for(const filled of [true,false])for(const[width,height]of viewports)test(`primary workspaces fit ${width}x${height} with ${filled?'loaded':'empty'} data`,async({page})=>{
  test.setTimeout(60000);
  await page.setViewportSize({width,height});
  const errors=[];page.on('pageerror',error=>errors.push(error.message));
  await page.route('https://**',route=>route.abort());
  await session(page,'ADMIN',dataFor(filled));
    for(const view of views){
      const menu=page.locator('#mobileMenu');
      if(await menu.isVisible()&&await menu.getAttribute('aria-expanded')!=='true')await menu.click();
      await page.locator(`.nav [data-view="${view}"]`).click();
      await expect(page.locator(`#view-${view}`)).toBeVisible();
      await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth),{message:`${view} overflow at ${width}x${height}`}).toBeLessThanOrEqual(width);
      const bar=await page.locator('.signature-topbar').boundingBox();
      expect(bar.height,`${view} topbar at ${width}x${height}`).toBeLessThanOrEqual(90);
      expect(bar.x).toBeGreaterThanOrEqual(0);expect(bar.x+bar.width).toBeLessThanOrEqual(width+1);
      await expect(page.locator('#workspaceSearchBtn')).toBeVisible();
      await expect(page.locator('#workspaceAccountBtn')).toBeVisible();
    }
  expect(errors).toEqual([]);
});

test('mobile command palette remains reachable after opening and dismissing the navigation drawer',async({page})=>{
  await page.route('https://**',route=>route.abort());
  await page.setViewportSize({width:320,height:720});await session(page,'ADMIN',dataFor(true));
  await page.locator('#mobileMenu').click();
  await expect(page.locator('.nav [data-view="reports"]')).toBeVisible();
  await page.locator('.nav [data-view="reports"]').click();
  await page.locator('#workspaceSearchBtn').click();
  await expect(page.locator('#workspaceCommandInput')).toBeFocused();
  await page.locator('#workspaceCommandInput').fill('Inventario');
  await page.keyboard.press('Enter');
  await expect(page.locator('#view-inventory')).toBeVisible();
  await expect(page.locator('#workspaceCommandDialog')).not.toBeVisible();
  await expect(page.locator('#mobileMenu')).toHaveAttribute('aria-expanded','false');
  await page.locator('#workspaceAccountBtn').click();
  await expect(page.locator('#workspaceProfileBtn')).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(page.locator('#workspaceAccountBtn')).toBeFocused();
});

test('dashboard notes stay compact and leave enough width for the infrastructure map',async({page})=>{
  await page.setViewportSize({width:1440,height:900});
  await page.route('https://**',route=>route.abort());
  await session(page,'ADMIN',dataFor(true));
  const notes=await page.locator('.session-notes').boundingBox();
  const map=await page.locator('.map-shell').boundingBox();
  const main=await page.locator('#view-dashboard').boundingBox();
  expect(notes.height,'An empty notes widget must not contain other dashboard panels').toBeLessThan(440);
  expect(map.width,'The network map needs a readable share of the desktop workspace').toBeGreaterThan(main.width*0.4);
});
