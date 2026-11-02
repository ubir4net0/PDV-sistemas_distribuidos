import { api, ApiError, type Profile, type Token } from './api';

export interface SessionState { profile: Profile | null; expiresAt: number; busy: boolean; message: string }
const initial = (): SessionState => ({ profile: null, expiresAt: 0, busy: false, message: '' });
// This instance is tab-local. No persistence, logging or token decoding.
export class Session {
  private token: Token | null = null;
  private state: SessionState = initial();
  private listeners = new Set<() => void>();
  private generation = 0;
  private pending: AbortController | null = null;
  constructor(private client = api) {}
  snapshot = () => this.state;
  subscribe = (listener: () => void) => { this.listeners.add(listener); return () => { this.listeners.delete(listener); }; };
  private set(value: Partial<SessionState>) { this.state = { ...this.state, ...value }; this.listeners.forEach(fn => fn()); }
  private start() { this.pending?.abort(); this.pending = new AbortController(); return { id: ++this.generation, signal: this.pending.signal }; }
  clear(message = '') { this.generation++; this.pending?.abort(); this.pending = null; this.token = null; this.state = { ...initial(), message }; this.listeners.forEach(fn => fn()); }
  async login(email: string, password: string) {
    const op = this.start(); this.set({ busy: true, message: '' });
    let token: Token | null = null;
    try {
      token = await this.client.login(email.trim(), password, op.signal);
      const profile = await this.client.me(token.accessToken, op.signal);
      if (op.id !== this.generation) { void this.client.logout(token.accessToken, new AbortController().signal).catch(() => {}); return; }
      if (Date.parse(token.expiresAt) <= Date.now()) { this.clear('Sua sessão expirou. Entre novamente.'); return; }
      this.token = token; this.set({ profile, expiresAt: Date.parse(token.expiresAt), busy: false });
    } catch (error) {
      if (op.id === this.generation) this.set({ busy: false, message: error instanceof ApiError ? error.message : 'Não foi possível entrar. Tente novamente.' });
      if (token) void this.client.logout(token.accessToken, new AbortController().signal).catch(() => {});
    }
  }
  async logout() {
    if (!this.token || this.state.busy) return;
    const token = this.token; const op = this.start(); this.set({ busy: true, message: '' });
    try {
      await this.client.logout(token.accessToken, op.signal);
      if (op.id === this.generation) this.clear('Você saiu da sua conta.');
    } catch (error) {
      if (op.id !== this.generation) return;
      if (error instanceof ApiError && error.status === 401) this.clear('Sua sessão foi encerrada.');
      else this.set({ busy: false, message: 'Não foi possível confirmar a saída. Tente novamente.' });
    }
  }
  expire = () => {
    if (this.token && this.state.expiresAt <= Date.now()) this.clear('Sua sessão expirou. Entre novamente.');
  };
  async refresh() {
    this.expire(); if (!this.token || this.state.busy) return;
    const token = this.token; const id = this.generation;
    try {
      const profile = await this.client.me(token.accessToken, new AbortController().signal);
      if (id === this.generation) this.set({ profile });
    } catch (error) {
      if (id === this.generation && error instanceof ApiError && error.status === 401) this.clear('Sua sessão foi encerrada. Entre novamente.');
    }
  }
}
export const session = new Session();
