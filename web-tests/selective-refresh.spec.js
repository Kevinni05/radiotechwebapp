import { test, expect } from '@playwright/test';
import { session } from './fixtures/control-room-session.js';

test('automatic refresh skips offscreen report/profile data and pauses while notes are edited', async ({ page }) => {
    await page.clock.install();
    const calls = [];
    await session(page, 'ADMIN', { respond(path, method) { if (method === 'GET') calls.push(path); } });
    await page.locator('#sessionNotesEditor').focus();
    calls.length = 0;
    await page.clock.runFor(60001);
    expect(calls.filter(path => ['/dashboard/stats', '/reports', '/dashboard/profile'].includes(path))).toEqual([]);
    await page.locator('#sessionNotesEditor').evaluate(element => element.blur());
    calls.length = 0;
    await page.clock.runFor(60001);
    await expect.poll(() => calls.includes('/dashboard/stats')).toBe(true);
    expect(calls).not.toContain('/reports');
    expect(calls).not.toContain('/dashboard/profile');
});
