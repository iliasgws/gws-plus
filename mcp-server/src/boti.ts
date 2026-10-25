const baseUrl = process.env.BOTI_BASE_URL ?? 'https://boti.education/p/greenwood/botiapi/';
if (new URL(baseUrl).protocol !== 'https:') throw new Error('BOTI_BASE_URL must use HTTPS');
const botiHeaders = {
  accept: 'application/json',
  'user-agent': 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36',
};

export type BotiStudent = { id: string; name: string };
export type BotiLogin = { userId: string; parentId: string; keyToken: string; students: BotiStudent[] };
export type BotiAccount = { userId: string; parentId: string; studentId: string; keyToken: string };
export type BotiSessionUpdate = { keyToken: string; students: BotiStudent[] };

class BotiSessionExpiredError extends Error {}

function endpoint(name: string): URL {
  return new URL(name, baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`);
}

async function parseResponse(response: Response): Promise<Record<string, any>> {
  if (!response.ok) throw new Error(`Boti returned HTTP ${response.status}`);
  const body = await response.json() as Record<string, any>;
  if (body.disconnect === true) throw new BotiSessionExpiredError('The school session has expired. Sign in again.');
  if (body.error) throw new Error(String(body.msg ?? 'Boti rejected the request'));
  return body;
}

export async function login(login: string, password: string): Promise<BotiLogin> {
  const form = new FormData();
  form.set('login', login);
  form.set('password', password);
  form.set('privacy', 'true');
  form.set('remembreMe', 'false');
  const response = await fetch(endpoint('login'), { method: 'POST', body: form, headers: botiHeaders, signal: AbortSignal.timeout(15_000) });
  const body = await parseResponse(response);
  if (typeof body.keyToken !== 'string' || typeof body.userId !== 'string' || typeof body.parentId !== 'string') {
    throw new Error('Boti login response did not include a usable parent session');
  }
  const rawStudents = Array.isArray(body.eleves) ? body.eleves : [];
  const students: BotiStudent[] = rawStudents.flatMap((student: any) => {
    if (!student || (typeof student.id !== 'string' && typeof student.id !== 'number')) return [];
    const id = String(student.id);
    if (!id || id.length > 128) return [];
    const name = [student.prenom, student.nom].filter((part: unknown) => typeof part === 'string' && part.trim()).join(' ').trim()
      || [student.firstName, student.lastName].filter((part: unknown) => typeof part === 'string' && part.trim()).join(' ').trim()
      || (typeof student.nomcomplet === 'string' ? student.nomcomplet.trim() : '')
      || (typeof student.name === 'string' ? student.name.trim() : '');
    if (!name || name.length > 160) return [];
    return [{ id, name }];
  });
  if (!students.length || students.length !== rawStudents.length || new Set(students.map(student => student.id)).size !== students.length) {
    throw new Error('Boti login response did not include a linked student');
  }
  return { userId: body.userId, parentId: body.parentId, keyToken: body.keyToken, students };
}

export async function getWithRefresh(
  account: BotiAccount,
  route: string,
  params: Record<string, string> = {},
  persistRefresh?: (update: BotiSessionUpdate) => Promise<BotiSessionUpdate>,
) {
  const request = async () => {
    const url = endpoint(route);
    for (const [key, value] of Object.entries(params)) url.searchParams.set(key, value);
    // Account identity and selected child are grant-bound; route arguments cannot override them.
    url.searchParams.set('key', account.keyToken);
    url.searchParams.set('user_id', account.userId);
    url.searchParams.set('eleveId', account.studentId);
    url.searchParams.set('lang', 'fr');
    url.searchParams.set('versionCode', process.env.BOTI_APP_VERSION_CODE ?? '24140');
    url.searchParams.set('versionNumber', process.env.BOTI_APP_VERSION ?? '2.4.14');
    url.searchParams.set('paltform', process.env.BOTI_PLATFORM ?? 'android');
    const response = await fetch(url, { headers: botiHeaders, signal: AbortSignal.timeout(20_000) });
    return parseResponse(response);
  };
  try {
    return await request();
  } catch (error) {
    if (!(error instanceof BotiSessionExpiredError) || !persistRefresh) throw error;
    const update = await refreshSession(account);
    if (!update.students.some(student => student.id === account.studentId)) {
      throw new Error('The selected student is no longer linked to this school account. Reauthorize the MCP connection.');
    }
    const persisted = await persistRefresh(update);
    account.keyToken = persisted.keyToken;
    return request();
  }
}

async function refreshSession(account: BotiAccount): Promise<BotiSessionUpdate> {
  const url = endpoint('acces_check');
  url.searchParams.set('key', account.keyToken);
  url.searchParams.set('user_id', account.userId);
  url.searchParams.set('parent_id', account.parentId);
  url.searchParams.set('eleveId', account.studentId);
  url.searchParams.set('lang', 'fr');
  url.searchParams.set('versionCode', process.env.BOTI_APP_VERSION_CODE ?? '24140');
  url.searchParams.set('versionNumber', process.env.BOTI_APP_VERSION ?? '2.4.14');
  url.searchParams.set('paltform', process.env.BOTI_PLATFORM ?? 'android');
  const response = await fetch(url, { headers: botiHeaders, signal: AbortSignal.timeout(20_000) });
  const body = await parseResponse(response);
  if (typeof body.keyToken !== 'string' || body.userId !== account.userId || body.parentId !== account.parentId || !Array.isArray(body.eleves)) {
    throw new Error('The school could not refresh this session. Sign in again.');
  }
  const students: BotiStudent[] = body.eleves.flatMap((student: any) => {
    if (!student || (typeof student.id !== 'string' && typeof student.id !== 'number')) return [];
    const id = String(student.id);
    const name = [student.prenom, student.nom].filter((part: unknown) => typeof part === 'string' && part.trim()).join(' ').trim()
      || (typeof student.nomcomplet === 'string' ? student.nomcomplet.trim() : '');
    return id && id.length <= 128 && name && name.length <= 160 ? [{ id, name }] : [];
  });
  if (!students.length || students.length !== body.eleves.length || new Set(students.map(student => student.id)).size !== students.length) {
    throw new Error('The school returned an invalid student roster during session refresh');
  }
  return { keyToken: body.keyToken, students };
}

/** Send one verified plain-text message; all account identity comes from the OAuth grant. */
export async function sendPlainTextMessage(account: BotiAccount, fields: Record<string, string>) {
  const reply = typeof fields.ref === 'string';
  const allowed = reply
    ? new Set(['ref', 'sujet', 'message', 'theme', 'index'])
    : new Set(['sujet', 'message', 'theme']);
  if (Object.keys(fields).some(key => !allowed.has(key))) throw new Error('Unsupported message fields');
  for (const required of ['sujet', 'message', 'theme']) {
    if (typeof fields[required] !== 'string' || !fields[required].trim()) throw new Error(`Missing ${required}`);
  }
  if (reply && (!fields.ref || !/^\d+$/.test(fields.index ?? ''))) throw new Error('Reply thread metadata is invalid');
  if (!reply && fields.index !== undefined) throw new Error('New thread must not include a reply index');

  const form = new FormData();
  for (const [key, value] of Object.entries(fields)) form.set(key, value);
  form.set('eleve_id', account.studentId);
  form.set('parent_id', account.parentId);
  form.set('key', account.keyToken);
  if (!reply) form.set('user_id', account.userId);
  form.set('versionCode', process.env.BOTI_APP_VERSION_CODE ?? '24140');
  form.set('versionNumber', process.env.BOTI_APP_VERSION ?? '2.4.14');
  form.set('paltform', process.env.BOTI_PLATFORM ?? 'android');
  form.set('lang', 'fr');
  const response = await fetch(endpoint('nouveau-message'), {
    method: 'POST', body: form, headers: botiHeaders, signal: AbortSignal.timeout(60_000),
  });
  return parseResponse(response);
}

/** Mark one homework item officially done; identity fields come only from the OAuth grant. */
export async function markHomeworkDone(account: BotiAccount, homeworkId: string) {
  if (!/^\d{1,64}$/.test(homeworkId)) throw new Error('Homework id is invalid');
  const form = new FormData();
  form.set('devoir', JSON.stringify({ id: homeworkId, files: [] }));
  form.set('eleve_id', account.studentId);
  form.set('user_id', account.userId);
  form.set('parent_id', account.parentId);
  form.set('key', account.keyToken);
  form.set('versionCode', process.env.BOTI_APP_VERSION_CODE ?? '24140');
  form.set('versionNumber', process.env.BOTI_APP_VERSION ?? '2.4.14');
  form.set('paltform', process.env.BOTI_PLATFORM ?? 'android');
  form.set('lang', 'fr');
  const response = await fetch(endpoint('devoirs_date_v2'), {
    method: 'POST', body: form, headers: botiHeaders, signal: AbortSignal.timeout(60_000),
  });
  return parseResponse(response);
}

/** Submit only the verified quiz and shop multipart contracts. */
export async function postVerifiedForm(account: BotiAccount, route: 'quiz' | 'shop', fields: Record<string, string>) {
  const allowed = route === 'quiz'
    ? new Set(['quiz_id', 'questions'])
    : fields.delete_history === 'true'
      ? new Set(['commande', 'detailsId', 'delete_history'])
      : new Set(['product', 'commande', 'variants', 'size', 'quantity', 'comment', 'price']);
  if (Object.keys(fields).some(key => !allowed.has(key))) throw new Error('Unsupported school action fields');
  if (route === 'quiz') {
    if (!fields.quiz_id || !fields.questions) throw new Error('Missing quiz submission fields');
    const questions = JSON.parse(fields.questions);
    if (!Array.isArray(questions)) throw new Error('Quiz questions must be a JSON array');
  } else if (fields.delete_history === 'true') {
    if (!fields.commande || (fields.detailsId !== undefined && !fields.detailsId)) {
      throw new Error('Order deletion fields are invalid');
    }
  } else if (!fields.product || !/^\d+$/.test(fields.quantity ?? '') || fields.comment === undefined || fields.price === undefined) {
    throw new Error('Order fields are invalid');
  }

  const form = new FormData();
  for (const [key, value] of Object.entries(fields)) form.set(key, value);
  form.set('eleve_id', account.studentId);
  form.set('user_id', account.userId);
  form.set('parent_id', account.parentId);
  form.set('key', account.keyToken);
  form.set('versionCode', process.env.BOTI_APP_VERSION_CODE ?? '24140');
  form.set('versionNumber', process.env.BOTI_APP_VERSION ?? '2.4.14');
  form.set('paltform', process.env.BOTI_PLATFORM ?? 'android');
  form.set('lang', 'fr');
  const response = await fetch(endpoint(route), {
    method: 'POST', body: form, headers: botiHeaders, signal: AbortSignal.timeout(60_000),
  });
  return parseResponse(response);
}
