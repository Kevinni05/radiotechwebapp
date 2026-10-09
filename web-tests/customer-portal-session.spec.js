import {test,expect} from '@playwright/test';

test('portal logout discards an in-flight refresh before the next customer session',async({page})=>{
  let loginCount=0,requestCount=0,releaseRefresh;
  const authorizations=[],errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  await page.route('**/api/**',async route=>{
    const path=new URL(route.request().url()).pathname;
    if(path==='/api/v1/auth/public-config')return route.fulfill({json:{enabled:false}});
    if(path==='/api/v1/auth/login') {
      loginCount++;
      return route.fulfill({json:{role:'CUSTOMER',token:`customer-${loginCount}`,refreshToken:`refresh-${loginCount}`}});
    }
    if(path==='/api/v1/auth/refresh') {
      await new Promise(resolve=>{
        releaseRefresh=async()=>{
          await route.fulfill({json:{token:'stale-customer',refreshToken:'stale-refresh'}});
          resolve();
        };
      });
      return;
    }
    if(path==='/api/v1/pro/catalog')return route.fulfill({json:[{id:'requests',title:'Richieste di assistenza',writable:true,fields:[],actions:[]}]});
    if(path==='/api/v1/pro/requests') {
      authorizations.push(route.request().headers().authorization);
      requestCount++;
      if(requestCount===1)return route.fulfill({status:401,json:{message:'Token scaduto'}});
      return route.fulfill({json:{records:[],partial:false}});
    }
    return route.fulfill({status:404,json:{}});
  });
  await page.goto('/portal');
  async function login() {
    await page.getByLabel('Email',{exact:true}).fill('cliente@example.test');
    await page.getByLabel('Password',{exact:true}).fill('test-password');
    await page.getByRole('button',{name:'Accedi al portale'}).click();
    await expect(page.locator('#portalApp')).toBeVisible();
  }
  await login();
  await expect.poll(()=>typeof releaseRefresh).toBe('function');
  await page.getByRole('button',{name:'Esci',exact:true}).click();
  await login();
  await expect(page.locator('#proStatus')).toContainText('0 registrazioni');
  await releaseRefresh();
  await page.locator('#proRefresh').click();
  await expect.poll(()=>authorizations.length).toBeGreaterThan(2);
  expect(authorizations.slice(1)).toEqual(authorizations.slice(1).map(()=>'Bearer customer-2'));
  await expect(page.locator('#portalStatus')).toBeEmpty();
  expect(errors).toEqual([]);
});

for(const outcome of ['success','failure']) {
  test(`portal pending save ${outcome} cannot alter the next customer's editor`,async({page})=>{
    let loginCount=0,releaseSave;
    const errors=[];
    page.on('pageerror',error=>errors.push(error.message));
    await page.route('**/api/**',async route=>{
      const path=new URL(route.request().url()).pathname;
      if(path==='/api/v1/auth/public-config')return route.fulfill({json:{enabled:false}});
      if(path==='/api/v1/auth/login') {
        loginCount++;
        return route.fulfill({json:{role:'CUSTOMER',token:`customer-${loginCount}`,refreshToken:`refresh-${loginCount}`}});
      }
      if(path==='/api/v1/pro/catalog')return route.fulfill({json:[{id:'requests',title:'Richieste di assistenza',writable:true,fields:[{key:'name',label:'Titolo',type:'text',required:true}],actions:[]}]});
      if(path==='/api/v1/pro/requests'&&route.request().method()==='POST') {
        await new Promise(resolve=>{
          releaseSave=async()=>{
            await route.fulfill({status:outcome==='success'?200:409,json:outcome==='success'?{id:'previous-request'}:{message:'Errore della sessione precedente'}});
            resolve();
          };
        });
        return;
      }
      if(path==='/api/v1/pro/requests')return route.fulfill({json:{records:[],partial:false}});
      return route.fulfill({status:404,json:{}});
    });
    await page.goto('/portal');
    async function openEditor(title) {
      await page.getByLabel('Email',{exact:true}).fill('cliente@example.test');
      await page.getByLabel('Password',{exact:true}).fill('test-password');
      await page.getByRole('button',{name:'Accedi al portale'}).click();
      await expect(page.locator('#proStatus')).toContainText('0 registrazioni');
      await page.locator('#proNew').click();
      await page.getByLabel('Titolo *',{exact:true}).fill(title);
    }
    await openEditor('Richiesta della sessione precedente');
    await page.getByRole('button',{name:'Salva',exact:true}).click();
    await expect.poll(()=>typeof releaseSave).toBe('function');
    await page.getByRole('button',{name:'Esci',exact:true}).click();
    await openEditor('Bozza del nuovo cliente');
    await releaseSave();
    // Allow the fulfilled response and its rejected stale-session continuation to finish.
    await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
    await expect(page.locator('#proForm')).toBeVisible();
    await expect(page.getByLabel('Titolo *',{exact:true})).toHaveValue('Bozza del nuovo cliente');
    await expect(page.locator('#proFormStatus')).toBeEmpty();
    await expect(page.locator('#portalStatus')).toBeEmpty();
    expect(errors).toEqual([]);
  });
}
