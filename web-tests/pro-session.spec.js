import {test,expect} from '@playwright/test';

test('shared Pro workspace discards a previous tenant catalog before caching it',async({page})=>{
  await page.route('**/api/v1/auth/public-config',route=>route.fulfill({json:{enabled:false}}));
  await page.goto('/portal');
  await page.evaluate(()=>{
    const state={authRole:'CUSTOMER',sessionEpoch:0};
    let catalogRequests=0,release;
    const workspace=window.RadioTechPro({
      state,escapeHtml:value=>String(value??''),toast:()=>{},switchView:()=>{},
      apiFetch:async path=>{
        if(path==='/api/v1/pro/catalog') {
          catalogRequests++;
          if(catalogRequests===1)return new Promise(resolve=>release=resolve);
          return [{id:'new-tenant',title:'Nuova azienda',fields:[],actions:[],writable:false}];
        }
        return {records:[],partial:false};
      }
    });
    window.proSessionHarness={state,workspace,release:()=>release([{id:'previous-tenant',title:'Azienda precedente',fields:[],actions:[],writable:false}]),requests:()=>catalogRequests};
    document.querySelector('[data-view="pro"]').click();
    state.sessionEpoch++;
    workspace.reset();
  });
  await page.evaluate(()=>window.proSessionHarness.release());
  await page.evaluate(()=>document.querySelector('[data-view="pro"]').click());
  await expect(page.locator('#proTitle')).toHaveText('Nuova azienda');
  await expect(page.locator('#proModule option')).toHaveCount(1);
  await expect(page.locator('#proModule')).toHaveValue('new-tenant');
  expect(await page.evaluate(()=>window.proSessionHarness.requests())).toBe(2);
  await expect(page.locator('#proRecords')).not.toContainText('Azienda precedente');
});
