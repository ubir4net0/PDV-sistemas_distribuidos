# Outbox, Inbox e RabbitMQ — PDV-038

[Spec](../../sdd/features/PDV-038-outbox-inbox.md), [ADR-005](../decisions/ADR-005-mensageria-e-idempotencia.md) e [estados aprovados](../decisions/ADR-008-estados-venda-pagamento.md). Banco compartilhado entre API/worker é simplificação acadêmica. Transações PostgreSQL e RabbitMQ são independentes; reentrega/republicação é esperada.

## Execução

Com serviços PDV healthy e migrations API aplicadas:

```powershell
./scripts/api.ps1 -Action Migrate
./scripts/worker.ps1 -Action Verify
./scripts/worker.ps1 -Action Run -BusinessMessaging -ExpireReservations
```

BusinessMessaging habilita publicador e consumidores de negócio; não combinar com SimulatePayments, que mantém o demonstrador local anterior. Expiração pode coexistir. Padrão é desligado; sem modos ativos o worker continua diagnóstico sem datasource de negócio. Configuração nativa: PDV_BUSINESS_MESSAGING_ENABLED=true, PDV_BUSINESS_PREFIX=pdv.business.payments, credenciais PG/Rabbit privadas e PDV_DB_SCHEMA. Worker valida funções, sem executar migrations. Não há serviço financeiro/fiscal externo.

## Transações e identidades

V16 cria outbox_events imutáveis, outbox_deliveries, inbox_messages imutável por consumidor/evento e simulation_decisions imutável por pagamento. Trigger em payment_intents grava solicitação na transação do checkout. Backfill cria solicitação de intenções antigas preservando identidade da operação; não executa pagamento na migration.

Solicitação consumida: bloquear identidade de inbox → reconhecer replay/conflito → iniciar e proteger pagamento → persistir decisão determinística/intenção de resposta → inbox → commit → ack. Resposta consumida: validar prova durável → aplicar estado da Saga → inbox → commit → ack. Decisão e intenção de resposta não podem divergir por falha intermediária. Reserva inválida antes de iniciar é comando tratado sem pagamento; sem reanimar reserva. Fingerprint usa campos semânticos, não formatação JSON ou cabeçalhos de classe.

Consumidores lógicos payment-simulator-v1 e sale-coordinator-v1 têm deduplicação independente. EventId protege reentrega; paymentId/chave técnica de etapa protegem o efeito mesmo quando um produtor envia outro EventId. Reutilizar EventId com outro conteúdo é conflito e vai à DLQ. UUIDs globais e payload são persistidos uma vez; envelope inclui type/eventId/paymentId/outcome. Solicitação pdv.payment.requested.v1 tem outcome=null; resposta pdv.payment.result.v1 tem APROVADO/RECUSADO/INDETERMINADO. MessageId deve corresponder a eventId, correlação ao pagamento. Campos desconhecidos/tipo/MIME/tamanho>4096 são rejeitados. Header de classe Java não participa da desserialização.

## Publicação, retry e falhas

Publicador consulta lote de10 (limite configurável1–100), a cada1s. Uma entrega é bloqueada por transação com SKIP LOCKED, evitando dois publicadores na mesma linha. Mensagem persistente/mandatory só é marcada PUBLISHED depois de confirm positivo sem return. Falha/timeout/sem rota conserva envelope e incrementa tentativa, com backoff1/2/4/8/16s; após5 tentativas, DEAD durável no banco. Queda depois do confirm antes da marcação causa republicação da mesma identidade, tratada pela inbox. PUBLISHED comprova aceite pelo broker, não conclusão do negócio.

Consumidor manual/prefetch1 faz até3 tentativas locais por entrega para falhas de persistência, com100/200ms entre tentativas; cada tentativa usa transação nova. Contrato/conflito/invariante permanente vai diretamente à DLQ. Após limite, rejeição sem requeue e DLX; sem hot loop de requeue. Reinício antes do ack pode iniciar outro ciclo de tentativas; não há limite global de crashes prometido. Logs só constantes/contagens, sem payload/credenciais.

Timeouts: conexão5s/socket10s/query5s, lock2s/statement4s nos comandos, confirmação5s. Rodadas não se sobrepõem no mesmo job. Namespace padrão pdv.business.payments, alternativo restrito a pdv.business/test/smoke.*. Objetos duráveis: .exchange/.errors, .requests/.results/.dead, routing keys payment.request/payment.result/rejected. Diagnóstico usa outro prefixo e contrato.

## Inspeção e replay no laboratório

Verificar entrega, inbox, estado/decisão do pagamento e causa antes de replay. Para DEAD de publicação, corrigir broker/rota e executar `SELECT pdv.retry_outbox_event('UUID-do-evento'::uuid)` com acesso administrativo local; função só rearma DEAD, sem mudar envelope/IDs. Para DLQ, inspecionar e reenviar o envelope original à routing key do tipo, preservando EventId/MessageId/PaymentId; remover da DLQ após confirmação de publicação. Ack perdido pode duplicar, e inbox deve reconhecer. Nunca criar outra identidade para contornar conflito ou apagar histórico de pagamento. Painel/admin público pertence às tarefas próprias.

Filas clássicas duráveis e mensagens persistentes são verificadas no broker local. Replicação/HA e falha física do nó não foram ensaiadas; DLX neste laboratório não promete ausência de perda em toda falha do broker. As [garantias de confirms/ack](https://www.rabbitmq.com/docs/confirms) são distintas; o [tratamento de falhas Spring AMQP](https://docs.spring.io/spring-amqp/reference/amqp/receiving-messages/async-annotation-driven/error-handling.html) depende do modo manual configurado. Sem promessa de exactly-once.

O incremento038 registra aprovação/recusa/desconhecido na Saga, sem baixa, liberação ou comprovante. Confirmação039 e recusa/reconciliação040 evoluem etapas em migrations próprias; cancelamento/estorno041 permanece separado.

## Evolução PDV-039

Aprovação também grava pdv.sale.confirm.v1 (outcome APROVADO) na outbox. A fila results aceita essa etapa; o coordenador confirma estoque/comprovante/inbox juntos. [Contrato de conclusão](sale-confirmation.md). A descrição do incremento038 acima permanece como histórico do seu escopo.

## Evolução PDV-040

Fila results aceita pdv.payment.reconciled.v1 (APROVADO/RECUSADO) e pdv.sale.release.v1 (RECUSADO). Provas duráveis são obrigatórias; resultado desconhecido antigo não regride o reconciliado. Liberação/inbox/ledger/evento confirmam juntos e só alteram reservado. [Contrato](payment-reconciliation.md).
