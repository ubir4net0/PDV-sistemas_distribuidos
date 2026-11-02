import { useEffect, useRef, useState, type Dispatch, type SetStateAction, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from './api';
import { can, message } from './Customers';
import { session } from './session';
import { readCustomer, readPage, type Customer } from './customers-model';
import { revision } from './transport';
import { brl, emptySales, quantity, readCart, readProduct, readSale, type Cart, type Product, type SalesMemory } from './sales-model';

export function PointOfSale({ memory, setMemory }: { memory: SalesMemory; setMemory: Dispatch<SetStateAction<SalesMemory>> }) {
  const [search,setSearch]=useState(''); const [page,setPage]=useState(0); const [products,setProducts]=useState<Product[]>([]); const [total,setTotal]=useState('0');
  const [customerSearch,setCustomerSearch]=useState(''); const [customers,setCustomers]=useState<Customer[]>([]); const [selected,setSelected]=useState('');
  const [loading,setLoading]=useState(false); const [error,setError]=useState(''); const [retry,setRetry]=useState(0);
  const [method,setMethod]=useState(''); const [scenario,setScenario]=useState('');
  const [savedCarts,setSavedCarts]=useState<Cart[]|null>(null); const [cartPage,setCartPage]=useState(0); const [cartTotal,setCartTotal]=useState('0');
  const productInput=useRef<HTMLInputElement>(null); const customerInput=useRef<HTMLInputElement>(null); const checkoutForm=useRef<HTMLFormElement>(null);
  const cart=memory.cart; const frozen=memory.busy || !!memory.attempt || !!memory.sale || memory.locked || memory.needsReload;
  const allowed=can('cart.read') && can('cart.write') && can('catalog.read') && can('customer.read');
  useEffect(()=>{
    if(!allowed)return; const controller=new AbortController(); setLoading(true);
    const timer=setTimeout(()=>{void session.request(`/products?status=ATIVO&size=20&page=${page}&q=${encodeURIComponent(search)}`,'GET',undefined,controller.signal)
      .then(v=>{if(controller.signal.aborted)return;const result=readPage(v,readProduct);setProducts(result.items);setTotal(result.total);setMemory(m=>({...m,products:{...m.products,...Object.fromEntries(result.items.map(p=>[p.id,p]))}}));})
      .catch(e=>{if(!controller.signal.aborted){setProducts([]);setError(message(e));}}).finally(()=>{if(!controller.signal.aborted)setLoading(false);});},250);
    return()=>{clearTimeout(timer);controller.abort();};
  },[search,page,retry,allowed,setMemory]);
  useEffect(()=>{
    if(!allowed)return;const controller=new AbortController();const timer=setTimeout(()=>{void session.request(`/customers?active=true&size=20&name=${encodeURIComponent(customerSearch)}`,'GET',undefined,controller.signal)
      .then(v=>{if(!controller.signal.aborted)setCustomers(readPage(v,readCustomer).items);}).catch(e=>{if(!controller.signal.aborted){setCustomers([]);setError(message(e));}});},250);
    return()=>{clearTimeout(timer);controller.abort();};
  },[customerSearch,retry,allowed]);
  useEffect(()=>{const key=(e:KeyboardEvent)=>{if(e.key==='F2'){e.preventDefault();productInput.current?.focus();}if(e.key==='F4'){e.preventDefault();customerInput.current?.focus();}if(e.altKey&&e.key==='Enter'){e.preventDefault();checkoutForm.current?.requestSubmit();}};window.addEventListener('keydown',key);return()=>window.removeEventListener('keydown',key);},[]);
  function patch(value: Partial<SalesMemory>){setMemory(m=>({...m,...value}));}
  async function hydrate(value: Cart) {
    const names:Record<string,Product>={};
    for(let start=0;start<value.items.length;start+=8){await Promise.allSettled(value.items.slice(start,start+8).map(async l=>{try{names[l.productId]=readProduct(await session.request(`/products/${l.productId}`));}catch{/* IDs remain visible if catalog access fails */}}));}
    setMemory(m=>({...m,products:{...m.products,...names}}));
  }
  async function createCart(){
    if(memory.busy || cart || (!selected&&!memory.create))return;
    const intent=memory.create??{requestId:crypto.randomUUID(),customerId:selected};patch({create:intent,busy:true});setError('');
    try{const result=readCart(await session.request('/carts','POST',intent));patch({cart:result,create:null,busy:false});}
    catch(e){setError(message(e));patch({busy:false,...(e instanceof ApiError&&e.status>=400&&e.status<500?{create:null}:{})});}
  }
  async function mutate(productId:string,raw:string|null){
    if(!cart || frozen)return;if(raw!==null&&!quantity(raw)){setError('Informe uma quantidade inteira positiva, até o limite permitido.');return;}
    patch({busy:true});setError('');
    try{const v=raw===null?await session.request(`/carts/${cart.id}/items/${productId}?version=${cart.version}`,'DELETE'):await session.request(`/carts/${cart.id}/items/${productId}`,'PUT',{quantity:revision(raw),version:revision(cart.version)});patch({cart:readCart(v),busy:false});}
    catch(e){setError(message(e));patch({busy:false,needsReload:true,locked:e instanceof ApiError&&e.code==='cart_checked_out'});}
  }
  async function reloadCart(id=cart?.id){
    if(!id||memory.busy||memory.attempt)return;patch({busy:true});setError('');
    try{const result=readCart(await session.request(`/carts/${id}`));patch({cart:result,busy:false,needsReload:false});void hydrate(result);}
    catch(e){setError(message(e));patch({busy:false,needsReload:true});}
  }
  async function listCarts(index=0){
    if(memory.busy||memory.attempt)return;patch({busy:true});setError('');
    try{const result=readPage(await session.request(`/carts?page=${index}&size=20`),readCart);setSavedCarts(result.items);setCartTotal(result.total);setCartPage(index);}catch(e){setError(message(e));}finally{patch({busy:false});}
  }
  async function pay(e:FormEvent){
    e.preventDefault();if(!cart||memory.busy||memory.sale||memory.needsReload||memory.locked||!can('sale.checkout'))return;
    if(!memory.attempt&&(!method||!scenario||!cart.items.length)){setError('Adicione itens e selecione o método e o cenário fictícios.');return;}
    const intent=memory.attempt??{cartId:cart.id,key:crypto.randomUUID(),version:cart.version,method,scenario};patch({attempt:intent,busy:true});setError('');
    try{const result=readSale(await session.request(`/carts/${intent.cartId}/checkout`,'POST',{version:revision(intent.version),method:intent.method,scenario:intent.scenario},undefined,intent.key));patch({sale:result,attempt:null,busy:false});}
    catch(err){setError(message(err));patch({busy:false,...(err instanceof ApiError&&err.status>=400&&err.status<500?{attempt:null,needsReload:true,locked:err.code==='cart_checked_out'}:{})});}
  }
  if(!allowed)return <main className="module"><h1>Ponto de venda</h1><p role="alert">Seu acesso não permite operar o PDV.</p></main>;
  return <main className="module pos"><div className="module-title"><div><span className="section-number">VENDAS / OPERAÇÃO</span><h1>Ponto de venda</h1><p>Seu atendimento, do primeiro item ao próximo passo.</p></div><div className="button-row"><button className="light-button" disabled={memory.busy||!!memory.attempt&&!memory.sale} onClick={()=>void listCarts()}>Meus carrinhos</button><button className="action" disabled={memory.busy||!!memory.attempt&&!memory.sale} onClick={()=>{setMemory(m=>({...emptySales(),products:m.products}));setMethod('');setScenario('');setSelected('');setError('');setSavedCarts(null);}}>Novo atendimento</button></div></div>
    {error&&<p className="notice" role="alert">{error} <button className="text-button" onClick={()=>{setError('');setRetry(retry+1);}}>Repetir consulta</button></p>}
    {savedCarts&&<section className="saved-carts"><h2>Meus carrinhos</h2>{!savedCarts.length&&<p>Nenhum carrinho encontrado.</p>}{savedCarts.map(c=><button className="light-button" key={c.id} disabled={memory.busy||!!memory.attempt} onClick={()=>{setSavedCarts(null);patch({sale:null,attempt:null,locked:false,needsReload:false});void reloadCart(c.id);}}>Retomar {c.id.slice(0,8)} · {brl(c.total)}</button>)}<div className="pagination"><button disabled={cartPage===0||memory.busy} onClick={()=>void listCarts(cartPage-1)}>Anterior</button><button disabled={BigInt((cartPage+1)*20)>=BigInt(cartTotal)||memory.busy} onClick={()=>void listCarts(cartPage+1)}>Próxima</button><button onClick={()=>setSavedCarts(null)}>Fechar carrinhos</button></div></section>}
    {!cart?<section className="customer-choice"><h2>Quem vamos atender?</h2><label htmlFor="pos-customer-search">Buscar cliente ativo · F4</label><input id="pos-customer-search" ref={customerInput} value={customerSearch} onChange={e=>setCustomerSearch(e.target.value)} maxLength={200} disabled={memory.busy||!!memory.create} placeholder="Digite um nome"/><label htmlFor="pos-customer">Cliente</label><select id="pos-customer" value={memory.create?.customerId??selected} onChange={e=>setSelected(e.target.value)} disabled={memory.busy||!!memory.create}><option value="">Selecione um cliente</option>{customers.map(c=><option key={c.id} value={c.id}>{c.name}</option>)}</select><p className="helper">Até20 resultados por busca. Refine o nome ou cadastre em Clientes.</p><button className="action" disabled={memory.busy||(!selected&&!memory.create)} onClick={()=>void createCart()}>{memory.busy?'Abrindo…':memory.create?'Repetir abertura':'Iniciar atendimento'}</button></section>:<p className="cart-context">Carrinho {cart.id.slice(0,8)} · Cliente {customers.find(c=>c.id===cart.customerId)?.name??cart.customerId} <span>{memory.sale?'Atendimento convertido em venda.':'Itens ainda não reservam estoque.'}</span></p>}
    <div className="pos-grid"><section className="product-panel" aria-labelledby="products-title"><h2 id="products-title">Catálogo</h2><label htmlFor="product-search">Buscar produto por nome ou SKU · F2</label><input id="product-search" ref={productInput} value={search} onChange={e=>{setSearch(e.target.value);setPage(0);}} maxLength={200} placeholder="Comece pelo nome ou código"/>
      {loading?<p className="empty" role="status">Buscando produtos…</p>:!products.length?<p className="empty">Nenhum produto ativo encontrado.</p>:<div className="product-grid">{products.map(p=><article className="product-tile" key={p.id}><span className="section-number">{p.sku}</span><h3>{p.name}</h3><strong>{brl(p.price)}</strong><button className="light-button" aria-label={`Adicionar ${p.name}`} disabled={!cart||frozen} onClick={()=>void mutate(p.id,String(BigInt(cart?.items.find(l=>l.productId===p.id)?.quantity??'0')+1n))}>Adicionar ＋</button></article>)}</div>}
      <div className="pagination"><span>{total} produto(s)</span><button disabled={page===0||loading} onClick={()=>setPage(page-1)}>Anterior catálogo</button><button disabled={BigInt((page+1)*20)>=BigInt(total)||loading} onClick={()=>setPage(page+1)}>Próxima catálogo</button></div>
    </section><aside className="cart-panel" aria-labelledby="cart-title"><div className="cart-title"><h2 id="cart-title">Seu carrinho</h2><span>{cart?.items.length??0} itens</span></div>
      {!cart?.items.length?<p className="cart-empty">Selecione um cliente e adicione produtos para começar.</p>:<ul className="cart-lines">{cart.items.map(l=><li key={l.productId}><div><strong>{memory.products[l.productId]?.name??`Produto ${l.productId.slice(0,8)}`}</strong><span>{brl(l.unitPrice)} por unidade</span></div><form key={`${l.productId}-${cart.version}`} onSubmit={e=>{e.preventDefault();const data=new FormData(e.currentTarget);void mutate(l.productId,String(data.get('quantity')));}}><label className="sr-only" htmlFor={`quantity-${l.productId}`}>Quantidade de {memory.products[l.productId]?.name??l.productId}</label><input id={`quantity-${l.productId}`} name="quantity" defaultValue={l.quantity} inputMode="numeric" pattern="[0-9]+" disabled={frozen}/><button className="text-button" type="submit" disabled={frozen}>Atualizar</button><button className="text-button" type="button" aria-label={`Remover ${memory.products[l.productId]?.name??l.productId}`} disabled={frozen} onClick={()=>void mutate(l.productId,null)}>Remover</button></form><b>{brl(l.subtotal)}</b></li>)}</ul>}
      <div className="cart-total"><span>Total do atendimento</span><output aria-label="Total do atendimento" aria-live="polite">{brl(cart?.total??'0.00')}</output><small>Calculado pelo servidor · sem descontos</small></div>
      {memory.needsReload&&!memory.locked&&<button className="light-button" disabled={memory.busy||!!memory.attempt} onClick={()=>void reloadCart()}>Reconsultar carrinho</button>}{memory.locked&&<p className="notice">Este carrinho já foi convertido e não pode ser editado.</p>}
      {memory.sale?<section className="checkout-success" role="status"><h3>Venda criada · #{memory.sale.number}</h3><p>Aguardando confirmação do pagamento simulado.</p><p>Código: {memory.sale.id}</p><Link className="action" to={`/app/sales/${memory.sale.id}`}>Acompanhar pagamento</Link></section>:<form ref={checkoutForm} onSubmit={e=>void pay(e)} className="checkout-form"><p className="demo-label">DEMONSTRAÇÃO · SEM COBRANÇA REAL</p><fieldset disabled={!cart||frozen}><label htmlFor="payment-method">Método fictício</label><select id="payment-method" value={memory.attempt?.method??method} onChange={e=>setMethod(e.target.value)}><option value="">Selecione</option>{[['DINHEIRO','Dinheiro'],['PIX','Pix'],['CARTAO_CREDITO','Cartão de crédito'],['CARTAO_DEBITO','Cartão de débito'],['BOLETO','Boleto']].map(([v,l])=><option key={v} value={v}>{l} (fictício)</option>)}</select><label htmlFor="payment-scenario">Cenário da demonstração</label><select id="payment-scenario" value={memory.attempt?.scenario??scenario} onChange={e=>setScenario(e.target.value)}><option value="">Selecione</option><option value="APROVADO">Aprovação</option><option value="RECUSADO">Recusa</option><option value="TIMEOUT">Resultado desconhecido (timeout)</option></select></fieldset>
        {memory.attempt&&!memory.busy&&<p className="notice">Resultado não confirmado. Repita o checkout para recuperar a mesma operação.</p>}<button className="primary" disabled={!cart||memory.busy||memory.needsReload||memory.locked||!can('sale.checkout')} type="submit">{memory.busy?'Aguarde…':memory.attempt?'Repetir checkout com segurança':'Finalizar atendimento'}</button><p className="helper">Alt+Enter para finalizar. O preço e o estoque serão revalidados.</p></form>}
    </aside></div>{cart&&<div className="mobile-order-summary"><span>{brl(cart.total)}</span><a href="#cart-title">Ver carrinho e pagamento ↓</a></div>}
  </main>;
}
