import { acceptedContent, inputRequired, inputResponse, type McpServer } from '@modelcontextprotocol/server';
import * as z from 'zod/v4';
import { actionDigest, actionRequestState, consumeActionConfirmation, discardActionConfirmation } from './action-confirmation.js';

type Params = Record<string, string>;
type GetData = (route: string, params?: Params) => Promise<unknown>;
type PostData = (route: string, fields: Params) => Promise<unknown>;
type RequireScope = (scope: string) => void;
type Question = Record<string, any>;

const quizId = z.string().trim().min(1).max(128);
const answersSchema = z.array(z.object({
  question_index: z.number().int().min(0).max(99),
  answer: z.string().trim().min(1).max(1_000),
  elapsed_seconds: z.number().int().min(0).max(3_600),
})).min(1).max(100);
const confirmationSchema = z.object({
  confirmation: z.enum(['SUBMIT']).describe('Type SUBMIT after reviewing every answer and elapsed time.'),
});

function object(value: unknown): Record<string, any> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, any> : {};
}

function parseQuiz(value: unknown): { id: string; title: string; questions: Question[] } {
  const body = object(value);
  const data = object(body.data);
  const id = quizId.parse(data.quiz_id);
  if (data.can_play !== true) throw new Error('This quiz is unavailable to play (can_play is false or missing)');
  if (typeof data.label !== 'string' || !data.label.trim() || data.label.length > 500) {
    throw new Error('The school did not provide a usable quiz title');
  }
  if (!Array.isArray(data.questions) || data.questions.length < 1 || data.questions.length > 100) {
    throw new Error('The school did not provide a supported question list');
  }
  const questions = data.questions.map((question: unknown, index: number) => {
    const item = object(question);
    if (typeof item.question !== 'string' || !item.question.trim() || item.question.length > 5_000) {
      throw new Error(`Quiz question ${index + 1} is missing readable text`);
    }
    if (!Array.isArray(item.reponses) || item.reponses.length < 1 || item.reponses.length > 100 ||
        item.reponses.some((choice: unknown) => typeof object(choice).reponse !== 'string' || !object(choice).reponse.trim() || object(choice).reponse.length > 1_000)) {
      throw new Error(`Quiz question ${index + 1} has an unsupported answer list`);
    }
    if (!Number.isInteger(item.temps_reponse) || item.temps_reponse < 0 || item.temps_reponse > 3_600) {
      throw new Error(`Quiz question ${index + 1} has an unsupported time limit`);
    }
    if (!item.answer || typeof item.answer !== 'object' || Array.isArray(item.answer)) {
      throw new Error(`Quiz question ${index + 1} has no answer state`);
    }
    return item as Question;
  });
  return { id, title: data.label.trim(), questions };
}

/** Register a verified quiz attempt tool. Every submission requires fresh client elicitation. */
export function registerQuizWriteTools(server: McpServer, getData: GetData, postData: PostData, requireScope: RequireScope): void {
  const inputSchema = {
    quiz_id: quizId.describe('Quiz id returned by list_quizzes.'),
    answers: answersSchema.describe('Provide one explicit answer and elapsed time for every question, in question_index order. Answer text must exactly match one of that question’s choices.'),
  };

  server.registerTool('submit_quiz_attempt', {
    description: 'Record one persistent school quiz attempt. Requires explicit answers for all questions and fresh human MCP elicitation showing the exact quiz, questions, choices, and elapsed seconds. Does not calculate or return a score. Clients without elicitation support cannot submit.',
    inputSchema,
  }, async (args, ctx) => {
    requireScope('documents:read');
    requireScope('quizzes:write');
    const id = quizId.parse(args.quiz_id);
    const quiz = parseQuiz(await getData('quiz', { quiz_id: id }));
    if (quiz.id !== id) throw new Error('The school returned a different quiz than requested');

    const supplied = answersSchema.parse(args.answers);
    if (supplied.length !== quiz.questions.length) throw new Error('Provide exactly one answer for every quiz question');
    const byIndex = new Map(supplied.map(item => [item.question_index, item]));
    if (byIndex.size !== quiz.questions.length || quiz.questions.some((_question, index) => !byIndex.has(index))) {
      throw new Error('Question indices must cover every question exactly once, starting at zero');
    }

    const selected = quiz.questions.map((question, index) => {
      const entry = byIndex.get(index)!;
      if (entry.elapsed_seconds > question.temps_reponse) {
        throw new Error(`Elapsed time for question ${index + 1} exceeds its ${question.temps_reponse}-second limit`);
      }
      const choice = question.reponses.find((item: Record<string, any>) => item.reponse === entry.answer);
      if (!choice) throw new Error(`Answer for question ${index + 1} must exactly match one of the school-provided choices`);
      return { question, answer: entry.answer, elapsed: entry.elapsed_seconds };
    });

    const digest = actionDigest('submit_quiz_attempt', { quiz, selected: selected.map(({ answer, elapsed }, question_index) => ({ question_index, answer, elapsed_seconds: elapsed })) });
    const response = inputResponse(ctx.mcpReq.inputResponses, 'quiz_confirmation');
    if (response.kind === 'elicit' && response.action !== 'accept') {
      discardActionConfirmation(ctx, 'submit_quiz_attempt', digest);
      return { content: [{ type: 'text', text: 'Confirmation declined or canceled. No quiz attempt was submitted.' }] };
    }
    const requestState = await actionRequestState(ctx, 'submit_quiz_attempt', digest);
    const confirmation = acceptedContent(ctx.mcpReq.inputResponses, 'quiz_confirmation', confirmationSchema);
    if (confirmation?.confirmation !== 'SUBMIT') {
      return inputRequired({ inputRequests: { quiz_confirmation: inputRequired.elicit({
        message: `Confirm recording this quiz attempt? This permanently records an attempt for the selected student.\nQuiz: ${quiz.title}\n${selected.map(({ question, answer, elapsed }, index) => `${index + 1}. ${question.question}\nChosen answer: ${answer}\nElapsed seconds: ${elapsed}`).join('\n\n')}\n\nType SUBMIT to authorize this attempt.`,
        requestedSchema: confirmationSchema,
      }) }, requestState });
    }

    const freshQuiz = parseQuiz(await getData('quiz', { quiz_id: id }));
    if (JSON.stringify(freshQuiz) !== JSON.stringify(quiz)) {
      throw new Error('The quiz changed during confirmation; review the current questions and confirm again');
    }
    consumeActionConfirmation(ctx, 'submit_quiz_attempt', digest);

    // Clone the current school payload and modify only the two verified answer fields.
    const submittedQuestions: Question[] = freshQuiz.questions.map((question, index) => {
      const answer = selected[index];
      return {
        ...question,
        answer: { ...question.answer, answer: answer.answer, answered: answer.elapsed },
      };
    });
    await postData('quiz', { quiz_id: quiz.id, questions: JSON.stringify(submittedQuestions) });
    return { content: [{ type: 'text', text: JSON.stringify({ submitted: true, quiz_id: quiz.id, attempt_recorded: true }) }] };
  });
}
