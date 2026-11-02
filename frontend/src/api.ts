export interface Profile { id: string; name: string; email: string; roles: string[]; permissions: string[] }
export interface Token { accessToken: string; tokenType: 'Bearer'; expiresAt: string }
export class ApiError extends Error {
  constructor(public status: number, message: string, public code?: string) { super(message); }
}
const object = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null;
const strings = (v: unknown): v is string[] => Array.isArray(v) && v.every(x => typeof x === 'string');
export function readToken(v: unknown): Token {
  if (!object(v) || typeof v.accessToken !== 'string' || !/^[A-Za-z0-9_-]{32,512}$/.test(v.accessToken)
      || v.tokenType !== 'Bearer' || typeof v.expiresAt !== 'string'
      || !Number.isFinite(Date.parse(v.expiresAt)) || Date.parse(v.expiresAt) <= Date.now()) {
    throw new ApiError(502, 'A resposta de acesso é inválida. Tente novamente.');
  }
  return { accessToken: v.accessToken, tokenType: 'Bearer', expiresAt: v.expiresAt };
}
export function readProfile(v: unknown): Profile {
  if (!object(v) || typeof v.id !== 'string' || typeof v.name !== 'string' || typeof v.email !== 'string'
      || !strings(v.roles) || !strings(v.permissions)) throw new ApiError(502, 'Não foi possível ler seu perfil.');
  return { id: v.id, name: v.name, email: v.email, roles: v.roles, permissions: v.permissions };
}
async function request(path: string, method: string, signal: AbortSignal, token?: string, body?: object) {
  let response: Response;
  try {
    response = await fetch(path, {
      method, signal: AbortSignal.any([signal, AbortSignal.timeout(10_000)]),
      credentials: 'omit', cache: 'no-store', redirect: 'error',
      headers: { Accept: 'application/json', ...(body ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      ...(body ? { body: JSON.stringify(body) } : {}),
    });
  } catch {
    if (signal.aborted) throw new DOMException('Aborted', 'AbortError');
    throw new ApiError(0, 'Não foi possível conectar. Verifique sua conexão e tente novamente.');
  }
  if (!response.ok) {
    const message = response.status === 401 ? 'E-mail ou senha inválidos, ou acesso indisponível.'
      : response.status === 403 ? 'Seu acesso não permite esta ação.'
      : response.status === 400 ? 'Confira os dados e tente novamente.'
      : 'O serviço está indisponível. Tente novamente em instantes.';
    throw new ApiError(response.status, message);
  }
  if (method === 'POST' && path === '/auth/logout') {
    if (response.status !== 204) throw new ApiError(502, 'Não foi possível confirmar a saída. Tente novamente.');
    return undefined;
  }
  try { return await response.json() as unknown; }
  catch { throw new ApiError(502, 'Resposta inválida do serviço. Tente novamente.'); }
}
export const api = {
  login: async (email: string, password: string, signal: AbortSignal) =>
    readToken(await request('/auth/login', 'POST', signal, undefined, { email, password })),
  me: async (token: string, signal: AbortSignal) => readProfile(await request('/auth/me', 'GET', signal, token)),
  logout: async (token: string, signal: AbortSignal) => { await request('/auth/logout', 'POST', signal, token); },
};
export function validateCredentials(email: string, password: string): string | undefined {
  if (!email.trim() || !password.trim()) return 'Informe seu e-mail e sua senha.';
  if (email.trim().length > 320 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) return 'Informe um e-mail válido.';
  if (new TextEncoder().encode(password).length > 72) return 'A senha deve ter no máximo 72 bytes.';
  return undefined;
}
