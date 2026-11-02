import type { Sale } from './sales-model';
export function paymentState(s: Pick<Sale,'status'|'paymentStatus'>) {
  if(s.status==='CONCLUIDA'&&s.paymentStatus==='APROVADO')return {title:'Pagamento aprovado',detail:'Venda concluída com confirmação do servidor.',tone:'approved',poll:false};
  if(s.paymentStatus==='APROVADO')return {title:'Pagamento aprovado',detail:'A venda está sendo finalizada. Aguarde a confirmação.',tone:'approved',poll:true};
  if(s.paymentStatus==='RECUSADO')return {title:'Pagamento recusado',detail:'O simulador registrou a recusa. Consulte novamente para acompanhar a finalização.',tone:'declined',poll:false};
  if(s.paymentStatus==='INDETERMINADO')return {title:'Resultado pendente',detail:'O resultado ainda não foi confirmado. A reserva permanece protegida.',tone:'pending',poll:false};
  if(s.paymentStatus==='CRIADO')return {title:'Aguardando processamento',detail:'A operação foi criada. O simulador ainda vai processar o pagamento.',tone:'processing',poll:true};
  if(s.paymentStatus==='EM_PROCESSAMENTO')return {title:'Processando simulação',detail:'Aguarde o resultado confirmado pelo servidor.',tone:'processing',poll:true};
  return {title:'Estado indisponível',detail:'Consulte novamente antes de tomar outra ação.',tone:'pending',poll:false};
}
export const uuid = (v:string)=>/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(v);
