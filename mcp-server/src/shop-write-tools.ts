import { acceptedContent, inputRequired, inputResponse, type McpServer } from '@modelcontextprotocol/server';
import * as z from 'zod/v4';
import { actionDigest, actionRequestState, consumeActionConfirmation, discardActionConfirmation } from './action-confirmation.js';

type Params = Record<string, string>;
type GetData = (route: string, params?: Params) => Promise<unknown>;
type PostData = (route: string, fields: Params) => Promise<unknown>;
type RequireScope = (scope: string) => void;
type HasScope = (scope: string) => boolean;
type Row = Record<string, any>;

const id = z.string().trim().min(1).max(128);
const comment = z.string().max(1000).default('');
const confirmationSchema = z.object({ confirmation: z.enum(['CONFIRM']).describe('Type CONFIRM after reviewing this exact shop action.') });

function obj(value: unknown): Row { return value && typeof value === 'object' && !Array.isArray(value) ? value as Row : {}; }
function rows(value: unknown): Row[] { return Array.isArray(value) ? value.map(obj) : []; }
function scalar(value: unknown, label: string): string {
  if (typeof value !== 'string' && typeof value !== 'number') throw new Error(`The school did not provide ${label}`);
  const text = String(value).trim();
  if (!text || text.length > 256) throw new Error(`The school did not provide a usable ${label}`);
  return text;
}
function historyRows(value: unknown): Row[] { return rows(obj(value).products); }

/** Register immediate boutique actions. Each POST is preceded by a fresh human elicitation. */
export function registerShopWriteTools(server: McpServer, getData: GetData, postData: PostData, requireScope: RequireScope, hasScope: HasScope = () => true): void {
  const gate = () => { requireScope('shop:write'); requireScope('shop:read'); };

  const orderInput = {
    product_id: id.describe('Product id from list_shop_products or get_shop_product.'),
    variant_id: id.optional().describe('Current variant id from get_shop_product; required when product has variants.'),
    size: z.string().max(128).optional().describe('Current size label; when variant_id is supplied it must match that variant.'),
    quantity: z.number().int().min(1).max(100),
    comment: comment.describe('Order comment, including the meal date when ordering a guest meal.'),
    order_id: id.optional().describe('Existing order id for editing; omit to create a new order.'),
  };

  const resolveOrder = async (args: Record<string, any>) => {
    gate();
    const productId = id.parse(args.product_id);
    const orderId = args.order_id === undefined ? undefined : id.parse(args.order_id);
    const quantity = z.number().int().min(1).max(100).parse(args.quantity);
    const note = comment.parse(args.comment ?? '');

    let currentOrder: Row | undefined;
    let article: Row | undefined;
    if (orderId) {
      currentOrder = historyRows(await getData('shop', { new_history: 'true' })).find(row => String(row.id) === orderId);
      if (!currentOrder) throw new Error('Order not found in the current order history; refresh and retry');
      const matches = rows(currentOrder.articles).filter(row => String(row.product_id) === productId);
      if (matches.length !== 1) throw new Error('The order must contain exactly one matching product article to edit safely');
      article = matches[0];
      if (article.can_edit !== true) throw new Error('The school does not currently allow editing this article');
    }

    const detail = obj(await getData('shop', { product: productId, ...(orderId ? { commande: orderId } : {}) }));
    const product = obj(detail.product);
    if (String(product.id ?? '') !== productId) throw new Error('The school returned a different product; refusing to order');
    const label = scalar(product.label, 'product name');
    if (label.toLocaleLowerCase('fr').includes('repas invité') && !/(?:\d{4}-\d{2}-\d{2}|\d{2}\/\d{2}\/\d{4})/.test(note)) {
      throw new Error('Include the guest meal date in the order comment, for example 23/09/2026');
    }
    if (orderId && (!detail.commande || obj(detail.commande).qte === undefined)) throw new Error('The school did not return current editable order fields');
    if (!orderId && product.can_add_to_cart !== true) throw new Error('The school does not currently allow ordering this product');

    const variants = rows(detail.variants);
    let selected: Row | undefined;
    let size = '';
    let price: string;
    if (variants.length) {
      if (!args.variant_id) throw new Error('Choose a current variant from get_shop_product');
      selected = variants.find(row => String(row.id) === String(args.variant_id));
      if (!selected) throw new Error('That variant is no longer offered; refresh the product and retry');
      size = scalar(selected.label ?? selected.value, 'variant label');
      if (args.size !== undefined && args.size !== size) throw new Error('The supplied size does not match the current variant');
      const stock = Number(selected.qte);
      if (Number.isFinite(stock) && stock >= 0 && quantity > stock) throw new Error('The requested quantity exceeds current stock');
      price = scalar(selected.amount, 'current variant price');
    } else {
      if (args.variant_id || args.size) throw new Error('This product has no current variants or sizes');
      price = scalar(product.price, 'current product price');
    }

    if (orderId) {
      if (String(article?.product_id ?? '') !== productId) throw new Error('Order product binding changed; refusing to edit');
      const currentPrefill = obj(detail.commande);
      // The GET order-detail contract returns size/quantity/comment; require the order article to
      // remain explicitly editable and the product's current variant/price to resolve above.
      if (currentPrefill.qte === undefined || currentPrefill.size === undefined) throw new Error('Current edit fields are incomplete');
    }
    return {
      productId, orderId, quantity, note, label, size, price,
      variantId: selected ? scalar(selected.id, 'variant id') : undefined,
      mode: orderId ? 'edit' as const : 'create' as const,
      fields: {
        product: productId, ...(orderId ? { commande: orderId } : {}),
        ...(selected ? { variants: scalar(selected.id, 'variant id') } : {}),
        size, quantity: String(quantity), comment: note, price,
      },
    };
  };

  if (hasScope('shop:read') && hasScope('shop:write')) server.registerTool('place_shop_order', {
    description: 'Create or edit an immediate school shop order. Rechecks current product/variant/price and edit permission. Requires fresh elicitation for the exact action.',
    inputSchema: orderInput,
  }, async (raw, ctx) => {
    const draft = await resolveOrder(raw as Record<string, any>);
    const digest = actionDigest('place_shop_order', draft);
    const confirmationResponse = inputResponse(ctx.mcpReq.inputResponses, 'shop_order_confirmation');
    if (confirmationResponse.kind === 'elicit' && confirmationResponse.action !== 'accept') {
      discardActionConfirmation(ctx, 'place_shop_order', digest);
      return { content: [{ type: 'text', text: 'Confirmation declined or canceled. No order was submitted.' }] };
    }
    const requestState = await actionRequestState(ctx, 'place_shop_order', digest);
    const accepted = acceptedContent(ctx.mcpReq.inputResponses, 'shop_order_confirmation', confirmationSchema);
    if (accepted?.confirmation !== 'CONFIRM') return inputRequired({ inputRequests: { shop_order_confirmation: inputRequired.elicit({
      message: `Confirm this ${draft.mode} order?\nProduct: ${draft.label}\nVariant/size: ${draft.size || 'none'}\nQuantity: ${draft.quantity}\nUnit price: ${draft.price}\nComment/day: ${draft.note || 'none'}\nOrder id: ${draft.orderId ?? 'new order'}\nThis action is submitted to the school immediately. Type CONFIRM to proceed.`, requestedSchema: confirmationSchema,
    }) }, requestState });
    // Re-fetch after the user's confirmation so stale stock, price, or permission cannot be used.
    const fresh = await resolveOrder(raw as Record<string, any>);
    if (JSON.stringify({ ...draft, fields: undefined }) !== JSON.stringify({ ...fresh, fields: undefined })) throw new Error('The product, price, stock, or order changed during confirmation; refresh and confirm again');
    consumeActionConfirmation(ctx, 'place_shop_order', digest);
    const result = await postData('shop', fresh.fields);
    const response = obj(result);
    if (response.error === true || response.alert_classe !== 'success_alert_boti') throw new Error('The school did not confirm the order; no success was reported');
    return { content: [{ type: 'text', text: JSON.stringify({ submitted: true, action: fresh.mode, product: fresh.label, variant: fresh.size, quantity: fresh.quantity, unit_price: fresh.price, comment: fresh.note, order_id: fresh.orderId ?? null, result }) }] };
  });

  const deleteInput = {
    order_id: id.describe('Order id from get_shop_order_history.'),
    article_id: id.optional().describe('Article id from that order; omit to delete the whole order.'),
  };
  if (hasScope('shop:read') && hasScope('shop:write')) server.registerTool('delete_shop_order', {
    description: 'Delete an order or one article from it. Re-fetches order history and checks server can_delete permissions. Requires fresh elicitation.',
    inputSchema: deleteInput,
  }, async (raw, ctx) => {
    gate();
    const args = raw as Record<string, any>;
    const orderId = id.parse(args.order_id);
    const articleId = args.article_id === undefined ? undefined : id.parse(args.article_id);
    const resolveDelete = async () => {
      const order = historyRows(await getData('shop', { new_history: 'true' })).find(row => String(row.id) === orderId);
      if (!order) throw new Error('Order not found in the current order history; refresh and retry');
      const articles = rows(order.articles);
      if (articleId) {
        const item = articles.find(row => String(row.id) === articleId);
        if (!item) throw new Error('Article not found in that order');
        if (item.can_delete !== true) throw new Error('The school does not currently allow deleting this article');
        return { order, items: [item] };
      }
      if (order.can_delete !== true) throw new Error('The school does not currently allow deleting this order');
      if (!articles.length) throw new Error('The order has no readable articles; refusing an ambiguous deletion');
      return { order, items: articles };
    };
    const initial = await resolveDelete();
    const summary = async (state: typeof initial) => Promise.all(state.items.map(async item => {
      const productId = scalar(item.product_id, 'product id');
      const detail = obj(await getData('shop', { product: productId, commande: orderId }));
      if (String(obj(detail.product).id ?? '') !== productId) throw new Error('The school returned a different product; refusing deletion');
      const prefill = obj(detail.commande);
      if (prefill.qte === undefined || prefill.size === undefined || prefill.comment === undefined) throw new Error('Current order details are incomplete; refusing deletion');
      return {
        article_id: scalar(item.id, 'article id'), product: scalar(item.label, 'article name'),
        variant: item.size == null ? 'none' : String(item.size), quantity: scalar(item.quantity, 'article quantity'),
        unit_price: scalar(item.price, 'article unit price'), comment_day: String(prefill.comment || 'none'),
      };
    }));
    const before = await summary(initial);
    const digest = actionDigest('delete_shop_order', { orderId, articleId, items: before });
    const confirmationResponse = inputResponse(ctx.mcpReq.inputResponses, 'shop_delete_confirmation');
    if (confirmationResponse.kind === 'elicit' && confirmationResponse.action !== 'accept') {
      discardActionConfirmation(ctx, 'delete_shop_order', digest);
      return { content: [{ type: 'text', text: 'Confirmation declined or canceled. Nothing was deleted.' }] };
    }
    const requestState = await actionRequestState(ctx, 'delete_shop_order', digest);
    const accepted = acceptedContent(ctx.mcpReq.inputResponses, 'shop_delete_confirmation', confirmationSchema);
    if (accepted?.confirmation !== 'CONFIRM') return inputRequired({ inputRequests: { shop_delete_confirmation: inputRequired.elicit({
      message: `Confirm ${articleId ? 'deleting this article' : 'deleting the whole order'}?\nOrder: ${orderId}\nItems: ${JSON.stringify(before)}\nThis deletion is submitted to the school immediately. Type CONFIRM to proceed.`, requestedSchema: confirmationSchema,
    }) }, requestState });
    const fresh = await resolveDelete();
    if (JSON.stringify(await summary(fresh)) !== JSON.stringify(before)) throw new Error('The order changed during confirmation; refresh and confirm again');
    consumeActionConfirmation(ctx, 'delete_shop_order', digest);
    const result = await postData('shop', { commande: orderId, ...(articleId ? { detailsId: articleId } : {}), delete_history: 'true' });
    if (obj(result).error === true) throw new Error('The school reported that deletion failed');
    return { content: [{ type: 'text', text: JSON.stringify({ submitted: true, action: articleId ? 'delete_article' : 'delete_order', order_id: orderId, article_id: articleId ?? null, items: before, result }) }] };
  });
}
