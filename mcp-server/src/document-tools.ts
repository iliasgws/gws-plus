import type { McpServer } from '@modelcontextprotocol/server';
import * as z from 'zod/v4';
import { downloadMedia, MediaDownloadError, safeFilename } from './media-download.js';
import { readOnlyTool } from './tool-metadata.js';

type GetData = (route: string, params?: Record<string, string>) => Promise<unknown>;
type RequireScope = (scope: string) => void;
type HasScope = (scope: string) => boolean;
type OwnerKey = () => string;
type MediaDownloader = typeof downloadMedia;
const resourceIdSchema = z.string().trim().regex(/^[A-Za-z0-9_-]{1,64}$/);

function dataObject(body: unknown): any {
  if (!body || typeof body !== 'object') return undefined;
  const data = (body as any).data;
  // Boti details are flat in current live responses; older probes wrapped them.
  return data && typeof data === 'object' && !Array.isArray(data) ? data : body;
}
function libraryFiles(body: unknown): { title: string; files: Array<{ filename: string; url: string; mime?: string }> } {
  const data = dataObject(body);
  if (!data || !Array.isArray(data.files)) throw new MediaDownloadError('not_found','Library document details are unavailable');
  const title = typeof data.title === 'string' ? data.title.slice(0,200) : 'Bibliothèque';
  const files = data.files.flatMap((file: any) => {
    if (!file || typeof file !== 'object') return [];
    const url = typeof file.link === 'string' ? file.link : typeof file.path === 'string' ? file.path : '';
    const mime = typeof file.mime?.type === 'string' ? file.mime.type.toLowerCase() : undefined;
    const filename = safeFilename(file.filename) ?? safeFilename(url);
    return filename && url ? [{ filename: mime === 'application/pdf' && !filename.toLowerCase().endsWith('.pdf') ? `${filename}.pdf` : filename, url, mime }] : [];
  });
  return { title, files };
}
function actualAttachmentUrl(raw: unknown): string | undefined {
  if (typeof raw !== 'string') return;
  if (!raw.includes('docs.google.com/viewer')) return raw;
  try {
    // Mirror MediaUrls.lienRéel: extract the raw query value, then decode
    // exactly once so signed path escapes remain intact.
    const value = /[?&]url=([^&]+)/.exec(raw)?.[1];
    return value ? decodeURIComponent(value) : undefined;
  } catch { return; }
}
function structuredError(error: unknown) {
  const e = error instanceof MediaDownloadError ? error : new MediaDownloadError('upstream_failure','The attachment could not be retrieved');
  return { isError: true, content: [{ type: 'text' as const, text: JSON.stringify({ error: e.code, message: e.message }) }] };
}

/** Register bounded, session-scoped media downloads for verified Boti resources. */
export function registerDocumentTools(server: McpServer, getData: GetData, requireScope: RequireScope, hasScope: HasScope = () => true, ownerKey: OwnerKey = () => 'default', downloader: MediaDownloader = downloadMedia): void {
  if (hasScope('documents:read')) server.registerTool('download_library_pdf', {
    ...readOnlyTool('Télécharger un PDF', ['documents:read']),
    description: 'Download one verified PDF attachment from a Bibliothèque document. The file is returned as an embedded PDF resource; signed source URLs are never returned.',
    inputSchema: { resource_id: resourceIdSchema, filename: z.string().trim().min(1).max(200).optional().describe('Optional exact filename when the document has more than one PDF.') },
  }, async ({ resource_id, filename }) => {
    requireScope('documents:read');
    try {
      const { title, files } = libraryFiles(await getData('ressource_details', { ressource: resource_id }));
      const pdfs = files.filter(file => file.mime === 'application/pdf' || file.filename.toLowerCase().endsWith('.pdf'));
      const selected = filename ? pdfs.find(file => file.filename.toLowerCase() === (safeFilename(filename) ?? '').toLowerCase()) : pdfs.length === 1 ? pdfs[0] : undefined;
      if (!selected) throw new MediaDownloadError('not_found','No matching PDF attachment was found');
      const file = await downloader(selected.url, selected.filename, selected.mime ?? 'application/pdf', ownerKey());
      return { content: [{ type: 'text' as const, text: JSON.stringify({ title, filename: file.filename }) }, { type: 'resource' as const, resource: { uri: `boti-library://${resource_id}/${encodeURIComponent(file.filename)}`, mimeType: file.mime, blob: file.bytes.toString('base64') } }] };
    } catch (e) { return structuredError(e); }
  });

  if (hasScope('homework:read')) server.registerTool('download_homework_attachment', {
    ...readOnlyTool('Télécharger une pièce jointe de devoir', ['homework:read']),
    description: 'Download one attachment from the current detail of a homework item accessible to the authenticated parent and selected student. attachment_index is the zero-based index in the source files[] array.',
    inputSchema: { homework_id: resourceIdSchema, attachment_index: z.number().int().min(0).max(100) },
  }, async ({ homework_id, attachment_index }) => {
    requireScope('homework:read');
    try {
      const detail = dataObject(await getData('devoirs', { devoir: homework_id }));
      if (!detail || String(detail.id ?? '') !== homework_id) throw new MediaDownloadError('not_found','Homework attachment was not found');
      // The probed single-devoir payload uses files[]; older/list-shaped payloads
      // expose one `file` object. Match the Android normalizer's supported shapes.
      const files = Array.isArray(detail.files) ? detail.files : detail.file && typeof detail.file === 'object' ? [detail.file] : [];
      const item = files[attachment_index];
      if (!item || typeof item !== 'object') throw new MediaDownloadError('not_found','Homework attachment was not found');
      const url = actualAttachmentUrl(item.link ?? item.path);
      if (!url) throw new MediaDownloadError('not_found','Homework attachment was not found');
      const name = item.filename ?? item.text ?? url;
      const file = await downloader(url, name, item.mime?.type ?? item.mime, ownerKey());
      return { content: [{ type: 'text' as const, text: JSON.stringify({ homework_id, attachment_index, filename: file.filename, mime: file.mime, size: file.bytes.length }) }, { type: 'resource' as const, resource: { uri: `boti-homework://${homework_id}/${attachment_index}/${encodeURIComponent(file.filename)}`, mimeType: file.mime, blob: file.bytes.toString('base64') } }] };
    } catch (e) { return structuredError(e); }
  });
}
