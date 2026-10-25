import { acceptedContent, inputRequired, inputResponse, type McpServer } from '@modelcontextprotocol/server';
import * as z from 'zod/v4';
import { actionDigest, actionRequestState, consumeActionConfirmation, discardActionConfirmation } from './action-confirmation.js';

type Params = Record<string, string>;
type GetData = (route: string, params?: Params) => Promise<unknown>;
type PostData = (route: string, fields: Params) => Promise<unknown>;
type RequireScope = (scope: string) => void;
type HasScope = (scope: string) => boolean;
type Homework = {
  id: string;
  title: string;
  subject: string;
  dueDate: string | null;
  alreadyDone: boolean;
  canSetDone: boolean;
};

const homeworkId = z.string().trim().min(1).max(128);
const confirmationSchema = z.object({
  confirmation: z.enum(['DONE']).describe('Type DONE after reviewing the exact homework and its due date.'),
});

function object(value: unknown): Record<string, any> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, any> : {};
}

function text(value: unknown, label: string, max = 500): string {
  if (typeof value !== 'string' || !value.trim() || value.length > max) {
    throw new Error(`The school did not provide a usable ${label}`);
  }
  return value.trim();
}

/** Read the verified detail payload and reject anything the school does not allow marking done. */
function parseHomework(value: unknown, requestedId: string): Homework {
  const body = object(value);
  const id = String(body.id ?? '').trim();
  if (id !== requestedId) throw new Error('The school returned a different homework item than requested');
  const state = object(body.devoir_fait);
  const dueDate = typeof body.date_remise === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(body.date_remise)
    ? body.date_remise
    : null;
  return {
    id,
    title: text(body.title, 'homework title'),
    subject: typeof body.matiere === 'string' && body.matiere.trim() ? body.matiere.trim().slice(0, 200) : 'matière inconnue',
    dueDate,
    alreadyDone: state.fait === true,
    canSetDone: body.can_set_done === true,
  };
}

/** Register the verified official homework submission. Marking done is permanent and requires fresh elicitation. */
export function registerHomeworkWriteTools(server: McpServer, getData: GetData, postData: PostData, requireScope: RequireScope, hasScope: HasScope = () => true): void {
  if (!hasScope('homework:read') || !hasScope('homework:write')) return;
  server.registerTool('submit_homework', {
    description: 'Mark one homework item as officially done for the selected student. This is permanent and visible to the school. Requires fresh human MCP elicitation showing the exact homework, subject, and due date. Attachments and submitted copies are not supported. Clients without elicitation support cannot submit.',
    inputSchema: {
      homework_id: homeworkId.describe('Homework id returned by get_homework or get_homework_details.'),
    },
  }, async (args, ctx) => {
    requireScope('homework:read');
    requireScope('homework:write');
    const id = homeworkId.parse(args.homework_id);

    const homework = parseHomework(await getData('devoirs', { devoir: id }), id);
    if (homework.alreadyDone) throw new Error('This homework is already marked done for the selected student');
    if (!homework.canSetDone) throw new Error('The school does not currently allow marking this homework done');

    const digest = actionDigest('submit_homework', homework);
    const response = inputResponse(ctx.mcpReq.inputResponses, 'homework_confirmation');
    if (response.kind === 'elicit' && response.action !== 'accept') {
      discardActionConfirmation(ctx, 'submit_homework', digest);
      return { content: [{ type: 'text', text: 'Confirmation declined or canceled. No homework was marked done.' }] };
    }
    const requestState = await actionRequestState(ctx, 'submit_homework', digest);
    const confirmation = acceptedContent(ctx.mcpReq.inputResponses, 'homework_confirmation', confirmationSchema);
    if (confirmation?.confirmation !== 'DONE') {
      return inputRequired({ inputRequests: { homework_confirmation: inputRequired.elicit({
        message: `Confirm marking this homework as done for the selected student? This is permanent and visible to the school.\nHomework: ${homework.title}\nSubject: ${homework.subject}\nDue date: ${homework.dueDate ?? 'not provided'}\n\nNo attachment or submitted copy will be sent.\nType DONE to authorize this action.`,
        requestedSchema: confirmationSchema,
      }) }, requestState });
    }

    const fresh = parseHomework(await getData('devoirs', { devoir: id }), id);
    if (JSON.stringify(fresh) !== JSON.stringify(homework)) {
      throw new Error('The homework changed during confirmation; review it and confirm again');
    }
    consumeActionConfirmation(ctx, 'submit_homework', digest);

    const result = await postData('devoirs_date_v2', { devoir: JSON.stringify({ id: homework.id, files: [] }) });
    return {
      content: [{
        type: 'text',
        text: JSON.stringify({ submitted: true, homework_id: homework.id, title: homework.title, subject: homework.subject, due_date: homework.dueDate, attachments_sent: false, result }),
      }],
    };
  });
}
