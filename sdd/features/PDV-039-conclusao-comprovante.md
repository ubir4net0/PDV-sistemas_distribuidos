# PDV-039 — Concluir venda, baixar estoque e emitir comprovante

## Estado e fonte

[Tarefa viva](https://app.notion.com/p/4092c9a9991781f084d4e86acf07e56b), leitura02/11/2026 (Manaus), Backlog, RF-V03/V06. Aceite: “Após aprovação confirmada, baixa única e comprovante global; nunca concluir com reserva inválida.” Sem responsável/prazo inferidos. [Baseline](../../docs/sources/PDV_ESPECIFICACAO_E_BACKLOG.md) e [ADR-008 aprovada](../../docs/decisions/ADR-008-estados-venda-pagamento.md).

## Definition of Ready

PDV-029/036/037 relidas ao vivo, Em validação, artefatos técnicos disponíveis; não aceitar predecessoras. PDV-038 entregue em d0ae931, CI Backend41465980255/Planning41465980096 SUCCESS, Em validação sem aceite inferido. Pre-flight039: main/origin d0ae931, PG/Rabbit healthy, Migrate V16 sem pendências. Estados/proteção e confirmação após aprovação já aprovados; não reabrir essas decisões.

## Incremento e invariantes

Resposta de pagamento aprovada exige prova durável, mesma venda/pagamento/reserva e grupo ACTIVE/protegido. Confirmação local bloqueia venda/pagamento/Saga/reserva e saldos ordenados. Retira quantidade do físico e reservado uma vez; disponível preservado. Deadline nominal ultrapassado não autoriza expirar reserva protegida após início válido; pagamento aprovado e prova durável são necessários.

Venda CONCLUIDA, reserva CONFIRMED, trilha de baixa e comprovante imutável com UUID global/snapshot confirmam ou revertem juntos. Repetição recupera mesmo comprovante/efeito; nunca fabricar sucesso com reserva EXPIRED/RELEASED, saldo inconsistente ou etapa incompleta. Sem dinheiro adicional, desconto, imposto ou regra de preço nova.

Comprovante digital interno JSON com UUID, venda, instante, cliente por ID, itens/preços/total/método fictício capturados. GET /sales/{id}/receipt exige sale.read e propriedade (ADMIN/OPERATOR), sem link público, documento pessoal ou dados de cartão. Formato PDF e histórico paginado não são presumidos deste requisito digital. Sem comprovante fiscal ou API externa.

## Coordenação técnica planejada

Aprovação gera intenção pdv.sale.confirm.v1 na mesma transação do resultado. Consumidor coordenador executa etapa de confirmação separada, com inbox/efeito/comprovante juntos. Backfill de vendas já aprovadas cria intenção, sem baixar estoque durante DDL. Repetições recuperam o mesmo UUID de comprovante. Modo de polling local anterior continua demonstração de decisão; conclusão completa usa BusinessMessaging.

Trilha automática sale_stock_movements mantém snapshots antes/depois e é unida ao histórico existente por view, preservando DTO/paginação e mantendo movimentos manuais imutáveis. Guardas da reserva admitem CONFIRMED somente com provas de pagamento, recibo e todas as baixas. Saga registra a etapa de confirmação sem confundir fase de pagamento com conclusão da venda.

## Migração e teste planejados

Nova migration após038, sem editar V12/V15/V16 aplicadas. PostgreSQL real: upgrade com venda aprovada, rebuild/replay, funções e constraints, duas confirmações concorrentes, duplicação/out-of-order, falha em segundo saldo/auditoria/comprovante revertendo lote, versão limite e valores extremos. HTTP real comprova propriedade/revogação/404 antes de conclusão e comprovante estável. Worker AMQP integra resposta aprovada à conclusão, com reentrega e efeito único. API/worker Verify, Migrate/Validate, JAR, docs/planejamento/segredos e CI separados.

## Evidências e aceite

Implementado V17, etapa de confirmação no worker, comprovante privado e histórico automático. Nesta sessão: API Verify completo12m14s executou21 unitários+261IT; somente2 falhas nos testes HTTP novos (fixture sem inventory.read e comparação via JsonNode/double). Corrigidos somente testes: runner API preservado com -Dit.test=CheckoutHttpIT verify, banco UUID próprio, SUCCESS3m35s,21unitários+11HTTP/0falhas/erros/skips/lint. Demais250IT já passaram sem mudança em código/DDL. Cobertura única local282testes, com repetição dirigida da suíte afetada; CI repetirá o conjunto final.

Worker Verify completo SUCCESS4m11s:6unitários+44IT=50, zero falhas/erros/skips/lint.14BusinessMessagingIT incluem2novos AMQP de conclusão/duplicação/DLQ/replay.8novos SaleConfirmationIT PG cobrem upgradeV16, rebuild/replay, identidade/imutabilidade, TTL protegido, reservas inválidas, falhas em recibo/movimento/eventos, concorrência e falha no segundo saldo. HTTP comprova propriedade/404/revogação, snapshot e dinheiro extremo sem arredondamento.

Migrate V17 SUCCESS7,548s e Validate SUCCESS5,592s; precheck SQL em schema UUID BEGIN/ROLLBACK. JAR API17migrations/0fixtures e worker0DDL/0fixtures. Laboratório users/sales/receipts=0, limpeza de bancos próprios pelo runner. Planejamento60IDs/39requisitos/3hashes,272links Markdown locais/UTF8, escopo/segredos e diff PASS. Ensaio de catálogo97415requisições/0erro/p95 10,219703ms na carga aprovada de laboratório, sem extrapolar produção. Autorrevisão do SQL/locks/rollback, RBAC e diff feita; nenhuma API financeira externa.

Entrega em main sem PR; commit/CI serão vinculados na tarefa viva após publicação. Em validação aguarda aceite explícito; recusa/reconciliação040 e cancelamento041 continuam próprios.
