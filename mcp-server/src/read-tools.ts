import type { McpServer } from '@modelcontextprotocol/server';
import * as z from 'zod/v4';
import { readOnlyTool } from './tool-metadata.js';

type DataParams = Record<string, string>;
type GetData = (route: string, params?: DataParams) => Promise<unknown>;
type RequireScope = (scope: string) => void;
type HasScope = (scope: string) => boolean;

const MAX_RESULT_CHARS = 1_000_000;
const id = z.string().trim().min(1).max(64);
const page = z.number().int().min(1).max(10_000).default(1);
const start = z.number().int().min(0).max(10_000).default(0);
const smallLimit = z.number().int().min(1).max(50).default(10);

/** Register verified, read-only Boti API tools. */
export function registerReadTools(server: McpServer, getData: GetData, requireScope: RequireScope, hasScope: HasScope = () => true): void {
  const register = (
    name: string,
    title: string,
    description: string,
    scope: string,
    inputSchema: Record<string, any>,
    read: (args: Record<string, any>) => Promise<unknown>
  ) => {
    if (!hasScope(scope)) return;
    server.registerTool(name, { description, inputSchema, ...readOnlyTool(title, [scope]) }, async (args) => {
      requireScope(scope);
      const result = await read(args as Record<string, any>);
      const text = JSON.stringify(result ?? null);
      if (text.length > MAX_RESULT_CHARS) throw new Error(`Read result exceeds ${MAX_RESULT_CHARS} characters`);
      return { content: [{ type: 'text', text }] };
    });
  };

  // Existing tools are kept compatible with the original server API.
  register('get_homework', 'Devoirs', 'Read the authenticated parent’s homework list from Greenwood School.', 'homework:read', {
    start: z.number().int().min(0).max(10_000).default(0),
    limit: z.number().int().min(1).max(100).default(30),
  }, ({ start, limit }) => getData('devoirs', { start: String(start), limit: String(limit) }));

  register('get_document_units', 'Matières de la Bibliothèque', 'List the authenticated parent’s library subjects.', 'documents:read', {},
    () => getData('bibliotheque'));

  register('list_documents', 'Documents d’une matière', 'List library documents for one subject unit ID returned by get_document_units.', 'documents:read', {
    unit_id: id,
    start,
    limit: smallLimit,
  }, ({ unit_id, start, limit }) => getData('bibliotheque', { unite: unit_id, start: String(start), limit: String(limit) }));

  register('get_document_details', 'Détail d’un document', 'Read details and currently signed file URLs for a library resource. Signed URLs expire and are not stable links.', 'documents:read', {
    resource_id: id,
  }, ({ resource_id }) => getData('ressource_details', { ressource: resource_id }));

  register('get_homework_details', 'Détail d’un devoir', 'Read one homework item and its attachments/status from the authenticated parent’s account.', 'homework:read', {
    homework_id: id,
  }, ({ homework_id }) => getData('devoirs', { devoir: homework_id }));

  register('list_attendance_records', 'Absences et retards', 'Read the selected student’s raw attendance and lateness response, paged by the school. Item fields have not been verified on a non-empty account; do not assume the response is normalized or complete.', 'attendance:read', {
    page,
  }, ({ page }) => getData('absences', { page: String(page) }));

  register('get_timetable', 'Emploi du temps', 'Read the authenticated student’s weekly class timetable. Supply the server-returned last_week or next_week value to navigate weeks.', 'courses:read', {
    last_week: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional(),
    next_week: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional(),
  }, ({ last_week, next_week }) => {
    if (last_week && next_week) throw new Error('Pass at most one week navigation parameter');
    const params: DataParams = {};
    if (last_week) params.last_week = last_week;
    if (next_week) params.next_week = next_week;
    return getData('cours_v2', params);
  });

  register('list_interactive_resources', 'Ressources interactives', 'List verified interactive learning resources (quiz entries) from the authenticated parent’s document space. Other resource types are not verified.', 'documents:read', {
    search: z.string().max(200).default(''),
  }, ({ search }) => getData('ressources_v2', { search }));

  register('list_quizzes', 'Quiz', 'List available quizzes for the authenticated student. Pagination uses the verified start and limit parameters.', 'documents:read', {
    start,
    limit: smallLimit,
  }, ({ start, limit }) => getData('quiz', { start: String(start), limit: String(limit) }));

  register('get_quiz_details', 'Détail d’un quiz', 'Read one quiz and its questions. This is read-only; it does not submit or record a play.', 'documents:read', {
    quiz_id: id,
  }, ({ quiz_id }) => getData('quiz', { quiz_id }));

  register('list_news', 'Actualités', 'List school news cards for the authenticated parent. This returns list summaries, not full post bodies.', 'news:read', {
    start,
    limit: smallLimit,
  }, ({ start, limit }) => getData('nouveautes', { start: String(start), limit: String(limit) }));

  register('get_news_post', 'Actualité', 'Read one school news post with its full body and verified detail fields.', 'news:read', {
    post_id: id,
  }, ({ post_id }) => getData('post_view', { post: post_id }));

  register('list_conversations', 'Conversations', 'List the authenticated parent’s school-administration conversations and message pages. This tool does not send messages.', 'messages:read', {
    page,
  }, ({ page }) => getData('messages', { page: String(page) }));

  register('list_administrative_requests', 'Demandes administratives', 'List the authenticated parent’s administrative requests and their recorded responses.', 'requests:read', {
    page,
  }, ({ page }) => getData('demandes', { page: String(page) }));

  register('get_school_contact', 'Coordonnées de l’école', 'Read the school’s published contact details and social links.', 'school:read', {},
    () => getData('contact'));

  register('list_shop_products', 'Boutique', 'List shop products or guest-meal planning for a rubric. Pass both rubrique and search; rubrique 2 is the verified guest-meal schedule.', 'shop:read', {
    rubrique: z.string().regex(/^(?:-1|\d{1,32})$/).default('-1'),
    search: z.string().max(200).default(''),
  }, ({ rubrique, search }) => getData('shop', { rubrique, search }));

  register('get_guest_meal_schedule', 'Repas invité', 'Read the verified guest-meal schedule from shop rubric 2. This tool does not reserve or order meals.', 'shop:read', {},
    () => getData('shop', { rubrique: '2', search: '' }));

  register('get_shop_product', 'Produit de la boutique', 'Read one shop product’s detail, variants, prices, and availability.', 'shop:read', {
    product_id: id,
  }, ({ product_id }) => getData('shop', { product: product_id }));

  register('get_shop_order_history', 'Historique des commandes', 'Read the authenticated parent’s shop order history. This does not create, edit, or delete orders.', 'shop:read', {},
    () => getData('shop', { new_history: 'true' }));
}
