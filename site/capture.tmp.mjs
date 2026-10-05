import { chromium } from 'playwright-core';
const base = 'http://localhost:4321';
const out = '/home/iliasmouhcine/gws-plus/.impeccable/review';
const browser = await chromium.launch({ executablePath: '/usr/bin/chromium', args: ['--no-sandbox'] });
const shots = [
  { path: '/', file: 'desktop', width: 1440, full: true },
  { path: '/', file: 'desktop-fold', width: 1440, full: false },
  { path: '/', file: 'mobile', width: 390, full: true },
  { path: '/docs/product/overview/', file: 'docs-mobile', width: 390, full: true },
  { path: '/docs/', file: 'docs-index-mobile', width: 390, full: true },
  { path: '/docs/', file: 'docs-index-desktop', width: 1440, full: true },
  { path: '/docs/product/design/', file: 'docs-desktop', width: 1440, full: true },
  { path: '/', file: 'desktop-dark', width: 1440, full: false, dark: true },
];
for (const shot of shots) {
  const ctx = await browser.newContext({
    viewport: { width: shot.width, height: shot.width === 390 ? 844 : 900 },
    reducedMotion: 'reduce', colorScheme: shot.dark ? 'dark' : 'light', deviceScaleFactor: 1,
  });
  const page = await ctx.newPage();
  await page.goto(base + shot.path, { waitUntil: 'networkidle' });
  await page.evaluate(() => document.fonts.ready);
  await page.screenshot({ path: `${out}/${shot.file}.png`, fullPage: shot.full });
  await ctx.close();
  console.log('captured', shot.file);
}
await browser.close();
