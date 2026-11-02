import { useEffect, useRef, useState, useSyncExternalStore, type FormEvent } from 'react';
import { session } from './session';
import { ApiError } from './api';
import { blankCustomer, readCustomer, readPage, validateCustomer, type Customer, type CustomerInput } from './customers-model';
import { revision } from './transport';
export const can = (permission: string) => {
  const p = session.snapshot().profile;
  return !!p && p.roles.some(r => r === 'ADMIN' || r === 'OPERATOR') && p.permissions.includes(permission);
};
export const message = (e: unknown) => e instanceof ApiError ? e.message : 'Não foi possível concluir. Tente novamente.';
export function Customers() {
  useSyncExternalStore(session.subscribe, session.snapshot);
  const [filters, setFilters] = useState({ name: '', document: '', active: '' }); const [query, setQuery] = useState(filters);
  const [page, setPage] = useState(0); const [rows, setRows] = useState<Customer[]>([]); const [total, setTotal] = useState('0');
  const [loading, setLoading] = useState(true); const [error, setError] = useState(''); const [notice, setNotice] = useState(''); const [reload, setReload] = useState(0);
  const [editor, setEditor] = useState<{ customer: Customer | null } | null>(null);
  useEffect(() => {
    if (!can('customer.read')) { setLoading(false); return; }
    const controller = new AbortController(); setLoading(true); setError('');
    const params = new URLSearchParams({ page: String(page), size: '20', ...Object.fromEntries(Object.entries(query).filter(([, v]) => v)) });
    void session.request(`/customers?${params}`, 'GET', undefined, controller.signal).then(v => {
      if (controller.signal.aborted) return; const result = readPage(v, readCustomer); setRows(result.items); setTotal(result.total);
    }).catch(e => { if (!controller.signal.aborted) { setRows([]); setError(message(e)); } }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [query, page, reload]);
  if (!can('customer.read')) return <main className="module"><h1>Clientes</h1><p role="alert">Seu acesso não permite consultar clientes.</p></main>;
  return <main className="module"><div className="module-title"><div><span className="section-number">CADASTROS / 01</span><h1>Clientes</h1><p>Pessoas e empresas, sempre por perto.</p></div>{can('customer.create') && <button className="action" disabled={!!editor} onClick={() => { setNotice(''); setEditor({ customer: null }); }}>Novo cliente <span aria-hidden="true">＋</span></button>}</div>
    {notice && <p className="success" role="status">{notice}</p>}
    <form className="filters" onSubmit={e => { e.preventDefault(); setPage(0); setQuery({ ...filters }); }}>
      <div><label htmlFor="search-name">Nome</label><input id="search-name" value={filters.name} maxLength={200} placeholder="Buscar por nome" onChange={e => setFilters({ ...filters, name: e.target.value })} /></div>
      <div><label htmlFor="search-document">Documento</label><input id="search-document" value={filters.document} maxLength={18} placeholder="CPF ou CNPJ" onChange={e => setFilters({ ...filters, document: e.target.value })} /></div>
      <div><label htmlFor="search-status">Situação</label><select id="search-status" value={filters.active} onChange={e => setFilters({ ...filters, active: e.target.value })}><option value="">Todas</option><option value="true">Ativos</option><option value="false">Inativos</option></select></div>
      <button className="action" type="submit">Buscar</button>
    </form>
    {loading ? <p className="empty" role="status">Carregando clientes…</p> : error ? <div className="notice" role="alert">{error} <button className="text-button" onClick={() => setReload(reload + 1)}>Tentar novamente</button></div> : rows.length === 0 ? <div className="empty"><span aria-hidden="true">◎</span><h2>Nenhum cliente encontrado</h2><p>Cadastre um cliente ou ajuste os filtros da busca.</p></div> : <div className="table-wrap"><table><caption className="sr-only">Clientes encontrados</caption><thead><tr><th scope="col">Cliente</th><th scope="col">Documento</th><th scope="col">Contato</th><th scope="col">Situação</th><th scope="col">Ação</th></tr></thead><tbody>{rows.map(c => <tr key={c.id}><td><strong>{c.name}</strong><small>{c.personType === 'PF' ? 'Pessoa física' : 'Pessoa jurídica'}</small></td><td>{c.document}</td><td>{c.email || c.phone || 'Não informado'}</td><td><span className={`status-pill ${c.active ? '' : 'muted'}`}>{c.active ? 'Ativo' : 'Inativo'}</span></td><td><button className="text-button" aria-label={`Abrir ${c.name}`} disabled={!!editor} onClick={() => setEditor({ customer: c })}>Abrir ↗</button></td></tr>)}</tbody></table></div>}
    {!loading && !error && <div className="pagination"><span>{total} cliente(s) · Página {page + 1}</span><button onClick={() => setPage(page - 1)} disabled={page === 0}>Anterior</button><button onClick={() => setPage(page + 1)} disabled={BigInt((page + 1) * 20) >= BigInt(total)}>Próxima</button></div>}
    {editor && <CustomerEditor key={editor.customer?.id ?? 'new'} customer={editor.customer} close={() => setEditor(null)} saved={() => { setEditor(null); setNotice('Cadastro salvo com sucesso.'); setReload(reload + 1); }} />}
  </main>;
}
function CustomerEditor({ customer, close, saved }: { customer: Customer | null; close: () => void; saved: () => void }) {
  const [current, setCurrent] = useState(customer); const [value, setValue] = useState<CustomerInput>(customer ?? blankCustomer());
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false); const [conflict, setConflict] = useState(false);
  const heading = useRef<HTMLHeadingElement>(null); useEffect(() => { heading.current?.focus(); }, []);
  const editable = can(current ? 'customer.update' : 'customer.create');
  const statusAllowed = !!current && session.snapshot().profile?.roles.includes('ADMIN') && can('customer.status');
  async function perform(action: () => Promise<unknown>) {
    if (busy) return; setBusy(true); setError(''); setConflict(false);
    try { await action(); saved(); } catch (e) { setError(message(e)); setConflict(e instanceof ApiError && e.code === 'version_conflict'); } finally { setBusy(false); }
  }
  function submit(e: FormEvent) {
    e.preventDefault(); const invalid = validateCustomer(value); if (invalid) { setError(invalid); return; }
    const fields = { personType: value.personType, document: value.document.trim(), name: value.name.trim(), phone: value.phone || null, email: value.email || null, locality: value.locality || null };
    void perform(() => session.request(current ? `/customers/${current.id}` : '/customers', current ? 'PUT' : 'POST', current ? { customer: fields, version: revision(current.version) } : fields));
  }
  async function reloadCurrent() {
    if (!current) return; setBusy(true);
    try { const fresh = readCustomer(await session.request(`/customers/${current.id}`)); setCurrent(fresh); setValue(fresh); setConflict(false); setError(''); } catch (e) { setError(message(e)); } finally { setBusy(false); }
  }
  return <section className="editor" aria-labelledby="editor-title"><div className="editor-title"><div><span className="section-number">CADASTRO</span><h2 id="editor-title" tabIndex={-1} ref={heading}>{current ? 'Detalhes do cliente' : 'Novo cliente'}</h2></div><button className="text-button" onClick={close} disabled={busy}>Fechar</button></div>
    <form onSubmit={submit} noValidate aria-busy={busy}><fieldset disabled={busy || !editable}><div className="form-grid"><div><label htmlFor="person-type">Tipo de pessoa</label><select id="person-type" value={value.personType} onChange={e => setValue({ ...value, personType: e.target.value })}><option value="PF">Pessoa física</option><option value="PJ">Pessoa jurídica</option></select></div>
      {([['name', 'Nome', 200], ['document', 'CPF ou CNPJ', 18], ['phone', 'Telefone (opcional)', 32], ['email', 'E-mail (opcional)', 320], ['locality', 'Localidade (opcional)', 160]] as const).map(([key, label, max]) => <div key={key}><label htmlFor={`customer-${key}`}>{label}</label><input id={`customer-${key}`} value={value[key]} maxLength={max} required={key === 'name' || key === 'document'} autoComplete="off" type={key === 'email' ? 'email' : 'text'} onChange={e => setValue({ ...value, [key]: e.target.value })} aria-describedby="customer-error" /></div>)}</div></fieldset>
      {error && <p className="notice" id="customer-error" role="alert">{error}</p>}{conflict && <button type="button" className="text-button" onClick={() => void reloadCurrent()} disabled={busy}>Descartar edição e recarregar</button>}
      <div className="editor-actions">{editable && <button className="action" disabled={busy || conflict} type="submit">{busy ? 'Salvando…' : 'Salvar cadastro'}</button>}<button type="button" className="light-button" onClick={close} disabled={busy}>Cancelar</button>{statusAllowed && <button type="button" className="text-button" disabled={busy || conflict} onClick={() => void perform(() => session.request(`/customers/${current!.id}/status`, 'PATCH', { active: !current!.active, version: revision(current!.version) }))}>{current!.active ? 'Inativar cliente' : 'Ativar cliente'}</button>}</div>
    </form></section>;
}
