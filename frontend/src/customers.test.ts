import { afterEach, expect, it, vi } from 'vitest';
import { exactParse, exactStringify, integer, revision, businessRequest } from './transport';
import { readCustomer, validDocument, validateCustomer, blankCustomer } from './customers-model';
import { Session } from './session';
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); });
it('validates CPF/CNPJ masks and alphanumeric DV without external lookups', () => {
  expect(validDocument('PF', '529.982.247-25')).toBe(true); expect(validDocument('PF', '11111111111')).toBe(false);
  expect(validDocument('PJ', '12.345.678/0001-95')).toBe(true); expect(validDocument('PJ', '12345678000190')).toBe(false);
  expect(validDocument('PF', '52998224724')).toBe(false); expect(validDocument('PF', '５２９９８２２４７２５')).toBe(false);
  expect(validateCustomer(blankCustomer())).toContain('nome');
});
it('round trips exact int64 versions and rejects unsafe DTOs', () => {
  expect(integer((exactParse('{"version":9223372036854775806}') as {version:unknown}).version)).toBe('9223372036854775806');
  expect(exactStringify({ version: revision('9223372036854775806') })).toBe('{"version":9223372036854775806}');
  expect(() => integer(1)).toThrow(); expect(() => readCustomer({})).toThrow();
});
it('blocks token-bearing external paths and sanitizes unrecognized errors', async () => {
  const signal = new AbortController().signal; await expect(businessRequest('//example.invalid', 'GET', 'synthetic', signal)).rejects.toThrow('Caminho');
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{"code":"private stack trace"}', { status: 409 })));
  await expect(businessRequest('/customers', 'GET', 'synthetic', signal)).rejects.toThrow('Conflito');
});
it('business401 clears the in-memory session and stale responses do not restore access', async () => {
  const c = { login: vi.fn().mockResolvedValue({ accessToken: 'a'.repeat(43), tokenType: 'Bearer' as const, expiresAt: new Date(Date.now()+60_000).toISOString() }), me: vi.fn().mockResolvedValue({ id:'test', name:'Test', email:'test@example.test', roles:['OPERATOR'], permissions:['customer.read'] }), logout: vi.fn() };
  const s = new Session(c); await s.login('test@example.test', 'synthetic');
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', {status:401}))); await expect(s.request('/customers')).rejects.toThrow(); expect(s.snapshot().profile).toBeNull();
});
