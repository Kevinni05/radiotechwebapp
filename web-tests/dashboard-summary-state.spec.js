import {test,expect} from '@playwright/test';
import {session} from './fixtures/control-room-session.js';

test('partial summary keeps unavailable antenna counts distinct from zero',async({page})=>{
 await session(page,'ADMIN',{respond:path=>path==='/dashboard/stats'?{antennas:1,availability:100}:undefined});
 await expect(page.locator('#availabilityValue')).toContainText('100');
 await expect(page.locator('#activeCount')).toHaveText('— / 1');
 await expect(page.locator('#inactiveCount')).toHaveText('— / 1');
 await expect(page.locator('#availabilityBadge')).toHaveText('Conteggio non disponibile');
 await expect(page.locator('#availabilityBadge')).toHaveClass(/gray/);
 await expect(page.locator('#statLowStock')).toHaveText('—');
 await expect(page.locator('#statOperators')).toHaveText('—');
 await expect(page.locator('#taskAssigned')).toHaveText('—');
});

test('explicit zero assets is a known empty summary',async({page})=>{
 await session(page,'ADMIN',{respond:path=>path==='/dashboard/stats'?{antennas:0,activeAntennas:0,inactiveAntennas:0,availability:0}:undefined});
 await expect(page.locator('#availabilityBadge')).toHaveText('NESSUN ASSET');
 await expect(page.locator('#activeCount')).toHaveText('0 / 0');
 await expect(page.locator('#inactiveCount')).toHaveText('0 / 0');
 await expect(page.locator('#availabilityBadge')).toHaveClass(/gray/);
});

test('missing or invalid percentage does not claim zero availability',async({page})=>{
 await session(page,'ADMIN',{respond:path=>path==='/dashboard/stats'?{antennas:2,activeAntennas:1,inactiveAntennas:1,availability:200}:undefined});
 await expect(page.locator('#availabilityValue')).toHaveText('—');
 await expect(page.locator('#activeCount')).toHaveText('1 / 2');
 await expect(page.locator('#availabilityBadge')).toHaveClass(/gray/);
});
