import { test, expect } from '@playwright/test';
import { readFile } from 'node:fs/promises';

const assets = new URL('../src/main/resources/static/assets/', import.meta.url);
async function scene(page) {
  await page.setContent('<main><h1>RadioTech</h1><button id="action">Apri console</button></main>');
  await page.addStyleTag({ content: await readFile(new URL('ambient-canvas.css', assets), 'utf8') });
  await page.addScriptTag({ content: await readFile(new URL('ambient-canvas.js', assets), 'utf8') });
  await expect(page.locator('.ambient-field canvas')).toBeAttached();
}
const pixels = page => page.locator('.ambient-field canvas').evaluate(canvas => canvas.toDataURL());

test('light field animates behind interactive UI without errors or overflow', async ({ page }) => {
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await scene(page);
  const before = await pixels(page);
  await expect.poll(() => pixels(page)).not.toBe(before);
  await page.mouse.move(1200, 600);
  let clicks = 0;
  await page.exposeFunction('recordAction', () => { clicks++; });
  await page.locator('#action').evaluate(button => button.addEventListener('click', () => window.recordAction()));
  await page.locator('#action').click();
  await expect.poll(() => clicks).toBe(1);
  await page.locator('#action').focus();
  await expect(page.locator('#action')).toBeFocused();
  expect(await page.locator('.ambient-field').getAttribute('aria-hidden')).toBe('true');
  expect(await page.locator('.ambient-field').evaluate(el => getComputedStyle(el).zIndex)).toBe('-1');
  for (const width of [390, 1920, 3840]) {
    await page.setViewportSize({ width, height: width === 3840 ? 2160 : 900 });
    await expect.poll(() => page.locator('.ambient-field canvas').evaluate(c => c.width * c.height)).toBeLessThan(1203000);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  }
  expect(errors).toEqual([]);
});

test('OS and in-app reduced motion produce a still frame; high contrast hides lights', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await scene(page);
  const still = await pixels(page);
  await page.mouse.move(600, 300);
  await page.waitForTimeout(150);
  expect(await pixels(page)).toBe(still);
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await expect.poll(() => pixels(page)).not.toBe(still);
  await page.evaluate(() => { document.documentElement.dataset.motion = 'reduced'; });
  const manual = await pixels(page);
  await page.waitForTimeout(150);
  expect(await pixels(page)).toBe(manual);
  await page.evaluate(() => { document.documentElement.dataset.contrast = 'high'; });
  await expect(page.locator('.ambient-field')).toBeHidden();
  await page.evaluate(() => { document.documentElement.dataset.contrast = 'normal'; });
  await page.emulateMedia({ media: 'print' });
  await expect(page.locator('.ambient-field')).toBeHidden();
});
