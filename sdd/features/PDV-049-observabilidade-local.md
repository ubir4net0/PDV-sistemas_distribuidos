# PDV-049 — Observabilidade local

## Fonte e DoR

Notion lido em 02/11/2026: https://app.notion.com/p/4092c9a999178134a75eedd3c5807cb8. RNF17/18. Aceite: “Trace ID atravessa API/worker/mensagem e logs sem dados sensíveis; dashboards locais.” PDV-005/006/038 lidas, implementações de API/worker/outbox reais conferidas; Em validação preservado, sem aceite implícito. Responsável e datas preservados. Preflight repetido: main sincronizada, Compose saudável e Migrate V18 SUCCESS9s antes do código.

## Contratos e invariantes

Observabilidade técnica incremental da ADR-007, sem fornecedor externo ou dados pessoais. Trace da operação = UUID de pagamento sem hífens; span raiz da API derivado da identidade da operação, spans de publicação/consumo novos e parentes explícitos. Publicação aponta à raiz; consumo aponta ao publicador recebido, sem afirmar causalidade completa de eventos derivados. Publicador envia traceparent W3C v00; consumidor valida formato, identidade esperada e descarta header inválido, usando pai raiz como fallback compatível. Mesmo trace continua em retries/replays e etapas subsequentes; não é uma garantia de exactly-once ou tracing de todos os endpoints HTTP.

Log JSON de evento técnico possui somente ID, instante, serviço, evento permitido, trace/span/pai, resultado permitido e duração. Sem payload, URL arbitrária, headers, identidade do usuário, método financeiro, motivo ou stacktrace. API e worker centralizam os registros em tabela própria PostgreSQL V19; logs de negócio são gravados após o efeito transacional, falha de telemetria não desfaz pagamento. Console emite a mesma estrutura para fallback. Repositório não recebe logs de runtime.

GET /operations/telemetry exige ADMIN; no-store, últimas100 observações e agregados das últimas24h (quantidade/erros/duração média), filtro opcional de trace32hex exato. Painel local no frontend com métricas e spans/JSON seguro, sem exposição de Actuator interno ou credenciais. Leitura para operação administrativa, não concessão de acesso às vendas alheias. Retenção/escala/exportação para collector de produção ficam fora do laboratório.

## Aceite e plano de verificação

PostgreSQL real: upgrade V18→V19 preservando operação, constraints, replay e rebuild controlado em namespaces UUID. API: autorização401/403/200, filtros inválidos400, schema/ausência de segredos, correspondência da raiz do checkout. Worker/AMQP reais: traceparent e relações de spans, logs centralizados, aprovação/recusa/TIMEOUT, replay sem efeito duplicado e header inválido sem injeção. Typecheck/unit/build e E2E administrativo contra stack própria. Contratos/planejamento/diff/segredos; commit separado main sem PR, CI do SHA e Em validação aguardando aceite.

## Evidências

CI do primeiro SHA6a2fca0: frontend16/17 passou; asserção antiga de loading dependia de600ms e perdeu a janela no runner. Primeira barreira ainda bloqueava apenas a primeira leitura; StrictMode pode cancelá-la e iniciar outra. A barreira final segura todas as leituras até observar loading, sem alteração de comportamento da aplicação. Repetição completa de browser/CI requerida para o SHA corrigido.

Implementação concluída e autorevisada. Frontend typecheck/23 unit/build PASS;17 Playwright PASS1m42, stack API/PG/Rabbit/worker própria, correlação raiz→publicação→consumo, filtro/erro/reconsulta e regressões. Desktop/celular inspecionados sem overflow. Worker Verify completo55/0 (6unit+49IT), incluindo19 cenários AMQP;83 linhas JSON sintéticas parseadas com service/event permitidos. Migrate/Validate V19 PASS; upgrade/rebuild/replay fazem parte da suíte API ainda em execução no momento do commit (21unit/0 e integrações já executadas sem falha até aqui). O resultado completo local e a CI do SHA final serão registrados na tarefa viva antes de Em validação.

Contratos61rotas/58DTO/71schemas/15 exemplos válidos e11 inválidos, planejamento/fontes,348links/UTF-8/diff PASS. Banco/recursos E2E removidos. Sem nova dependência, fornecedor ou serviço externo. Logs gerais de framework continuam separados; a centralização trata spans técnicos permitidos. Observação pós-commit pode faltar em queda do processo e não é ledger. Commit próprio main sem PR; aceite explícito pendente.

Resultado final local: API Verify completo SUCCESS15m00,21unit+281IT=302, zero falhas/erros/skips; upgrade V18→V19/constraints/rebuild/replay aprovados. Repetição final de browser17/17 PASS1m24 depois de bloquear todas as leituras StrictMode no cenário de loading. As duas rodadas intermediárias registraram16/17; código da aplicação não precisou mudar. CI Backend dos SHAs6a2fca0/95bd710 aprovou302+55. A CI do último commit de teste será registrada no Notion.
