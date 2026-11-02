import { parse, stringify, isLosslessNumber, LosslessNumber } from 'lossless-json';
import { ApiError } from './api';

export const exactParse = (text: string): unknown => parse(text);
export const exactStringify = (value: unknown) => stringify(value);
export function record(v: unknown): Record<string, unknown> {
  if (typeof v !== 'object' || !v || Array.isArray(v)) throw new ApiError(502, 'Resposta inválida do serviço.');
  return v as Record<string, unknown>;
}
export function text(v: unknown): string { if (typeof v !== 'string') throw new ApiError(502, 'Resposta inválida do serviço.'); return v; }
export function integer(v: unknown): string {
  if (!isLosslessNumber(v) || !/^\d+$/.test(v.value) || BigInt(v.value) > 9223372036854775807n) throw new ApiError(502, 'Revisão ou quantidade inválida do serviço.');
  return v.value;
}
export const revision = (v: string) => { if (!/^\d+$/.test(v)) throw new Error('Invalid revision'); return new LosslessNumber(v); };
const errors: Record<string, string> = {
  document_conflict: 'Este documento já está cadastrado.', version_conflict: 'Este registro mudou. Descarte a edição e recarregue antes de tentar novamente.',
  invalid_request: 'Confira os dados informados.', customer_not_found: 'Cliente não encontrado.',
};
export async function businessRequest(path: string, method: string, token: string, signal: AbortSignal, body?: object, key?: string): Promise<unknown> {
  if (!/^\/(customers|products|carts|sales)(\/|\?|$)/.test(path) || path.includes('://') || path.includes('..')) throw new ApiError(400, 'Caminho inválido.');
  let response: Response;
  try {
    response = await fetch(path, { method, signal: AbortSignal.any([signal, AbortSignal.timeout(10_000)]), credentials: 'omit', cache: 'no-store', redirect: 'error',
      headers: { Accept: 'application/json', Authorization: `Bearer ${token}`, ...(body ? { 'Content-Type': 'application/json' } : {}), ...(key ? { 'Idempotency-Key': key } : {}) },
      ...(body ? { body: exactStringify(body) } : {}),
    });
  } catch { if (signal.aborted) throw new DOMException('Aborted', 'AbortError'); throw new ApiError(0, 'Sem conexão. Tente novamente; confira a lista antes de repetir um cadastro.'); }
  if (!response.ok) {
    let code = ''; try { code = text(record(await response.json()).code); } catch { /* never display raw server errors */ }
    throw new ApiError(response.status, errors[code] ?? (response.status === 401 ? 'Sua sessão foi encerrada. Entre novamente.' : response.status === 403 ? 'Seu acesso não permite esta ação.' : response.status === 409 ? 'Conflito com o estado atual. Reconsulte antes de tentar novamente.' : 'O serviço está indisponível. Tente novamente.'), code);
  }
  try { return exactParse(await response.text()); } catch { throw new ApiError(502, 'Resposta inválida do serviço.'); }
}
