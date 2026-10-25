const baseUrl = process.env.BOTI_BASE_URL ?? 'https://boti.education/p/greenwood/botiapi/';
if (new URL(baseUrl).protocol !== 'https:') throw new Error('BOTI_BASE_URL must use HTTPS');

export type BotiAccount = { userId: string; parentId: string; studentId: string; keyToken: string };

function endpoint(name: string): URL {
  return new URL(name, baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`);
}

async function parseResponse(response: Response): Promise<Record<string, any>> {
  if (!response.ok) throw new Error(`Boti returned HTTP ${response.status}`);
  const body = await response.json() as Record<string, any>;
  if (body.disconnect === true) throw new Error('The school session has expired. Sign in again.');
  if (body.error) throw new Error(String(body.msg ?? 'Boti rejected the request'));
  return body;
}

export async function login(login: string, password: string): Promise<BotiAccount> {
  const form = new FormData();
  form.set('login', login);
  form.set('password', password);
  form.set('privacy', 'true');
  form.set('remembreMe', 'false');
  const response = await fetch(endpoint('login'), { method: 'POST', body: form, signal: AbortSignal.timeout(15_000) });
  const body = await parseResponse(response);
  if (typeof body.keyToken !== 'string' || typeof body.userId !== 'string' || typeof body.parentId !== 'string') {
    throw new Error('Boti login response did not include a usable parent session');
  }
  const students = Array.isArray(body.eleves) ? body.eleves : [];
  const requestedStudent = String(body.eleveId ?? students[0]?.id ?? '');
  if (!requestedStudent || !students.some((student: any) => String(student.id) === requestedStudent)) {
    throw new Error('Boti login response did not include a linked student');
  }
  return { userId: body.userId, parentId: body.parentId, studentId: requestedStudent, keyToken: body.keyToken };
}

export async function get(account: BotiAccount, route: string, params: Record<string, string> = {}) {
  const url = endpoint(route);
  url.searchParams.set('key', account.keyToken);
  url.searchParams.set('user_id', account.userId);
  url.searchParams.set('eleveId', account.studentId);
  url.searchParams.set('lang', 'fr');
  url.searchParams.set('versionCode', process.env.BOTI_APP_VERSION_CODE ?? '85');
  url.searchParams.set('versionNumber', process.env.BOTI_APP_VERSION ?? '0.8.5');
  url.searchParams.set('paltform', process.env.BOTI_PLATFORM ?? 'android');
  for (const [key, value] of Object.entries(params)) url.searchParams.set(key, value);
  const response = await fetch(url, { headers: { accept: 'application/json' }, signal: AbortSignal.timeout(20_000) });
  return parseResponse(response);
}
