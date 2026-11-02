# PDV-030 — Expiração segura pelo worker

## Estado, fonte e aceite

Leitura viva em 01/11/2026: https://app.notion.com/p/4092c9a99917816b9e42e02e93001099. RF-V02, sem responsável/prazo definidos. Original: “Worker libera no máximo uma vez, evita corrida com pagamento aprovado e gera status.” Dependências PDV-029 (neste lote) e PDV-006 aac69cd entregue Em validação. Requer leitura viva/preflight próprios após PDV-029.

## Recorte confirmado e contrato planejado

Proprietário confirmou polling 5s/até100 reservas por rodada e barreira transacional antes de iniciar pagamento; aprovado/incerto retido até reconciliação. Reutilizar função de expiração da V12, PostgreSQL compartilhado conforme arquitetura acadêmica. Cada reserva em transação própria, seleção indexada e SKIP LOCKED; duas instâncias não liberam duas vezes. Estado EXPIRED e evento imutável persistidos junto ao saldo. Sem pagamentos/Saga/outbox/inbox ou liberação de resultado desconhecido.

Worker independente oferece modo explícito de expiração, ativado por configuração/runner com PostgreSQL real; diagnóstico RabbitMQ existente preservado. Startup valida contrato de banco, sem migrar implicitamente. Testes PostgreSQL reais com migrações API em schemas próprios, dois workers, proteção de pagamento/expiração, rollback/auditoria e renovação, scheduler/rodada limitada; regressões AMQP e API completas. Runner/CI devem fornecer banco/broker e cleanup restrito, sem credenciais no Git/logs. Sem endpoint HTTP novo.

## Evidências e aceite

Plano antes do código. Implementação iniciada após a entrega técnica/verificação da PDV-029; testes/commit/CI separados. Em validação após evidências, sem aceite inferido.

## DoR e plano técnico antes do código

PDV-029 be78af8 entregue com234 testes e CI Backend41372552458/Planning41372552537 SUCCESS; lida novamente sem aceite implícito. Main sincronizada/serviços saudáveis/MigrateV12 SUCCESS neste preflight. Modo opt-in PDV_RESERVATION_EXPIRY_ENABLED=true ou runner -ExpireReservations; 5s/100 por padrão. Driver/JDBC condicionais, sem banco exigido no diagnóstico normal. Worker valida função V12, nunca aplica migrations. Cada item usa TransactionTemplate/lock_timeout2s/statement_timeout4s, queryTimeout5s e driver connect5s/socket10s; falhas não liberam reserva, rodada posterior tenta novamente. Logs só contagens/erros constantes. SQL de migração API entra apenas nos recursos de teste do worker; JAR de produção não contém DDL/fixtures.

## Evidências executadas e autorevisão

Verify worker SUCCESS em2m12s:6unitários+20integrações=26, zero falhas/erros/skips/lint. Nove novos PostgreSQL reais cobrem2workers/efeito único, status/auditoria/físico, rodada limitada, bloqueio de pagamento em outra transação atravessando prazo, proteção tardia rejeitada, renovação/candidato antigo, rollback de saldo/auditoria e retry, além do bean @Scheduled real5s. Os11 testes AMQP e6 unitários existentes passaram. Banco Verify/schema/topologia próprios removidos. JAR sem DDL/fixtures.

Smoke do JAR independente com -ExpireReservations validou contrato V12 e iniciou modo de expiração real, sem porta HTTP; encerramento exit0/container removido e topologia smoke excluída por namespace exato (zero remanescentes). Main/serviços/Migrate V12 aprovados antes do código. Nenhuma migration/API alterada neste incremento; regressão API completa será confirmada no CI do SHA próprio. Não equivale à Saga/pagamento/reconciliação futuros. Publicação/CI serão vinculadas no card, sem aceite inferido.

Planejamento/fontes, sintaxe PowerShell, oito docs UTF-8/72 links locais, diff e scan de credenciais staged PASS. Autorevisão aprovada; sem falha de produto nesta execução.
