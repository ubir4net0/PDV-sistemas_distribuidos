# PDV-038 — Implementar Outbox/Inbox e consumo confiável

## Estado e fonte

[Tarefa viva](https://app.notion.com/p/4092c9a9991781cbaf48deecaaacbd81), leitura 02/11/2026 (Manaus), Backlog, RNF06/08. Aceite original: “Publicação transacional via outbox, deduplicação inbox, retry e DLQ documentados/testados.” Sem responsável/prazo atribuídos.

Fontes: [baseline textual, seções 8/12](../../docs/sources/PDV_ESPECIFICACAO_E_BACKLOG.md), [ADR-005](../../docs/decisions/ADR-005-mensageria-e-idempotencia.md), ADR-007/008 aprovadas. Inclusão incremental dos complementos já autorizada; detalhes técnicos de transporte pertencem a esta tarefa.

## Contexto e Definition of Ready

Leituras vivas das dependências PDV-006/009/036: Em validação, com processo AMQP, Flyway e coordenação disponíveis. Artefatos conferidos no código, sem aceite automático de predecessoras. PDV-037 fornece simulador local; transporte deve preservar suas identidades e decisões, sem chamar serviço financeiro.

Pre-flight: main/origin 0f3a073, checkout limpo, PostgreSQL/RabbitMQ PDV healthy, Migrate V15 SUCCESS. Independente do Amor e Vida. ADR-008 aprova estados e separa transporte038, baixa039, reconciliação040 e cancelamento041. Refinamento de040 não impede este incremento técnico.

## Fronteiras e desenho

API grava intenção durável no checkout; trigger na mesma transação acrescenta envelope à outbox. Worker independente publica ao RabbitMQ, consome solicitação, persiste decisão do simulador e intenção de resposta. Outro consumidor lógico aplica resposta à Saga. Banco compartilhado é simplificação acadêmica; filas e transações são reais. Não prometer exactly-once ou commit global PostgreSQL/RabbitMQ.

Modo opt-in BusinessMessaging é separado do polling local SimulatePayments; ativar ambos é erro de configuração. Pode coexistir com expiração. Padrão continua diagnóstico. Nenhuma dependência financeira/fiscal externa.

V16 preserva scripts aplicados, adiciona envelopes imutáveis, estado de publicação, inbox imutável por consumidor/evento e decisão de simulação por operação. Backfill de intenções existentes preserva IDs e não repete pagamento conhecido. Chave técnica de evento única por intenção/etapa, fingerprint por conteúdo semântico, correlação pelo pagamento.

Publicador bloqueia um registro de entrega por transação com SKIP LOCKED, publica mensagem persistente/mandatory e espera confirm sem return antes de marcar publicado. Timeout/nack/sem rota mantém estado recuperável; até5 tentativas com backoff limitado, então DEAD durável no banco. Republicação após queda/confirm perdido é esperada. Nenhum payload/credencial em logs.

Consumidor arbitra consumidor/evento na transação; mesmo ID/conteúdo é replay, conteúdo diferente é erro. Inbox, efeito e intenção da próxima etapa confirmam ou revertem juntos. Ack somente depois do commit; queda antes do ack admite reentrega. Falhas transitórias recebem até3 tentativas locais por entrega (100/200ms); contrato inválido/conflito é permanente; após limite rejeição sem requeue para DLQ. Sem hot loop de requeue. Crashes podem iniciar novo ciclo de tentativas por reentrega; não há limite global de reinícios prometido.

## Contratos de evento e operação

Envelopes JSON fixos v1: type/eventId/paymentId/outcome (nulo na solicitação), até4096bytes, UUIDs obrigatórios, tipo esperado por fila, content-type application/json, messageId igual eventId. Cabeçalhos externos não selecionam classes Java. Solicitação pdv.payment.requested.v1; resposta pdv.payment.result.v1. Valores/método/cenário vêm do banco, não da mensagem. Namespace próprio pdv.business.* ou pdv.test/smoke.*, filas duráveis de request/result e DLQ, routing keys explícitas.

Reserva inválida antes do início é comando tratado sem pagamento; timeout mantém proteção. Resposta antiga/duplicada não pode recriar decisão. Sem baixa física, liberação, comprovante ou conclusão neste commit. O adaptador de aplicação de resultado é evoluído por migration própria nas039/040; transporte não concede autorização para mudar regras financeiras.

Reprocessamento preserva envelope/eventId/paymentId. Procedimento de laboratório deve verificar causa/efeito existente antes de reenviar DLQ ou rearmar entrega DEAD; não criar outra identidade para contornar erro. Objetos de teste e cleanup restritos a nomes UUID próprios.

## Plano de verificação

API Verify com PostgreSQL real: upgrade V15 com dados, trigger outbox atômico/rollback, backfill, constraints/FKs/histórico imutável, rebuild/replay. Worker Verify PostgreSQL/RabbitMQ reais: fluxo completo, mensagens repetidas/eventos diferentes na mesma operação, fingerprint conflitante, dois publicadores/consumidores, commit antes do ack/reentrega, publish antes da marcação/republicação, broker/rota indisponível, rollback da inbox/decisão/resposta, retry limitado e DLQ/replay, payload inválido e logs sanitizados. JAR, scripts/AST, opt-in, planejamento/fontes/links/diff/segredos e CI do SHA próprio.

## Contrato operacional

[Operação e limites do transporte](../../docs/workflows/business-messaging.md). Fontes oficiais de confirms e ack/erros consultadas; comportamento validado contra versões fixadas, sem atualizar dependências.

## Evidências e aceite

Worker Verify final SUCCESS4m21s:6unitários+42integrações=48, zero falhas/erros/skips/lint. Doze novos PostgreSQL/RabbitMQ cobrem fluxo3cenários, IDs repetidos/diferentes por operação, rollback de inbox/decisão/resposta, retry transitório3/100–200ms com contador não transacional de teste, DLQ/replay, conflito e sanitização de logs, ausência de rota/DEAD/rearme, broker desconectado, publish antes da marcação, commit antes do ack com fechamento de canal real, dois publicadores/escritores e contratos inseguros/header de classe. Um import sem uso no teste foi detectado pelo lint inicial14,666s, removido e todo o Verify do worker repetido. API Verify SUCCESS11m15s:21unitários+252integrações=273, zero falhas/erros/skips/lint. Quatro novos PG cobrem upgrade V15/backfill com intenção preservada, chave estável/conflitante/histórico imutável/rebuild/replay; separação decisão/resposta antes da aplicação; rollback completo quando intenção de resposta falha; resultado sem prova e reserva inválida rejeitados. Checkout/regressões verificam rollback da outbox na mesma transação. Bancos Verify removidos. Carga de laboratório81044requests/0erros/p95 14,946185ms na carga aprovada, sem inferir produção. Fontes/testes dos módulos ficaram congelados durante suas respectivas rodadas. Main direta com commit próprio, sem PR. Autorrevisão/testes/CI antes de Em validação; Concluída depende de aceite explícito do proprietário.

Migrate/Validate V16 SUCCESS7,199s/5,856s. Smoke do runner BusinessMessaging+ExpireReservations, polling desligado, startup7,368s/sem porta publicada, três filas de negócio e um consumidor em cada request/result. Container exato parado, --rm/runnerexit0 e todos os objetos dos dois prefixos próprios removidos (0 remanescentes). JAR API16 migrations/sem testes, worker0DDL/0fixtures. Autorrevisão: locks/mesma transação JDBC, identidades/provas, limites de retry/logs, backfill e janelas de falha revisados; CI/SHA próprios vinculados à tarefa viva após push.
