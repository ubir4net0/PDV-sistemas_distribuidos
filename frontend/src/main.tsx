import { StrictMode, useEffect, useState, useSyncExternalStore, type FormEvent } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { validateCredentials } from './api';
import { session } from './session';
import './style.css';

function Mark() { return <span className="brand"><svg aria-hidden="true" viewBox="0 0 32 32"><path d="M6 26V6h12a7 7 0 0 1 0 14h-6v6m0-12h6a1 1 0 0 0 0-2h-6" fill="currentColor" /></svg>pdv<span className="brand-dot">.</span></span>; }
function Login() {
  const state = useSyncExternalStore(session.subscribe, session.snapshot);
  const [email, setEmail] = useState(''); const [password, setPassword] = useState('');
  const [show, setShow] = useState(false); const [error, setError] = useState('');
  if (state.profile) return <Navigate to="/app" replace />;
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (state.busy) return;
    const invalid = validateCredentials(email, password); setError(invalid ?? '');
    if (invalid) return;
    void session.login(email, password); setPassword('');
  }
  return <main className="login-layout">
    <section className="story" aria-label="Apresentação">
      <Mark />
      <div className="story-body"><span className="eyebrow"><span className="live-dot" />SEU PONTO DE PARTIDA</span>
        <h1>O próximo passo<br />começa aqui<span>.</span></h1>
        <p>Um acesso para continuar o seu trabalho.<br />Simples, organizado e no seu ritmo.</p>
        <div className="visual" aria-hidden="true"><div className="visual-top"><span className="mini-mark">P</span><span>ESPAÇO DE TRABALHO</span><i /></div>
          <div className="visual-line" /><div className="visual-line short" /><div className="visual-grid"><div><span>01</span><b>Conectar</b></div><div><span>02</span><b>Continuar</b></div><div><span>03</span><b>Avançar</b></div></div>
          <div className="visual-bottom"><span className="check">✓</span> Tudo começa com você.</div>
        </div>
      </div>
      <footer className="story-foot"><span>PDV / SISTEMAS DISTRIBUÍDOS</span><span>PROJETO ACADÊMICO</span></footer>
    </section>
    <section className="access" aria-labelledby="login-title">
      <div className="mobile-brand"><Mark /></div>
      <div className="access-top">BEM-VINDO AO SEU ESPAÇO</div>
      <div className="form-wrap"><span className="section-number">ACESSO / 01</span>
        <h2 id="login-title">Bom ter você aqui.</h2><p className="form-intro">Entre com sua conta para continuar.</p>
        <form onSubmit={submit} noValidate aria-busy={state.busy}>
          <label htmlFor="email">E-mail</label><input id="email" type="email" name="email" autoComplete="username" autoCapitalize="none" spellCheck={false} value={email} onChange={e => { setEmail(e.target.value); setError(''); }} maxLength={320} placeholder="seu@email.com" required disabled={state.busy} aria-describedby="feedback" />
          <label htmlFor="password">Senha</label><div className="password-field"><input id="password" type={show ? 'text' : 'password'} name="password" autoComplete="current-password" value={password} onChange={e => { setPassword(e.target.value); setError(''); }} placeholder="Sua senha" required disabled={state.busy} aria-describedby="feedback" /><button type="button" className="show-password" aria-label={show ? 'Ocultar senha' : 'Mostrar senha'} aria-pressed={show} onClick={() => setShow(!show)} disabled={state.busy}>{show ? 'Ocultar' : 'Mostrar'}</button></div>
          <div id="feedback" className="feedback" role="status" aria-live="polite">{error || state.message}</div>
          <button type="submit" className="primary" disabled={state.busy}>{state.busy ? <><span className="spinner" />Entrando…</> : <>Entrar na minha conta <span aria-hidden="true">↗</span></>}</button>
        </form>
        <p className="access-note"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 10h12v11H6zM8 10V6a4 4 0 0 1 8 0v4" fill="none" stroke="currentColor" strokeWidth="1.5" /></svg>Use as credenciais fornecidas pelo administrador.</p>
      </div>
      <footer className="access-foot"><span>PDV · Ambiente de estudo</span><span>Feito para seguir em frente.</span></footer>
    </section>
  </main>;
}
function Workspace() {
  const state = useSyncExternalStore(session.subscribe, session.snapshot);
  if (!state.profile) return <Navigate to="/login" replace />;
  const profile = state.profile;
  return <div className="workspace"><header className="workspace-header"><Mark /><span className="workspace-label">SEU ESPAÇO</span><button className="secondary" onClick={() => void session.logout()} disabled={state.busy}>{state.busy ? 'Saindo…' : 'Sair da conta'}</button></header>
    <main className="workspace-main"><div className="workspace-heading"><span className="section-number">CONTA / VISÃO GERAL</span><h1>Olá, {profile.name}.</h1><p>Seu acesso está pronto. Este é o seu espaço de sessão.</p></div>
      {state.message && <p className="notice" role="alert">{state.message}</p>}
      <section className="profile-card" aria-labelledby="profile-title"><div className="profile-heading"><span className="avatar" aria-hidden="true">{profile.name.slice(0, 1).toUpperCase()}</span><div><span className="section-number">IDENTIDADE</span><h2 id="profile-title">Seu perfil</h2></div><span className="session-badge"><span className="live-dot" />Sessão ativa</span></div>
        <dl><div><dt>Nome</dt><dd>{profile.name}</dd></div><div><dt>E-mail</dt><dd>{profile.email}</dd></div><div><dt>Perfis de acesso</dt><dd>{profile.roles.map(r => r === 'ADMIN' ? 'Administrador' : r === 'OPERATOR' ? 'Operador' : r).join(', ') || 'Nenhum perfil atribuído'}</dd></div><div><dt>Sessão válida até</dt><dd>{new Date(state.expiresAt).toLocaleTimeString('pt-BR')}</dd></div></dl>
        <details><summary>Consultar minhas permissões ({profile.permissions.length})</summary><ul className="permission-list">{profile.permissions.map(p => <li key={p}>{p}</li>)}</ul></details>
      </section>
      <p className="workspace-note">Ao recarregar ou fechar esta página, entre novamente. Use “Sair da conta” para encerrar o acesso no servidor.</p>
    </main><footer className="workspace-footer">PDV / PROJETO ACADÊMICO</footer></div>;
}
function App() {
  const state = useSyncExternalStore(session.subscribe, session.snapshot);
  useEffect(() => {
    if (!state.profile) return;
    const timer = setInterval(session.expire, 1000);
    const focus = () => { void session.refresh(); };
    const visibility = () => { if (document.visibilityState === 'visible') focus(); };
    window.addEventListener('focus', focus); document.addEventListener('visibilitychange', visibility);
    return () => { clearInterval(timer); window.removeEventListener('focus', focus); document.removeEventListener('visibilitychange', visibility); };
  }, [state.profile, state.expiresAt]);
  return <Routes><Route path="/login" element={<Login />} /><Route path="/app/*" element={<Workspace />} /><Route path="*" element={<Navigate to="/app" replace />} /></Routes>;
}
createRoot(document.getElementById('root')!).render(<StrictMode><BrowserRouter><App /></BrowserRouter></StrictMode>);
