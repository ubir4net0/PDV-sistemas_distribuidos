# Conclusão e comprovante — PDV-039

[Spec](../../sdd/features/PDV-039-conclusao-comprovante.md), [estados aprovados](../decisions/ADR-008-estados-venda-pagamento.md) e [transporte](business-messaging.md).

Após aplicar APROVADO com prova durável, a mesma transação grava `pdv.sale.confirm.v1`, outcome APROVADO, chave `sale-confirm:<paymentId>`. O publicador usa a fila results/routing key payment.result. Não há endpoint público para impor uma conclusão. Execute worker com `-BusinessMessaging`, depois de Migrate; o modo antigo SimulatePayments apenas registra a decisão e deixa a intenção de confirmação para esse transporte.

O coordenador confirma em uma transação PostgreSQL: locks venda → pagamento → Saga → reserva → saldos por UUID; valida aprovação, itens/quantidades/total e reserva ACTIVE/protegida. O prazo nominal pode ter passado após o início válido protegido. Reserva inválida ou falta de prova impede concluir. Cliente/produto alterados depois do checkout não reescrevem o snapshot aceito.

Físico e reservado diminuem pela mesma quantidade uma única vez. Reserva CONFIRMED, venda CONCLUIDA, Saga com receipt_id/stock_confirmed_at, ledger imutável, eventos e comprovante confirmam juntos com a inbox. Falha em qualquer gravação reverte o conjunto; retry/DLQ seguem o contrato038. Reentrega e outro EventId para o mesmo pagamento recuperam a conclusão conhecida. Resultado antigo não regride a venda.

V17 acrescenta receipts, sale_confirmations e sale_stock_movements imutáveis; o histórico `/inventory/{productId}/movements` usa a view inventory_movement_history para unir movimentos manuais e tipo SALE. O operationId de SALE é o UUID real do pagamento; motivo `SALE:<saleId>`. As restrições manuais de V11 permanecem. A migration apenas cria intenções para aprovações antigas; não baixa estoque durante DDL.

`GET /sales/{id}/receipt`: Bearer, ADMIN/OPERATOR + sale.read, somente venda própria; 404 quando alheia/inexistente/ainda sem comprovante. Cache-Control: no-store. Retorna JSON imutável com id global, saleId, number, customerId, paymentId, método fictício, total, issuedAt e itens productId/quantity/unitPrice/subtotal. Valores decimais preservam precisão. Não inclui documentos pessoais ou dados de cartão. Comprovante interno digital; emissão fiscal/PDF não fazem parte desse contrato.

Recusa/reconciliação pertencem à PDV-040; cancelamento/estorno à PDV-041. Sem gateway ou operação financeira externa.
