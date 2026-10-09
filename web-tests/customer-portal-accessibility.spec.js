import {test,expect} from '@playwright/test';

test('portal login announces pending and failed access, then restores keyboard navigation after logout',async({page})=>{
  let loginAttempt=0,releaseLogin;
  const errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  await page.route('**/api/**',async route=>{
    const path=new URL(route.request().url()).pathname;
    if(path==='/api/v1/auth/public-config')return route.fulfill({json:{enabled:false}});
    if(path==='/api/v1/auth/login') {
      loginAttempt++;
      if(loginAttempt===1) {
        await new Promise(resolve=>{
          releaseLogin=async()=>{
            await route.fulfill({status:401,json:{message:'Email o password non valide.'}});
            resolve();
          };
        });
        return;
      }
      return route.fulfill({json:{role:'CUSTOMER',token:'portal-test',refreshToken:'portal-refresh'}});
    }
    if(path==='/api/v1/pro/catalog')return route.fulfill({json:[{id:'requests',title:'Richieste di assistenza',writable:true,fields:[],actions:[]}]});
    if(path==='/api/v1/pro/requests')return route.fulfill({json:{records:[],partial:false}});
    return route.fulfill({status:404,json:{}});
  });
  await page.setViewportSize({width:320,height:800});
  await page.goto('/portal');
  await page.getByLabel('Email',{exact:true}).fill('cliente@example.test');
  await page.getByLabel('Password',{exact:true}).fill('test-password');
  const login=page.getByRole('button',{name:'Accedi al portale'});
  await login.click();
  await expect(page.locator('#portalLogin')).toHaveAttribute('aria-busy','true');
  await expect(login).toBeDisabled();
  await expect(page.locator('#portalStatus')).toHaveText('Accesso…');
  await expect.poll(()=>typeof releaseLogin).toBe('function');
  await releaseLogin();
  await expect(page.locator('#portalStatus')).toHaveText('Email o password non valide.');
  await expect(page.locator('#portalStatus')).toBeFocused();
  await expect(login).toBeEnabled();
  await expect(page.locator('#portalLogin')).not.toHaveAttribute('aria-busy','true');
  await login.click();
  await expect(page.locator('#portalApp')).toBeVisible();
  await expect(page.locator('#portalApp [data-view="pro"]')).toBeFocused();
  await page.getByRole('button',{name:'Esci',exact:true}).click();
  await expect(page.locator('#portalEmail')).toBeFocused();
  await expect(page.locator('#portalStatus')).toBeEmpty();
  await expect(page.locator('#portalApp')).toBeHidden();
  expect(errors).toEqual([]);
});
