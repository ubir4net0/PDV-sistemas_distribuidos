import { expect,it } from 'vitest';
import { paymentState,uuid } from './payment-model';
it('never interprets an unknown result as a refusal or releases reservation',()=>{
  const s=paymentState({status:'AGUARDANDO_PAGAMENTO',paymentStatus:'INDETERMINADO'});expect(s.title).toBe('Resultado pendente');expect(s.detail).toContain('protegida');expect(s.poll).toBe(false);
});
it('distinguishes approved payment from concluded sale',()=>{
  expect(paymentState({status:'PAGAMENTO_APROVADO',paymentStatus:'APROVADO'}).detail).toContain('finalizada');expect(paymentState({status:'PAGAMENTO_APROVADO',paymentStatus:'APROVADO'}).poll).toBe(true);
  expect(paymentState({status:'CONCLUIDA',paymentStatus:'APROVADO'}).poll).toBe(false);
});
it('shows processing and refusal from persisted states only',()=>{
  expect(paymentState({status:'AGUARDANDO_PAGAMENTO',paymentStatus:'CRIADO'}).poll).toBe(true);expect(paymentState({status:'AGUARDANDO_PAGAMENTO',paymentStatus:'EM_PROCESSAMENTO'}).title).toContain('Processando');
  expect(paymentState({status:'PAGAMENTO_RECUSADO',paymentStatus:'RECUSADO'}).title).toBe('Pagamento recusado');expect(paymentState({status:'UNKNOWN',paymentStatus:'UNKNOWN'}).title).toBe('Estado indisponível');
});
it('requires complete UUID lookup without accepting paths or URLs',()=>{expect(uuid('00000000-0000-0000-0000-000000000001')).toBe(true);expect(uuid('../sales')).toBe(false);expect(uuid('https://example.invalid')).toBe(false);});
