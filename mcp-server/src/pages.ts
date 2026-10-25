/*
 * Shared HTML shell for the browser-facing OAuth pages.
 *
 * The markup mirrors the app's connexion screen (docs/product/DESIGN.md §4):
 * the supplied school illustration fills the viewport, the brand block sits
 * above the form, and both panels are « liquid glass » — translucent layers,
 * backdrop blur, a specular sheen and a gradient rim — over the same
 * « École vivante » tokens. No script runs on these pages: the visual effects
 * are pure CSS, so the Content-Security-Policy can stay script-free.
 */

/** Bumped when site.css or the served assets change, to refresh long-lived caches. */
export const assetVersion = '1';

export function escapeHtml(value: string) {
  return value.replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]!));
}

/**
 * Wraps a page body in the shared shell. `heading` is the visible page title
 * and defaults to `title`, the document title.
 */
export function html(title: string, body: string, heading = title) {
  return `<!doctype html>
<html lang="fr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light dark">
<meta name="theme-color" content="#F7F1E5" media="(prefers-color-scheme: light)">
<meta name="theme-color" content="#141B15" media="(prefers-color-scheme: dark)">
<title>${escapeHtml(title)}</title>
<link rel="icon" href="/assets/logo.png" type="image/png">
<link rel="stylesheet" href="/assets/site.css?v=${assetVersion}">
</head>
<body>
<div class="fond" aria-hidden="true"></div>
<main class="page">
<header class="marque verre">
<div class="marque-ligne"><img class="logo" src="/assets/logo.png" alt="" width="80" height="80"><span class="mot">GWS+</span></div>
<span class="surligneur" aria-hidden="true"></span>
</header>
<section class="card verre">
<h1>${escapeHtml(heading)}</h1>
${body}
</section>
<footer class="pied">Greenwood School +</footer>
</main>
</body>
</html>`;
}
