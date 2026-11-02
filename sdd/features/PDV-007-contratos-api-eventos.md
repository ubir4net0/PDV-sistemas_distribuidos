# PDV-007 — OpenAPI e contratos de evento

## Estado e fonte

[Tarefa viva](https://app.notion.com/p/4092c9a9991781599d94cbac5955bf23), relida em02/11/2026 (Manaus), Backlog. Aceite original: DTOs, erros padronizados, versionamento de eventos e exemplos acessíveis à equipe. Responsável/prazo não informados; não inferir. Baseline textual adotada na ADR-007; ADR-008/009 definem estados e reconciliação aprovados.

## Definition of Ready

PDV-003/005 e página de decisões relidas ao vivo; artefatos técnicos disponíveis em main, sem aceite das predecessoras. Bloqueio histórico DEC-01/02/03 resolvido para o contrato atual: estados na ADR-008/009, OC/cardinalidade na ADR-007. Originais acadêmicos continuam ausentes; equivalência não é requisito inventado para documentar o código autorizado. Pre-flight main8813856 sincronizada, PG/Rabbit healthy, Migrate V18 sem pendências8,186s.

## Fronteiras e contrato

OpenAPI3.1 versionada com todos os mappings públicos atuais, DTOs, Bearer opaco, autorização/propriedade, respostas/erros e headers de concorrência/idempotência. Sem alterar regras de negócio, schema, portas ou autenticação. Padronizar as lacunas encontradas de erro HTTP: parâmetro/header obrigatório ausente400 invalid_request e mídia incompatível415 unsupported_media_type, com teste HTTP real. Tipos futuros sem implementação (compras, cancelamento etc.) são pendências explícitas, não rotas funcionais publicadas. Campos desconhecidos de entrada são rejeitados; dinheiro e int64 preservam precisão, sem sugerir Number inseguro no consumidor.

JSON Schema versionado para os cinco envelopes atuais de negócio e contratos de diagnóstico separados; exemplos sintéticos sem credenciais utilizáveis. Evolução incompatível exige novo type/version e coordenação produtor/consumidor; cabeçalho de classe Java não é contrato. Retry/DLQ/inbox/outbox seguem038–040.

## Verificação planejada

Lint OpenAPI por ferramenta fixada/lockfile, validação JSON Schema de exemplos positivos/negativos, cobertura de mappings e campos DTO confrontados com fontes, referências/respostas/headers, links/UTF8/planejamento/segredos. CI dedicada repete as verificações. Sem novo DDL nem teste de comportamento espelhado; CI backend existente continua executando regressões reais. Frontend015 consumirá o contrato de autenticação em browser real com API própria.

## Evidências e aceite

Executado em 02/11/2026: `npm ci --ignore-scripts --no-audit --no-fund`, `npm run contracts:verify` e `npm run contracts:docs` aprovados. Lint, 59 operações HTTP, campos de 52 records Java, 65 schemas compilados, 15 exemplos válidos e 11 inválidos aprovados; HTML gerado localmente (ignorado). Um erro inicial no parser de parênteses das anotações foi corrigido antes desta execução.

`./scripts/api.ps1 -Action Verify`: Checkstyle, compilação, JAR, 21 unitários e 276 integrações HTTP/PostgreSQL (297 total), zero falhas/erros/skips, BUILD SUCCESS em 14:38. RbacHttpIT: 24 testes, incluindo parâmetro ausente e mídia incompatível. Banco UUID próprio removido pelo script; sem alteração de DDL. Worker não foi alterado; suas integrações serão repetidas pela CI existente.

`node scripts/verify-planning.mjs`: 60 IDs, 39 requisitos e três hashes aprovados; 305 links Markdown locais/UTF8 e `git diff --check` aprovados. Autorevisão dos contratos/erros e limites documentados. CI do SHA publicado será vinculada no card, sem presumir resultado. Commit próprio em main; Em validação aguarda aceite explícito. Sem aceitar predecessoras, criar tarefas extras ou inferir entrega dos contratos futuros.
