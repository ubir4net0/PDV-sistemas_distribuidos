import { useEffect, useState } from 'react';
import { session } from './session';
import { message } from './Customers';
import { integer, record, text } from './transport';

type Entry={id:string;version:string;kind:string;saleStatus:string;paymentStatus:string;occurredAt:string};
type Delivery={eventId:string;type:string;status:string;attempts:string;error:string;updatedAt:string};
type Trail={traceId:string;events:Entry[];deliveries:Delivery[]};
function read(v:unknown):Trail{const r=record(v);if(!Array.isArray(r.events)||!Array.isArray(r.deliveries))throw Error('Invalid timeline');return {traceId:text(r.traceId),events:r.events.map(v=>{const e=record(v);return {id:text(e.id),version:integer(e.version),kind:text(e.kind),saleStatus:e.saleStatus===null?'Não registrado':text(e.saleStatus),paymentStatus:e.paymentStatus===null?'Não registrado':text(e.paymentStatus),occurredAt:text(e.occurredAt)};}),deliveries:r.deliveries.map(v=>{const d=record(v);return {eventId:text(d.eventId),type:text(d.type),status:text(d.status),attempts:integer(d.attempts),error:d.error===null?'':text(d.error),updatedAt:text(d.updatedAt)};})};}
const when=(s:string)=>new Date(s).toLocaleString('pt-BR',{timeZoneName:'short'});
export function Timeline({id}:{id:string}){
  const [value,setValue]=useState<{id:string;trail:Trail|null;error:string;loading:boolean}>({id,trail:null,error:'',loading:false});const [load,setLoad]=useState(0);
  useEffect(()=>{setLoad(0);setValue({id,trail:null,error:'',loading:false});},[id]);
  useEffect(()=>{if(load===0)return;const c=new AbortController();setValue({id,trail:null,error:'',loading:true});void session.request(`/sales/${id}/timeline`,'GET',undefined,c.signal).then(v=>{const trail=read(v);if(!c.signal.aborted)setValue({id,trail,error:'',loading:false});}).catch(e=>{if(!c.signal.aborted)setValue({id,trail:null,error:message(e),loading:false});});return()=>c.abort();},[id,load]);
  const trail=value.id===id?value.trail:null;
  return <section className="sale-timeline" aria-labelledby="timeline-title"><div className="module-title"><div><span className="section-number">HISTÓRICO CONFIRMADO</span><h2 id="timeline-title">Rastreamento da venda</h2><p>Eventos registrados no servidor. Reconsulte para acompanhar novas etapas.</p></div><button className="light-button" disabled={value.loading} onClick={()=>setLoad(n=>n+1)}>{value.loading?'Consultando histórico…':trail?'Atualizar histórico':'Consultar histórico'}</button></div>
    {value.id===id&&value.error&&<p role="alert">{value.error}</p>}{trail&&<><p className="helper">Correlação do pagamento: <code>{trail.traceId}</code></p><ol className="timeline-list">{trail.events.map(e=><li key={e.id}><span className="timeline-dot" aria-hidden="true"/><div><b>{e.kind.replaceAll('_',' ')}</b><time dateTime={e.occurredAt}>{when(e.occurredAt)}</time><p>Venda: {e.saleStatus} · Pagamento: {e.paymentStatus}</p><small>Revisão {e.version}</small></div></li>)}</ol><h3>Entrega das mensagens</h3><p className="helper">Falha de entrega não significa recusa do pagamento.</p>{trail.deliveries.length===0?<p>Nenhuma entrega registrada.</p>:<ul className="delivery-list">{trail.deliveries.map(d=><li key={d.eventId}><b>{d.type}</b><span>{d.status} · {d.attempts} tentativa(s)</span>{d.error&&<strong>Falha: {d.error}</strong>}<time dateTime={d.updatedAt}>{when(d.updatedAt)}</time></li>)}</ul>}</>}
  </section>;
}
