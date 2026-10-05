import { defineCollection } from 'astro:content';
import { docsLoader } from './lib/loader.mjs';

const docs = defineCollection({
  loader: docsLoader(),
});

export const collections = { docs };
