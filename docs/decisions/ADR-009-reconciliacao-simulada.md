# ADR-009 — Recusa e reconciliação do simulador

Data: 02/11/2026 (Manaus). **Aprovada explicitamente pelo proprietário nesta conversa**, resposta “Confirmar esse refinamento” à proposta da [PDV-040](../../sdd/features/PDV-040-recusa-reconciliacao.md). Complementa a [ADR-008](ADR-008-estados-venda-pagamento.md) somente neste recorte; não aceita entregas de outras tarefas.

Recusa comprovada libera apenas o reservado uma vez, mantendo físico. TIMEOUT continua INDETERMINADO e conserva a reserva protegida, inclusive após seu prazo nominal. Silêncio, repetição ou ausência de decisão não autorizam liberação.

Somente ADMIN pode registrar um desfecho **fictício local** APROVADO/RECUSADO para uma tentativa TIMEOUT já INDETERMINADO, com motivo e histórico imutável de ator/instante/identidade. Pode registrar para pagamento de outro proprietário, sem ampliar leitura de vendas/comprovantes alheios. O resultado original desconhecido permanece imutável; a resolução é prova adicional separada. Não há serviço financeiro externo.

ADMIN/OPERATOR reconciliam somente suas vendas. Sem decisão durável, permanece pendente; com decisão, aprovação conclui via PDV-039, e recusa libera uma vez. Prova, transições e efeitos locais são reentrantes e auditáveis. Um timeout antigo não substitui o resultado reconciliado. Transporte Outbox/Inbox usa identidades de etapa preservadas.

Não há nova tentativa automática, troca de método, cancelamento, estorno ou devolução neste lote. Essas políticas continuam nas tarefas próprias, inclusive PDV-041. Decisões de implementação dos endpoints, respostas 202 e idempotência estão no [contrato](../workflows/payment-reconciliation.md).
