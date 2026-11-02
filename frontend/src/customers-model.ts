import { ApiError } from './api';
import { integer, record, text } from './transport';
export interface Customer { id: string; personType: string; document: string; name: string; phone: string; email: string; locality: string; active: boolean; version: string }
export interface CustomerInput { personType: string; document: string; name: string; phone: string; email: string; locality: string }
export const blankCustomer = (): CustomerInput => ({ personType: 'PF', document: '', name: '', phone: '', email: '', locality: '' });
export function readCustomer(v: unknown): Customer {
  const r = record(v); if (typeof r.active !== 'boolean' || !['PF', 'PJ'].includes(String(r.personType))) throw new ApiError(502, 'Cadastro inválido do serviço.');
  return { id: text(r.id), personType: text(r.personType), document: text(r.document), name: text(r.name), phone: r.phone == null ? '' : text(r.phone), email: r.email == null ? '' : text(r.email), locality: r.locality == null ? '' : text(r.locality), active: r.active, version: integer(r.version) };
}
export function readPage<T>(v: unknown, decode: (v: unknown) => T): { items: T[]; total: string } {
  const r = record(v); if (!Array.isArray(r.items)) throw new ApiError(502, 'Lista inválida do serviço.');
  return { items: r.items.map(decode), total: integer(r.total) };
}
export function validDocument(type: string, input: string): boolean {
  const raw = input.trim().toUpperCase(); const cpf = type === 'PF';
  if (!(cpf ? /^(?:\d{11}|\d{3}\.\d{3}\.\d{3}-\d{2})$/ : /^(?:[A-Z0-9]{12}\d{2}|[A-Z0-9]{2}\.[A-Z0-9]{3}\.[A-Z0-9]{3}\/[A-Z0-9]{4}-\d{2})$/).test(raw)) return false;
  const value = raw.replace(/[./-]/g, ''); if (new Set(value).size === 1) return false;
  for (let end = cpf ? 9 : 12; end < (cpf ? 11 : 14); end++) {
    let sum = 0; for (let i = 0; i < end; i++) sum += (value.charCodeAt(i) - 48) * (cpf ? end + 1 - i : 2 + (end - 1 - i) % 8);
    const rem = sum % 11; if (Number(value[end]) !== (rem < 2 ? 0 : 11 - rem)) return false;
  }
  return type === 'PF' || type === 'PJ';
}
export function validateCustomer(v: CustomerInput): string | undefined {
  if (!v.name.trim()) return 'Informe o nome do cliente.';
  if (!validDocument(v.personType, v.document)) return 'Informe um CPF ou CNPJ válido para o tipo selecionado.';
  if (v.email && !/^[^\s@]+@[^\s@]+$/.test(v.email)) return 'Informe um e-mail válido.';
  if (v.name.length > 200 || v.phone.length > 32 || v.email.length > 320 || v.locality.length > 160) return 'Confira os limites dos campos.';
  return undefined;
}
