import { readdirSync, readFileSync } from 'node:fs';
import { join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildHrefMap, rewriteMarkdownLinks } from './links.mjs';

const DOCS_DIR = fileURLToPath(new URL('../../../docs/', import.meta.url));
const REPO_ROOT = resolve(DOCS_DIR, '..');

const walk = (dir) =>
  readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) return walk(full);
    return entry.name.endsWith('.md') ? [full] : [];
  });

export const docsLoader = () => ({
  name: 'gws-docs-loader',
  async load({ store, parseData, renderMarkdown, generateDigest, config }) {
    const map = buildHrefMap();
    const files = walk(DOCS_DIR);
    for (const file of files) {
      const absToRoot = relative(REPO_ROOT, file).split(sep).join('/');
      const raw = readFileSync(file, 'utf8');
      const body = rewriteMarkdownLinks(raw, absToRoot, map) ?? raw;
      const id = absToRoot.replace(/^docs\//, '').replace(/\.md$/, '');
      const data = await parseData({ id, data: {}, filePath: file });
      const rendered = await renderMarkdown(body);
      const rootPath = relative(fileURLToPath(config.root), file);
      store.set({
        id,
        data,
        body,
        filePath: rootPath,
        digest: generateDigest(body),
        rendered,
      });
    }
  },
});
