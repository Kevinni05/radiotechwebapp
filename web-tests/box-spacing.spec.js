import {test,expect} from '@playwright/test';
import {session} from './fixtures/control-room-session.js';
import {measureBoxLayout} from './fixtures/box-layout.js';
const viewports=[[1920,1080],[1440,900],[1280,800],[1024,768],[768,1024],[430,932],[390,844],[375,812],[320,720]];
function data(filled){return {
  '/inventory':filled?[{id:'stock1',name:'Cavo coassiale a bassa perdita per collegamenti radio',sku:'RF-001',quantity:12,minimumThreshold:5,active:true}]:[],
  '/reports':filled?[{id:'report1',taskId:'t1',status:'SUBMITTED',operatorNotes:'Verifica del collegamento con misurazioni RF. '.repeat(15),attachments:[]}]:[],
  respond(path){
    if(path==='/calendar')return[];
    if(path==='/pro/catalog')return[{id:'requests',title:'Richieste',writable:true,fields:[{key:'title',label:'Titolo',type:'text',required:true}],actions:[]}];
    if(path==='/pro/requests')return{records:filled?[{id:'request1',title:'Verifica collegamento radio',status:'OPEN',version:1}]:[],partial:false};
    if(!filled&&['/operators','/tasks'].includes(path))return{success:true,data:[]};
    if(!filled&&['/incidents','/alerts','/workforce/shifts'].includes(path))return[];
  }
};}
test.use({reducedMotion:'reduce'});
for(const filled of [true,false])for(const[width,height]of viewports)test(`independent boxes are separated across all views at ${width}x${height} (${filled?'filled':'empty'})`,async({page})=>{
  test.setTimeout(60000);
  await page.setViewportSize({width,height});await page.route('https://**',route=>route.abort());
  await session(page,'ADMIN',data(filled));
  const views=await page.locator('.nav [data-view]').evaluateAll(nodes=>nodes.map(node=>node.dataset.view));
  expect(views.length).toBeGreaterThanOrEqual(18);
  for(const view of views){
    if(await page.locator('#mobileMenu').isVisible())await page.locator('#mobileMenu').click();
    await page.locator(`.nav [data-view="${view}"]`).click();
    const root=page.locator(`#view-${view}`);
    await expect(root).toBeVisible();
    await page.waitForLoadState('networkidle');
    await expect(root.locator('.spinner:visible')).toHaveCount(0);
    await expect.poll(async()=>await root.evaluate(measureBoxLayout),{message:`Panel collision or missing spacing in ${view}`,timeout:5000}).toMatchObject({issues:[],overflow:false});
  }
});
