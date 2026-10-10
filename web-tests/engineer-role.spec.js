import { test, expect } from '@playwright/test';
import { session } from './fixtures/control-room-session.js';

test('ENGINEER can use the existing network management and report actions', async ({ page }) => {
    await session(page, 'ENGINEER', {
        '/reports': [{ id: 'report-a', status: 'SUBMITTED', taskTitle: 'Verifica rete', attachments: [] }],
    });
    await page.locator('.nav [data-view="reports"]').click();
    await expect(page.locator('[data-report-approve="report-a"]')).toBeVisible();
    await expect(page.locator('[data-report-delete="report-a"]')).toBeVisible();
    await page.locator('.nav [data-view="antennas"]').click();
    await expect(page.locator('#newAntennaBtn')).toBeVisible();
});
