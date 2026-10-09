import {test,expect} from '@playwright/test';
import {session} from './fixtures/control-room-session.js';

test('weather shows backend failure details and refresh recovers without reloading the page',async({page})=>{
  const message='Limite di richieste del servizio meteo raggiunto. Riprova tra qualche minuto.';
  await session(page,'ADMIN',{antennas:[{id:'a1',name:'Lecce',latitude:40.35481,longitude:18.17244}],'/weather':{temperature_2m:24,weather_code:0,daily:{}}});
  await expect(page.locator('#liveWeather')).toContainText('24 °C');
  await page.setViewportSize({width:320,height:720});
  await page.route('**/api/v1/weather?*',route=>route.fulfill({status:503,json:{success:false,message}}));
  await page.locator('#refreshWeather').click();
  await expect(page.locator('#weatherCondition')).toHaveText('Meteo non disponibile');
  await expect(page.locator('#weatherSource')).toHaveText(message);
  await expect(page.locator('#weatherTemperature')).toHaveText('—');
  await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  await page.route('**/api/v1/weather?*',route=>route.fulfill({json:{temperature_2m:24,weather_code:0,daily:{},observedAt:new Date().toISOString()}}));
  await page.locator('#refreshWeather').click();
  await expect(page.locator('#liveWeather')).toContainText('24 °C');
  await expect(page.locator('#weatherSource')).toHaveText(/Previsione/);
  await page.setViewportSize({width:320,height:720});
  await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});
