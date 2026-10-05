import { chromium } from 'playwright-core';
const b = await chromium.launch({ executablePath: '/usr/bin/chromium', args: ['--no-sandbox'] });
const p = await (await b.newContext({ viewport: { width: 1440, height: 900 } })).newPage();
await p.goto('http://localhost:4321/', { waitUntil: 'networkidle' });
const r = await p.evaluate(() => {
  const table = document.querySelector('.appel');
  const tr = document.querySelector('.appel tbody tr');
  const feuille = document.querySelector('.feuille');
  const cs = getComputedStyle(tr);
  return {
    feuille: feuille.getBoundingClientRect().width,
    table: table.getBoundingClientRect().width,
    tr: tr.getBoundingClientRect().width,
    cols: cs.gridTemplateColumns,
    tbodyDisplay: getComputedStyle(document.querySelector('.appel tbody')).display,
    tableWidthCss: getComputedStyle(table).width,
  };
});
console.log(JSON.stringify(r, null, 2));
await b.close();
