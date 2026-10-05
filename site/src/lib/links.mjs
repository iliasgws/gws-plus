import { readFileSync, existsSync } from 'node:fs';
import { posix } from 'node:path';

export const REPO_ROOT_URL = 'https://github.com/iliasgws/gws-plus/blob/main';

export const CURATED = [
  'docs/README.md',
  'docs/product/OVERVIEW.md',
  'docs/product/DESIGN.md',
  'docs/product/ROADMAP.md',
  'docs/development/SETUP.md',
  'docs/development/ARCHITECTURE.md',
  'docs/development/NAVIGATION.md',
  'docs/api/BOTI-API.md',
  'docs/api/ENDPOINT-MAP.md',
  'docs/api/ENDPOINTS.md',
  'docs/security/SECURITY-NOTES.md',
];

export const routeFor = (repoPath) =>
  repoPath === 'docs/README.md'
    ? '/docs/'
    : `/docs/${repoPath.replace(/^docs\//, '').replace(/\.md$/, '').toLowerCase()}/`;

const DOCS_ROOT = new URL('../../../docs/', import.meta.url);

export const readDoc = (repoPath) => readFileSync(new URL(repoPath.replace(/^docs\//, ''), DOCS_ROOT), 'utf8');

export const docExists = (repoPath) => existsSync(new URL(repoPath.replace(/^docs\//, ''), DOCS_ROOT));

const normalizeTarget = (raw) => {
  if (/^(https?:|mailto:|#|\/)/.test(raw)) return null;
  const [path] = raw.split('#');
  if (!path.toLowerCase().endsWith('.md')) return null;
  return path;
};

export const buildHrefMap = () => {
  const map = new Map();
  for (const repoPath of CURATED) {
    if (!docExists(repoPath)) continue;
    const dir = posix.dirname(repoPath);
    const content = readDoc(repoPath);
    const targets = [
      ...[...content.matchAll(/\]\(([^)\s]+)\)/g)].map((m) => m[1]),
      ...[...content.matchAll(/href="([^"]+)"/g)].map((m) => m[1]),
    ];
    for (const raw of targets) {
      const path = normalizeTarget(raw);
      if (!path) continue;
      const resolved = posix.normalize(posix.join(dir, path));
      const dest = CURATED.includes(resolved) ? routeFor(resolved) : `${REPO_ROOT_URL}/${resolved}`;
      const existing = map.get(path);
      if (existing && existing !== dest) {
        throw new Error(`Liens contradictoires pour « ${path} » : ${existing} ≠ ${dest}`);
      }
      map.set(path, dest);
    }
  }
  return map;
};

export const rewriteMarkdownLinks = (code, repoPath, map) => {
  if (!/\.md(["')\s#?]|$)/.test(code)) return null;
  const dir = posix.dirname(repoPath);
  return code.replace(/\]\(([^)\s]+)\)/g, (full, target) => {
    const anchor = target.split('#')[1];
    const norm = normalizeTarget(target);
    if (!norm) return full;
    const resolved = posix.normalize(posix.join(dir, norm));
    const dest =
      map.get(norm) ??
      (CURATED.includes(resolved) ? routeFor(resolved) : `${REPO_ROOT_URL}/${resolved}`);
    return `](${dest}${anchor ? `#${anchor}` : ''})`;
  });
};
