import { acceptedContent, inputRequired, inputResponse, type McpServer } from '@modelcontextprotocol/server';
import * as z from 'zod/v4';
import { decrypt, db, encrypt, fingerprint, opaque } from './store.js';
import { actionDigest, actionRequestState, consumeActionConfirmation, discardActionConfirmation } from './action-confirmation.js';

type RequireScope = (scope: string) => void;
type HasScope = (scope: string) => boolean;
const timestamp = () => Math.floor(Date.now() / 1000);
const reminderId = z.string().trim().min(1).max(128);
const titleSchema = z.string().trim().min(1).max(160);
const noteSchema = z.string().trim().max(1_000).optional();
const dueAtSchema = z.string().trim().max(40).refine(value =>
  /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?(?:Z|[+-]\d{2}:\d{2})$/.test(value) && Number.isFinite(Date.parse(value)),
  'Use an RFC 3339 timestamp with a timezone, for example 2026-11-03T18:30:00+01:00.');
const confirmationSchema = z.object({ confirmation: z.enum(['SCHEDULE']) });

type StoredReminder = {
  reminder_id: string;
  title_cipher: string;
  note_cipher: string | null;
  due_at: number;
  created_at: number;
  completed_at: number | null;
  canceled_at: number | null;
};

/** Local MCP reminders are private to the selected student and this linked account. */
export function registerReminderTools(
  server: McpServer,
  accountId: string,
  studentId: string,
  studentName: string,
  requireScope: RequireScope,
  hasScope: HasScope = () => true,
): void {
  const studentHash = fingerprint(`reminder-student:${accountId}:${studentId}`);
  const load = (id: string): StoredReminder | undefined => db.prepare(`
    SELECT reminder_id,title_cipher,note_cipher,due_at,created_at,completed_at,canceled_at
    FROM reminders WHERE reminder_id=? AND account_id=? AND student_hash=?
  `).get(id, accountId, studentHash) as StoredReminder | undefined;

  if (hasScope('reminders:write') && hasScope('reminders:read')) server.registerTool('schedule_reminder', {
    description: 'Save a private reminder for the selected student. Requires a fresh human confirmation. It is shown when an agent calls list_reminders; this MCP service does not send push notifications, SMS, email, or proactive alerts.',
    inputSchema: {
      title: titleSchema.describe('Short reminder title.'),
      due_at: dueAtSchema.describe('RFC 3339 date/time with explicit timezone offset.'),
      note: noteSchema.describe('Optional private reminder note.'),
    },
  }, async (args, ctx) => {
    requireScope('reminders:write');
    requireScope('reminders:read');
    const title = titleSchema.parse(args.title);
    const dueAtInput = dueAtSchema.parse(args.due_at);
    const note = noteSchema.parse(args.note);
    const dueAt = Math.floor(Date.parse(dueAtInput) / 1000);
    const current = timestamp();
    if (dueAt < current + 60) throw new Error('Choose a reminder time at least one minute in the future');
    if (dueAt > current + 5 * 365 * 24 * 60 * 60) throw new Error('Reminder time must be within five years');

    const digest = actionDigest('schedule_reminder', { title, dueAt, note, studentHash });
    const response = inputResponse(ctx.mcpReq.inputResponses, 'reminder_confirmation');
    if (response.kind === 'elicit' && response.action !== 'accept') {
      discardActionConfirmation(ctx, 'schedule_reminder', digest);
      return { content: [{ type: 'text', text: 'Confirmation declined or canceled. No reminder was saved.' }] };
    }
    const requestState = await actionRequestState(ctx, 'schedule_reminder', digest);
    const confirmation = acceptedContent(ctx.mcpReq.inputResponses, 'reminder_confirmation', confirmationSchema);
    if (confirmation?.confirmation !== 'SCHEDULE') {
      return inputRequired({ inputRequests: { reminder_confirmation: inputRequired.elicit({
        message: `Save this reminder for ${studentName}?\nTitle: ${title}\nWhen: ${new Date(dueAt * 1000).toISOString()}\nNote: ${note || '(none)'}\n\nThis service stores the reminder and can show it when an agent checks the reminder list. It does not send notifications. Type SCHEDULE to save it.`,
        requestedSchema: confirmationSchema,
      }) }, requestState });
    }

    consumeActionConfirmation(ctx, 'schedule_reminder', digest);
    const activeCount = db.prepare(`SELECT COUNT(*) AS count FROM reminders
      WHERE account_id=? AND student_hash=? AND completed_at IS NULL AND canceled_at IS NULL`)
      .get(accountId, studentHash) as { count: number };
    if (activeCount.count >= 500) throw new Error('This student already has 500 active reminders; complete or cancel one before adding another');
    const id = opaque(18);
    db.prepare(`INSERT INTO reminders(reminder_id,account_id,student_hash,title_cipher,note_cipher,due_at,created_at)
      VALUES(?,?,?,?,?,?,?)`).run(id, accountId, studentHash, encrypt(title), note ? encrypt(note) : null, dueAt, current);
    return { content: [{ type: 'text', text: JSON.stringify({ saved: true, reminder_id: id, title, due_at: new Date(dueAt * 1000).toISOString(), student: studentName, delivery: 'Available when an agent calls list_reminders; no proactive notification is sent.' }) }] };
  });

  if (hasScope('reminders:read')) server.registerTool('list_reminders', {
    description: 'List reminders for the selected student in this linked account. Due reminders are marked due; the agent must call this tool to retrieve them because the service has no push-notification channel.',
    inputSchema: {
      view: z.enum(['active', 'due', 'all']).default('active'),
      limit: z.number().int().min(1).max(100).default(50),
      offset: z.number().int().min(0).max(10_000).default(0),
    },
  }, async args => {
    requireScope('reminders:read');
    const view = z.enum(['active', 'due', 'all']).parse(args.view ?? 'active');
    const limit = z.number().int().min(1).max(100).parse(args.limit ?? 50);
    const offset = z.number().int().min(0).max(10_000).parse(args.offset ?? 0);
    const rows = db.prepare(`SELECT reminder_id,title_cipher,note_cipher,due_at,created_at,completed_at,canceled_at
      FROM reminders WHERE account_id=? AND student_hash=?
      ${view === 'active' ? 'AND completed_at IS NULL AND canceled_at IS NULL' : ''}
      ${view === 'due' ? 'AND completed_at IS NULL AND canceled_at IS NULL AND due_at<=?' : ''}
      ORDER BY due_at ASC LIMIT ? OFFSET ?`).all(
        ...(view === 'due' ? [accountId, studentHash, timestamp(), limit, offset] : [accountId, studentHash, limit, offset]),
      ) as StoredReminder[];
    const now = timestamp();
    const reminders = rows.map(row => ({
      reminder_id: row.reminder_id,
      title: decrypt(row.title_cipher),
      note: row.note_cipher ? decrypt(row.note_cipher) : null,
      due_at: new Date(row.due_at * 1000).toISOString(),
      status: row.completed_at ? 'completed' : row.canceled_at ? 'canceled' : row.due_at <= now ? 'due' : 'upcoming',
      created_at: new Date(row.created_at * 1000).toISOString(),
    }));
    return { content: [{ type: 'text', text: JSON.stringify({ student: studentName, reminders, limit, offset }) }] };
  });

  if (hasScope('reminders:write')) server.registerTool('cancel_reminder', {
    description: 'Cancel one active reminder belonging to the selected student.',
    inputSchema: { reminder_id: reminderId.describe('ID from list_reminders.') },
  }, async args => {
    requireScope('reminders:write');
    const id = reminderId.parse(args.reminder_id);
    const reminder = load(id);
    if (!reminder || reminder.completed_at || reminder.canceled_at) throw new Error('Active reminder not found for the selected student');
    db.prepare('UPDATE reminders SET canceled_at=? WHERE reminder_id=? AND account_id=? AND student_hash=? AND completed_at IS NULL AND canceled_at IS NULL')
      .run(timestamp(), id, accountId, studentHash);
    return { content: [{ type: 'text', text: JSON.stringify({ canceled: true, reminder_id: id, title: decrypt(reminder.title_cipher) }) }] };
  });

  if (hasScope('reminders:write')) server.registerTool('complete_reminder', {
    description: 'Mark one due or upcoming reminder complete for the selected student.',
    inputSchema: { reminder_id: reminderId.describe('ID from list_reminders.') },
  }, async args => {
    requireScope('reminders:write');
    const id = reminderId.parse(args.reminder_id);
    const reminder = load(id);
    if (!reminder || reminder.completed_at || reminder.canceled_at) throw new Error('Active reminder not found for the selected student');
    db.prepare('UPDATE reminders SET completed_at=? WHERE reminder_id=? AND account_id=? AND student_hash=? AND completed_at IS NULL AND canceled_at IS NULL')
      .run(timestamp(), id, accountId, studentHash);
    return { content: [{ type: 'text', text: JSON.stringify({ completed: true, reminder_id: id, title: decrypt(reminder.title_cipher) }) }] };
  });
}
