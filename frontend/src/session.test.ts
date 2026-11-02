import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api, readProfile, readToken, validateCredentials, type Profile, type Token } from './api';
import { Session } from './session';
const profile: Profile = { id: 'synthetic', name: 'Pessoa Teste', email: 'user@example.test', roles: ['OPERATOR'], permissions: ['cart:read'] };
const token = (): Token => ({ accessToken: 'a'.repeat(43), tokenType: 'Bearer', expiresAt: new Date(Date.now() + 60_000).toISOString() });
function client() { return { login: vi.fn().mockResolvedValue(token()), me: vi.fn().mockResolvedValue(profile), logout: vi.fn().mockResolvedValue(undefined) }; }
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.useRealTimers(); });
describe('session lifecycle', () => {
  it('publishes authenticated state only after profile and removes state after server logout', async () => {
    const c = client(); const s = new Session(c); const listener = vi.fn(); const off = s.subscribe(listener);
    await s.login(' user@example.test ', 'synthetic'); expect(c.login.mock.calls[0][0]).toBe('user@example.test');
    expect(s.snapshot().profile).toEqual(profile); expect(s.snapshot()).not.toHaveProperty('accessToken');
    await s.logout(); expect(c.logout).toHaveBeenCalledOnce(); expect(s.snapshot().profile).toBeNull(); expect(listener).toHaveBeenCalled(); off();
  });
  it('retains session when logout cannot be confirmed and permits retry', async () => {
    const c = client(); const s = new Session(c); await s.login('user@example.test', 'synthetic');
    c.logout.mockRejectedValueOnce(new ApiError(0, 'network')); await s.logout();
    expect(s.snapshot().profile).toEqual(profile); expect(s.snapshot().busy).toBe(false);
    expect(s.snapshot().message).toContain('confirmar'); await s.logout(); expect(s.snapshot().profile).toBeNull();
  });
  it('treats logout401 as already ended', async () => {
    const c = client(); const s = new Session(c); await s.login('user@example.test', 'synthetic'); c.logout.mockRejectedValue(new ApiError(401, 'expired'));
    await s.logout(); expect(s.snapshot().profile).toBeNull();
  });
  it('expires and invalidates revoked sessions on profile refresh', async () => {
    vi.useFakeTimers(); const c = client(); const s = new Session(c); await s.login('user@example.test', 'synthetic');
    vi.advanceTimersByTime(60_001); s.expire(); expect(s.snapshot().profile).toBeNull();
    c.login.mockResolvedValue(token()); await s.login('user@example.test', 'synthetic');
    c.me.mockRejectedValue(new ApiError(401, 'revoked')); await s.refresh(); expect(s.snapshot().profile).toBeNull();
  });
  it('does not restore an ended session after a delayed login response', async () => {
    const c = client(); let resolve!: (p: Profile) => void; c.me.mockImplementation(() => new Promise<Profile>(r => { resolve = r; }));
    const s = new Session(c); const login = s.login('user@example.test', 'synthetic'); await Promise.resolve(); s.clear(); resolve(profile); await login;
    expect(s.snapshot().profile).toBeNull(); expect(c.logout).toHaveBeenCalledOnce();
  });
  it('does not restore profile from a refresh that races with logout', async () => {
    const c = client(); const s = new Session(c); await s.login('user@example.test', 'synthetic');
    let resolve!: (p: Profile) => void; c.me.mockImplementation(() => new Promise<Profile>(r => { resolve = r; }));
    const refresh = s.refresh(); await s.logout(); resolve(profile); await refresh; expect(s.snapshot().profile).toBeNull();
  });
  it('cleans up issued token when loading profile fails', async () => {
    const c = client(); c.me.mockRejectedValue(new ApiError(503, 'Serviço indisponível')); const s = new Session(c);
    await s.login('user@example.test', 'synthetic'); expect(s.snapshot().profile).toBeNull(); expect(s.snapshot().busy).toBe(false); expect(c.logout).toHaveBeenCalledOnce();
  });
});
describe('contract and safe client', () => {
  it('validates required fields, email and BCrypt UTF8 bytes without truncation', () => {
    expect(validateCredentials('', '')).toBeTruthy(); expect(validateCredentials('bad', 'pass')).toBeTruthy();
    expect(validateCredentials('user@example.test', 'á'.repeat(36))).toBeUndefined(); expect(validateCredentials('user@example.test', 'á'.repeat(37))).toContain('72');
  });
  it('rejects malformed or expired tokens and malformed profiles', () => {
    expect(() => readToken({ ...token(), tokenType: 'JWT' })).toThrow(ApiError);
    expect(() => readToken({ ...token(), expiresAt: '2000-01-01T00:00:00Z' })).toThrow(ApiError);
    expect(() => readProfile({ ...profile, permissions: [42] })).toThrow(ApiError);
  });
  it('omits cookies, uses Bearer and disallows redirects', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify(profile))); vi.stubGlobal('fetch', fetch);
    await api.me(token().accessToken, new AbortController().signal);
    const opts = fetch.mock.calls[0][1]; expect(opts.credentials).toBe('omit'); expect(opts.redirect).toBe('error'); expect(opts.cache).toBe('no-store'); expect(opts.headers.Authorization).toMatch(/^Bearer /);
  });
  it('never displays a raw internal error body', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('internal stackTrace private', { status: 503 })));
    await expect(api.login('user@example.test', 'synthetic', new AbortController().signal)).rejects.toThrow('serviço está indisponível');
  });
  it('requires204 for logout, handles malformed JSON and network failure', async () => {
    const fetch = vi.fn().mockResolvedValueOnce(new Response('{}', { status: 200 })).mockResolvedValueOnce(new Response('invalid')).mockRejectedValueOnce(new TypeError('network private'));
    vi.stubGlobal('fetch', fetch); const signal = new AbortController().signal;
    await expect(api.logout(token().accessToken, signal)).rejects.toThrow('confirmar');
    await expect(api.me(token().accessToken, signal)).rejects.toThrow('Resposta inválida');
    await expect(api.me(token().accessToken, signal)).rejects.toThrow('conectar');
  });
});
