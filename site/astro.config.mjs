import { defineConfig } from 'astro/config';
import headingIds from './src/lib/heading-ids.mjs';

export default defineConfig({
  trailingSlash: 'always',
  markdown: {
    rehypePlugins: [headingIds],
  },
  vite: {
    server: {
      fs: { allow: ['..'] },
    },
  },
});
