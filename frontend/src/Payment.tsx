import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { session } from './session';
import { ApiError } from './api';
import { can, message } from './Customers';
import { record } from './transport';
import { brl, readSale, type Sale } from './sales-model';
import { paymentState, uuid } from './payment-model';
import { Timeline } from './Timeline';

export function PaymentLookup(){
  const [code,setCode]=useState('');const [error,setError]=useState('');const navigate=useNavigate();
  function submit(e:FormEvent){e.preventDefault();if(!uuid(code.trim())){setError('Informe o código completo da venda.');return;}navigate(`/app/sales/${code.trim()}`);}
  return <main className="module"><div className="module-title"><div><span className="section-number">VENDAS / ACOMPANHAMENTO</span><h1>Pagamentos simulados</h1><p>Consulte sua venda pelo código recebido no checkout.</p></div></div>{can('sale.read')?<form className="customer-choice" onSubmit={submit}><label htmlFor="sale-code">Código da venda</label><input id="sale-code" maxLength={36} value={code} onChange={e=>setCode(e.target.value)} placeholder="Cole o código da sua venda" required aria-describedby="lookup-error"/><p className="feedback" role="status" id="lookup-error">{error}</p><button className="action" type="submit">Consultar venda</button></form>:<p role="alert">Seu acesso não permite consultar vendas.</p>}</main>;
}
export function Payment({id,reconcileKeys}:{id:string;reconcileKeys:Record<string,string>}){
  const [data,setData]=useState<{id:string;sale:Sale|null;error:string;loading:boolean;updated:string}>({id,sale:null,error:'',loading:true,updated:''});
  const [retry,setRetry]=useState(0);const [notice,setNotice]=useState('');const [reconciling,setReconciling]=useState(false);
  const active=useRef<AbortController|null>(null);const mounted=useRef(true);const currentId=useRef(id);currentId.current=id;
  const sale=data.id===id&&!data.error?data.sale:null;const state=sale?paymentState(sale):null;
  useEffect(()=>{setNotice('');setReconciling(false);},[id]);
  useEffect(()=>{
    mounted.current=true;const controller=new AbortController();active.current=controller;let timer:ReturnType<typeof setTimeout>;let rounds=0;
    setData({id,sale:null,error:'',loading:true,updated:''});
    if(!uuid(id)||!can('sale.read')){setData({id,sale:null,error:'Venda não encontrada para este acesso.',loading:false,updated:''});return()=>{mounted.current=false;controller.abort();};}
    async function load(){
      try{const value=readSale(await session.request(`/sales/${id}`,'GET',undefined,controller.signal));if(controller.signal.aborted)return;
        if(value.id!==id)throw new ApiError(502,'Resposta de venda inválida.');
        setData({id,sale:value,error:'',loading:false,updated:new Date().toLocaleTimeString('pt-BR')});
        if(paymentState(value).poll){if(++rounds<30)timer=setTimeout(()=>void load(),2000);else setNotice('Consulta automática pausada. Use Reconsultar venda para atualizar.');}
      }catch(e){if(!controller.signal.aborted)setData({id,sale:null,error:e instanceof ApiError&&e.status===404?'Venda não encontrada para este acesso.':message(e),loading:false,updated:''});}
    }
    void load();return()=>{mounted.current=false;controller.abort();clearTimeout(timer);};
  },[id,retry]);
  async function reconcile(){
    if(reconciling||!sale||!can('sale.reconcile'))return;setReconciling(true);setNotice('');
    const requestId=reconcileKeys[id]??crypto.randomUUID();reconcileKeys[id]=requestId;
    try{const result=record(await session.request(`/sales/${id}/reconcile`,'POST',{requestId},active.current?.signal));
      if(typeof result.pending!=='boolean')throw new ApiError(502,'Resposta de reconciliação inválida.');
      delete reconcileKeys[id];if(mounted.current&&currentId.current===id){setRetry(n=>n+1);setNotice(result.pending?'Reconciliação pendente. Ainda não há desfecho confirmado.':'Reconciliação recebida. Consultando o estado atual.');}
    }catch(e){if(mounted.current&&currentId.current===id)setNotice(message(e));}
    finally{if(mounted.current&&currentId.current===id)setReconciling(false);}
  }
  return <main className="module payment-module"><div className="module-title"><div><span className="section-number">VENDAS / ACOMPANHAMENTO</span><h1>Pagamento simulado</h1><p>Demonstração local. Nenhuma cobrança real.</p></div><Link className="light-button" to="/app/pos">Voltar ao PDV</Link></div>
    {(data.id!==id||data.loading)&&<p className="empty" role="status">Consultando venda…</p>}{data.id===id&&data.error&&<p className="notice" role="alert">{data.error}</p>}
    {sale&&state&&<section className={`payment-card ${state.tone}`} aria-labelledby="payment-title"><div className="payment-state" role="status"><span className="payment-icon" aria-hidden="true">{state.tone==='approved'?'✓':state.tone==='declined'?'×':state.tone==='processing'?'◌':'…'}</span><h2 id="payment-title">{state.title}</h2><p>{state.detail}</p></div>
      <dl className="payment-details"><div><dt>Venda</dt><dd>#{sale.number}</dd></div><div><dt>Total da venda</dt><dd>{brl(sale.total)}</dd></div><div><dt>Método fictício</dt><dd>{sale.method.replaceAll('_',' ')}</dd></div><div><dt>Cenário escolhido</dt><dd>{sale.scenario}</dd></div><div><dt>Estado da venda</dt><dd>{sale.status}</dd></div><div><dt>Estado do pagamento</dt><dd>{sale.paymentStatus}</dd></div></dl><p className="helper">Código da venda: {sale.id}</p><p className="helper">Última consulta: {data.updated}</p>
    </section>}
    {notice&&<p className="notice" role="status">{notice}</p>}<div className="payment-actions"><button className="action" disabled={data.loading||reconciling} onClick={()=>setRetry(n=>n+1)}>Reconsultar venda</button>{sale&&can('sale.reconcile')&&sale.paymentStatus==='INDETERMINADO'&&<button className="light-button" disabled={reconciling} onClick={()=>void reconcile()}>{reconciling?'Reconciliando…':'Reconciliar resultado'}</button>}<Link className="text-button" to="/app/payments">Consultar outro código</Link></div>
    {sale&&<Timeline key={id} id={id}/>}
  </main>;
}
