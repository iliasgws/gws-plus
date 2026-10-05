import { readFileSync } from 'node:fs';
// @ts-ignore -- module JS partagé avec astro.config.mjs
import { routeFor } from './links.mjs';

export type DocItem = {
  id: string;
  repoPath: string;
  route: string;
  label: string;
  section: string;
};

type Section = { label: string; items: DocItem[] };

const item = (repoPath: string, label: string, section: string): DocItem => {
  const id = repoPath.replace(/^docs\//, '').replace(/\.md$/, '').toLowerCase();
  return { id, repoPath, route: routeFor(repoPath), label, section };
};

export const SECTIONS: Section[] = [
  { label: 'Index', items: [item('docs/README.md', 'Documentation', 'Index')] },
  {
    label: 'Produit',
    items: [
      item('docs/product/OVERVIEW.md', 'Vue d’ensemble', 'Produit'),
      item('docs/product/DESIGN.md', 'Conception « Le registre »', 'Produit'),
      item('docs/product/ROADMAP.md', 'Feuille de route', 'Produit'),
    ],
  },
  {
    label: 'Développement',
    items: [
      item('docs/development/SETUP.md', 'Installation', 'Développement'),
      item('docs/development/ARCHITECTURE.md', 'Architecture', 'Développement'),
      item('docs/development/NAVIGATION.md', 'Navigation', 'Développement'),
    ],
  },
  {
    label: 'API Boti',
    items: [
      item('docs/api/BOTI-API.md', 'Le protocole', 'API Boti'),
      item('docs/api/ENDPOINT-MAP.md', 'Carte des endpoints', 'API Boti'),
      item('docs/api/ENDPOINTS.md', 'Inventaire des endpoints', 'API Boti'),
    ],
  },
  {
    label: 'Sécurité',
    items: [item('docs/security/SECURITY-NOTES.md', 'Notes de sécurité', 'Sécurité')],
  },
];

export const ORDERED: DocItem[] = SECTIONS.flatMap((s) => s.items);

export const byId = (id: string): DocItem | undefined =>
  ORDERED.find((d) => d.id === id.toLowerCase());

export const neighbours = (id: string): { prev?: DocItem; next?: DocItem } => {
  const i = ORDERED.findIndex((d) => d.id === id);
  if (i < 0) return {};
  return { prev: ORDERED[i - 1], next: ORDERED[i + 1] };
};

export const slugify = (text: string): string =>
  text
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');

export const readDoc = (repoPath: string): string =>
  readFileSync(new URL(`../../../${repoPath}`, import.meta.url), 'utf8');

export const docTitle = (repoPath: string): string => {
  const firstLine = readDoc(repoPath)
    .split('\n')
    .find((line) => line.startsWith('# '));
  return firstLine ? firstLine.slice(2).trim() : repoPath;
};

export type TocEntry = { depth: number; text: string; slug: string };

export const extractToc = (repoPath: string): TocEntry[] => {
  const out: TocEntry[] = [];
  let inFence = false;
  for (const line of readDoc(repoPath).split('\n')) {
    if (/^\s*(```|~~~)/.test(line)) inFence = !inFence;
    if (inFence) continue;
    const m = /^(#{2,3})\s+(.+?)\s*#*$/.exec(line);
    if (m) out.push({ depth: m[1].length, text: m[2], slug: slugify(m[2]) });
  }
  return out;
};
