import {test,expect} from '@playwright/test';
import {session} from './fixtures/control-room-session.js';

test('asset activity ratio is distinct from uptime and uses the server calculation time',async({page})=>{
 const asOf='2026-10-09T10:00:00Z';
 await session(page,'ADMIN',{respond:async(path)=>path==='/dashboard/stats'?{
  antennas:4,activeAntennas:1,inactiveAntennas:3,availability:25,asOf,
  operators:0,interventions:0,tasks:0,lowStock:0,taskStats:{},
 }:undefined});
 await expect(page.locator('#availabilityValue')).toHaveText('25,00%');
 await expect(page.locator('#activeCount')).toHaveText('1 / 4');
 await expect(page.locator('#inactiveCount')).toHaveText('3 / 4');
 await expect(page.locator('#availabilityBadge')).toHaveText('1 / 4 attive');
 await expect(page.locator('#assetAvailabilityMeaning')).toContainText('non misura il tempo di funzionamento');
 const expected=await page.evaluate(value=>new Date(value).toLocaleString('it-IT'),asOf);
 await expect(page.locator('#lastUpdate')).toHaveText(expected);
});

test('missing calculation time is explicit instead of showing a fresh client timestamp',async({page})=>{
 await session(page);
 await expect(page.locator('#lastUpdate')).toHaveText('Ora del dato non disponibile');
});
