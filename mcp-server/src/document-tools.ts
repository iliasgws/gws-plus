import type { McpServer } from '@modelcontextprotocol/server';
import * as z from 'zod/v4';

type GetData = (route: string, params?: Record<string, string>) => Promise<unknown>;
type RequireScope = (scope: string) => void;

const MAX_PDF_BYTES = 15 * 1024 * 1024;
const PDF_HOST = 'media.boti.education';
const resourceIdSchema = z.string().trim().regex(/^[A-Za-z0-9_-]{1,64}$/);

function safeFilename(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined;
  let candidate = value;
  try {
    if (/^https?:\/\//i.test(value)) candidate = new URL(value).pathname;
  } catch { return undefined; }
  try { candidate = decodeURIComponent(candidate); } catch { /* retain the literal filename */ }
  const leaf = candidate.replaceAll('\\', '/').split('/').at(-1)?.trim() ?? '';
  if (!leaf || leaf === '.' || leaf === '..') return undefined;
  const cleaned = leaf.normalize('NFKC').replace(/[^\p{L}\p{N}._ -]/gu, '_').replace(/[. ]+$/g, '').slice(0, 160);
  return cleaned || undefined;
}

function botiFiles(body: unknown): { title: string; files: Array<{ filename: string; url: string; mime?: string }> } {
  if (!body || typeof body !== 'object') throw new Error('Library document details are unavailable');
  const data = (body as any).data;
  if (!data || typeof data !== 'object' || !Array.isArray(data.files)) throw new Error('Library document has no verified files');
  const title = typeof data.title === 'string' ? data.title.slice(0, 200) : 'Bibliothèque';
  const files = data.files.flatMap((file: any) => {
    if (!file || typeof file !== 'object') return [];
    const url = typeof file.link === 'string' ? file.link : typeof file.path === 'string' ? file.path : '';
    const mime = typeof file.mime?.type === 'string' ? file.mime.type.toLowerCase() : undefined;
    const originalName = safeFilename(file.filename) ?? safeFilename(url);
    const filename = originalName && mime === 'application/pdf' && !originalName.toLowerCase().endsWith('.pdf')
      ? `${originalName}.pdf`.slice(0, 160)
      : originalName;
    return filename && url ? [{ filename, url, mime }] : [];
  });
  return { title, files };
}

function validatedMediaUrl(raw: string): URL {
  let url: URL;
  try { url = new URL(raw); } catch { throw new Error('The library file URL is invalid'); }
  // Check the original authority too: URL normalizes an explicit :443 away.
  const authority = /^https:\/\/([^/?#]+)/i.exec(raw)?.[1];
  if (url.protocol !== 'https:' || url.hostname !== PDF_HOST || !url.pathname.startsWith('/view/') ||
      url.username || url.password || url.port || authority !== PDF_HOST) {
    throw new Error('The library file is not hosted on the approved media server');
  }
  return url;
}

async function downloadPdf(url: URL, filename: string, declaredMime?: string): Promise<Buffer> {
  const response = await fetch(url, {
    method: 'GET',
    redirect: 'error',
    headers: { accept: 'application/pdf' },
    signal: AbortSignal.timeout(30_000),
  });
  if (!response.ok) throw new Error(`Library PDF download failed with HTTP ${response.status}`);
  const contentType = response.headers.get('content-type')?.split(';', 1)[0]?.trim().toLowerCase();
  if (contentType !== 'application/pdf' && declaredMime !== 'application/pdf' && !filename.toLowerCase().endsWith('.pdf')) {
    throw new Error('The selected library file is not a PDF');
  }
  const length = response.headers.get('content-length');
  if (length && /^\d+$/.test(length) && Number(length) > MAX_PDF_BYTES) {
    await response.body?.cancel();
    throw new Error('The library PDF exceeds the 15 MiB limit');
  }
  if (!response.body) throw new Error('The library PDF response has no body');
  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      total += value.byteLength;
      if (total > MAX_PDF_BYTES) {
        await reader.cancel();
        throw new Error('The library PDF exceeds the 15 MiB limit');
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }
  const bytes = Buffer.concat(chunks.map(chunk => Buffer.from(chunk)), total);
  if (bytes.length < 5 || bytes.subarray(0, 5).toString('ascii') !== '%PDF-') {
    throw new Error('The downloaded file did not have a valid PDF signature');
  }
  return bytes;
}

/** Register the bounded PDF download tool for verified Bibliothèque resources. */
export function registerDocumentTools(server: McpServer, getData: GetData, requireScope: RequireScope): void {
  server.registerTool('download_library_pdf', {
    description: 'Download one verified PDF attachment from a Bibliothèque document. The file is returned as an embedded PDF resource; signed source URLs are never returned.',
    inputSchema: {
      resource_id: resourceIdSchema,
      filename: z.string().trim().min(1).max(200).optional().describe('Optional exact filename when the document has more than one PDF.'),
    },
  }, async ({ resource_id, filename }) => {
    requireScope('documents:read');
    const details = await getData('ressource_details', { ressource: resource_id });
    const { title, files } = botiFiles(details);
    const pdfs = files.filter(file => file.mime === 'application/pdf' || file.filename.toLowerCase().endsWith('.pdf'));
    const selected = filename
      ? pdfs.find(file => file.filename.toLowerCase() === (safeFilename(filename) ?? '').toLowerCase())
      : pdfs.length === 1 ? pdfs[0] : undefined;
    if (!selected) throw new Error(pdfs.length > 1 && !filename ? 'This document has multiple PDFs; supply the filename.' : 'No matching PDF attachment was found');

    const bytes = await downloadPdf(validatedMediaUrl(selected.url), selected.filename, selected.mime);
    const uri = `boti-library://${resource_id}/${encodeURIComponent(selected.filename)}`;
    return {
      content: [
        { type: 'text' as const, text: JSON.stringify({ title, filename: selected.filename }) },
        { type: 'resource' as const, resource: { uri, mimeType: 'application/pdf', blob: bytes.toString('base64') } },
      ],
    };
  });
}
