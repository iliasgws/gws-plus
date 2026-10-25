import { acceptedContent, inputRequired, inputResponse, type McpServer } from '@modelcontextprotocol/server';
import * as z from 'zod/v4';
import { readOnlyTool, writeTool } from './tool-metadata.js';
import { actionDigest, actionRequestState, consumeActionConfirmation, discardActionConfirmation } from './action-confirmation.js';

type Params = Record<string, string>;
type GetData = (route: string, params?: Params) => Promise<unknown>;
type PostData = (route: string, fields: Params) => Promise<unknown>;
type RequireScope = (scope: string) => void;
type HasScope = (scope: string) => boolean;
type MessageDraft = {
  mode: 'new' | 'reply'; recipient: string; threadId: string | null; subject: string;
  themeId: string; themeLabel: string; text: string; fields: Params;
};

const threadId = z.string().trim().min(1).max(128);
const textSchema = z.string().trim().min(1).max(20_000);
const inputSchema = {
  mode: z.enum(['new', 'reply']),
  thread_id: threadId.optional().describe('Required for reply; an id from list_conversations.'),
  subject: z.string().trim().min(1).max(200).optional().describe('Required for a new thread; replies use the existing thread subject.'),
  theme_id: z.string().trim().min(1).max(64).optional().describe('Required for a new thread; choose an id from the themes returned by messages.'),
  page: z.number().int().min(1).max(10_000).default(1).describe('Conversation page containing the reply thread.'),
  text: textSchema.describe('Plain text only. Attachments and voice messages are not supported.'),
};
const confirmationSchema = z.object({
  confirmation: z.enum(['SEND']).describe('Type SEND after reviewing the exact message details.'),
});

function object(value: unknown): Record<string, any> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, any> : {};
}
function entries(value: unknown): Record<string, any>[] {
  return Array.isArray(value) ? value.map(object) : [];
}
function scalar(value: unknown, label: string): string {
  if (typeof value !== 'string' && typeof value !== 'number') throw new Error(`Boti did not provide ${label}`);
  const result = String(value).trim();
  if (!result || result.length > 256) throw new Error(`Boti did not provide a usable ${label}`);
  return result;
}

/** Register plain-text school messaging. Every send requires a fresh client elicitation. */
export function registerMessageTools(server: McpServer, getData: GetData, postData: PostData, requireScope: RequireScope, hasScope: HasScope = () => true): void {
  const resolve = async (args: Record<string, any>, writing = false): Promise<MessageDraft> => {
    if (writing) requireScope('messages:write');
    requireScope('messages:read');
    const text = textSchema.parse(args.text);
    const mode = args.mode as 'new' | 'reply';
    const page = z.number().int().min(1).max(10_000).parse(args.page ?? 1);
    const data = object(await getData('messages', { page: String(page) }));
    const themes = entries(data.themes);

    if (mode === 'new') {
      if (args.thread_id) throw new Error('A new message cannot include a thread id');
      const subject = z.string().trim().min(1).max(200).parse(args.subject);
      const themeId = scalar(args.theme_id, 'message category');
      const theme = themes.find(item => String(item.id) === themeId);
      if (!theme) throw new Error('Choose a message category currently returned by the school');
      return {
        mode, recipient: 'Administration de l’école', threadId: null, subject,
        themeId, themeLabel: scalar(theme.label, 'category name'), text,
        fields: { sujet: subject, message: text, theme: themeId },
      };
    }

    if (args.subject || args.theme_id) throw new Error('Replies use the existing thread subject and category');
    const requestedId = threadId.parse(args.thread_id);
    const conversation = entries(data.data).find(item => String(item.id) === requestedId);
    if (!conversation) throw new Error('Thread was not found on this conversation page; refresh list_conversations and retry');
    const subject = scalar(conversation.sujet, 'thread subject');
    const themeId = scalar(conversation.theme, 'thread category');
    const theme = themes.find(item => String(item.id) === themeId);
    const messages = entries(conversation.conversation);
    return {
      mode, recipient: scalar(conversation.to ?? conversation.at, 'thread recipient'), threadId: requestedId,
      subject, themeId, themeLabel: theme && typeof theme.label === 'string' ? theme.label : themeId,
      text, fields: { ref: requestedId, sujet: subject, message: text, theme: themeId, index: String(messages.length) },
    };
  };

  if (hasScope('messages:read')) server.registerTool('prepare_message', {
    ...readOnlyTool('Préparer un message', ['messages:read']),
    description: 'Preview a plain-text new school message or reply. Does not send anything; use send_message for a separately confirmed send.',
    inputSchema,
  }, async args => {
    const draft = await resolve(args as Record<string, any>);
    return { content: [{ type: 'text', text: JSON.stringify({ recipient: draft.recipient, thread_id: draft.threadId, subject: draft.subject, category: draft.themeLabel, category_id: draft.themeId, text: draft.text, sent: false }) }] };
  });

  if (hasScope('messages:read') && hasScope('messages:write')) server.registerTool('send_message', {
    ...writeTool('Envoyer un message', ['messages:read', 'messages:write'], { destructive: true }),
    description: 'Send a plain-text school message or reply. Requires fresh human MCP elicitation for the exact recipient, thread, subject, category, and final text. Clients without elicitation support cannot send.',
    inputSchema,
  }, async (args, ctx) => {
    const draft = await resolve(args as Record<string, any>, true);
    const digest = actionDigest('send_message', draft);
    const response = inputResponse(ctx.mcpReq.inputResponses, 'send_confirmation');
    if (response.kind === 'elicit' && response.action !== 'accept') {
      discardActionConfirmation(ctx, 'send_message', digest);
      return { content: [{ type: 'text', text: 'Confirmation declined or canceled. No message was sent.' }] };
    }
    const requestState = await actionRequestState(ctx, 'send_message', digest);
    const accepted = acceptedContent(ctx.mcpReq.inputResponses, 'send_confirmation', confirmationSchema);
    if (accepted?.confirmation !== 'SEND') {
      return inputRequired({ inputRequests: { send_confirmation: inputRequired.elicit({
        message: `Confirm sending this exact message?\nRecipient: ${draft.recipient}\nThread: ${draft.threadId ?? 'new thread'}\nSubject: ${draft.subject}\nCategory: ${draft.themeLabel}\nFinal text:\n${draft.text}\nType SEND to authorize this one message.`,
        requestedSchema: confirmationSchema,
      }) }, requestState });
    }
    const freshDraft = await resolve(args as Record<string, any>, true);
    if (JSON.stringify(draft) !== JSON.stringify(freshDraft)) throw new Error('The thread, category, or message details changed during confirmation; review and confirm again');
    consumeActionConfirmation(ctx, 'send_message', digest);
    const result = await postData('nouveau-message', freshDraft.fields);
    return { content: [{ type: 'text', text: JSON.stringify({ sent: true, recipient: freshDraft.recipient, thread_id: freshDraft.threadId, subject: freshDraft.subject, category: freshDraft.themeLabel, result }) }] };
  });
}
