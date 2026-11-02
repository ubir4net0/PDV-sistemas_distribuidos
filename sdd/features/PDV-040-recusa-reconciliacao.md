# PDV-040 — Tratar recusa, timeout e reconciliação

## Estado e fonte

[Tarefa viva](https://app.notion.com/p/4092c9a9991781298a2aec4b5d402663), leitura02/11/2026 (Manaus), Backlog, RF-V02/V03. Aceite: “Recusa libera reserva; timeout deixa estado inconclusivo até reconciliação, sem devolução prematura.” Sem responsável/prazo inventados. Fontes: baseline textual e ADR-008, que mantém políticas de reconciliação nas tarefas próprias.

## Definition of Ready

PDV-030/036/037 relidas ao vivo, Em validação com artefatos verificados. PDV-038 entregue d0ae931, CI verde/Em validação. PDV-039 entregue d31f3c3, testes locais corrigidos/verificados e CI Backend41470242461/Planning41470243048 SUCCESS; conclusão/comprovante disponíveis. Sem aceitar predecessoras. Pre-flight040: main/origin d31f3c3, PG/Rabbit healthy, Migrate V17 sem pendências em6,800s; tarefas/dependências relidas ao vivo. **Refinamento confirmado explicitamente pelo proprietário nesta conversa em02/11/2026 (Manaus).**

## Refinamento aprovado

- Recusa conhecida libera reservado uma vez, sem alterar físico; reserva RELEASED, venda PAGAMENTO_RECUSADO, trilha imutável. Não equivale a cancelamento/estorno.
- INDETERMINADO mantém reserva ACTIVE/protegida mesmo após TTL; silêncio ou retry não inventa resultado e não cria outra cobrança/tentativa.
- No laboratório, ADMIN com permissão própria registra desfecho fictício APROVADO/RECUSADO de tentativa TIMEOUT, inclusive para outra venda, com motivo e identidade idempotente. Registro separado/imutável, sem alterar o resultado original INDETERMINADO. Não amplia leitura de dados pessoais/vendas alheias e não é ação financeira real.
- ADMIN/OPERATOR solicitam reconciliação somente de suas vendas; comando consulta decisão durável e não recebe aprovação do cliente. Sem decisão, continua pendente/202, sem liberação/baixa. Decisão conhecida pode ser aplicada por consumidor interno via outbox/inbox: aprovado confirma/baixa/comprovante pela039; recusado libera uma vez. Resultado tardio/repetido não regride estágio conhecido.
- Sem nova tentativa automática, troca de método, cancelamento, estorno ou devolução neste incremento. Essas políticas permanecem próprias.

## Contratos e verificação planejados

Contrato técnico deste incremento: POST /simulator/payments/{id}/resolution ADMIN/simulation.resolve, motivo e requestId obrigatórios, resposta mínima sem PII; POST /sales/{id}/reconcile ADMIN/OPERATOR/sale.reconcile com propriedade e requestId obrigatório. Mesma identidade/conteúdo recupera resposta; divergência409. GET de venda/comprovante preserva matriz anterior.

V18 acrescentará prova provider_resolutions imutável, operações idempotentes e trilha de liberação. Decisão gera pdv.payment.reconciled.v1 e recusa gera pdv.sale.release.v1 na outbox; fila results/coordenador usa prova durável. Com decisão conhecida, reconcile próprio aplica e finaliza localmente na mesma transação; sem ela devolve202 congelado por requestId (nova consulta GET ou outro requestId observa progresso). Repetição recupera resposta original, inclusive202; conflito409. Mesma decisão/motivo pode ser reconhecida com nova chave sem duplicar ledger. O motivo é normalizado por trim,1–500caracteres. A resposta administrativa expõe somente paymentId/outcome/recordedAt; não concede leitura da venda.

Nova migration após039, preservando eventos/resultados originais e guarda contra desproteção sem prova. API/worker Verify em PostgreSQL/RabbitMQ: recusa/replay/concorrência, desconhecido após TTL, reconciliação pendente, aprovação/recusa tardia e reentrega, decisão conflitante, RBAC/propriedade, rollback completo e compatibilidade com expiração. Upgrade com dados/rebuild/replay, Migrate/Validate, docs/planejamento/segredos e CI próprios.

## Evidências e aceite

Implementado V18, provas/ledger imutáveis, comandos HTTP próprios, etapas AMQP e ADR-009 conforme aprovação explícita. Nesta sessão: API Verify completo18m30s,21unitários+275IT; única falha na ordem alfabética literal do teste de mappings (duas rotas novas estavam incluídas fora de posição). Corrigida somente essa lista, sem alterar produção/DDL: runner API preservado com -Dit.test=RbacHttpIT verify e banco UUID próprio, SUCCESS3m50s,21unitários+23HTTP/0falhas/erros/skips/lint. Demais252IT passaram no Verify completo; cobertura única296testes. CI repetirá o conjunto final.

9novos PaymentReconciliationIT PG: upgradeV17 com recusa/unknown preservados e somente intenção, rebuild/replay, liberação única/imutabilidade, desconhecido após TTL/forgery, dois desfechos tardios preservando resultado original, rollback de prova/outbox e de múltiplos saldos/evento/Saga, auditoria e concorrência. CheckoutHttpIT16/0 inclui5novos HTTP: ADMIN com pagamento alheio sem leitura da venda, OPERATOR negado mesmo com grant, própria reconciliação/202 congelado, replay/conflito, desfechos, validação/revogação, falha de comando revertendo prova ou conclusão e concorrência de decisões. Seed34grants/0contas e mappings atualizados/testados.

Worker Verify completo SUCCESS6m17s:6unitários+48IT=54, zero falhas/erros/skips/lint.18BusinessMessagingIT incluem4novos AMQP: liberação separada/duplicação, mensagens sem prova à DLQ, dois desfechos tardios com unknown antigo sem regressão e rollback/replay da liberação.

Precheck SQL em schema UUID BEGIN/ROLLBACK; Migrate V18 SUCCESS20,219s e Validate SUCCESS9,039s. JAR API18migrations/0fixtures e worker0DDL/0fixtures. Banco de laboratório users/sales/resolutions/releases=0 e RabbitMQ0filas de teste/smoke remanescentes; bancos de Verify próprios removidos pelos runners. Planejamento60IDs/39requisitos/3hashes,289links Markdown locais/UTF8, escopo/segredos e diff PASS. Ensaio catálogo63553requisições/0erro/p95 20,658557ms na carga aprovada de laboratório, sem extrapolar produção. Autorrevisão de locks, prova, imutabilidade, RBAC/propriedade, idempotência e rollback realizada.

Main sem PR; commit e CI serão vinculados à tarefa viva após publicação. Em validação aguardará aceite explícito do proprietário. Nenhuma cobrança/API financeira, nova tentativa, cancelamento, estorno, devolução ou frontend entregue neste incremento.
