# PDV-026 — Saldos físico, reservado e disponível

## Estado, fonte e aceite original

Leitura viva em 31/10/2026: https://app.notion.com/p/4092c9a9991781c2938ada931c5332db. Backlog, responsável/prazo não informados. RF-E04/RNF10, baseline textual seções 7–9. Aceite original: “Invariante disponível=físico-reservado; saldos nunca negativos; trilha auditável.”

## Contexto, dependências e Definition of Ready

PDV-009/020 relidas ao vivo, Em validação sem aceite inferido; Flyway V1–V9 e produtos JPA entregues, incluindo testes/CI de catálogo. Base main 2791fd9 sincronizada; três specs deste lote são os únicos arquivos locais novos. Docker Desktop iniciado, PostgreSQL/RabbitMQ PDV saudáveis, Migrate V9 SUCCESS. Proprietário confirmou explicitamente o refinamento e “Usar quantidades inteiras” nesta sessão; DoR satisfeito para o incremento.

## Refinamento confirmado pelo proprietário

ADMIN e OPERATOR consultam saldos/trilha; somente ADMIN registra entradas, saídas e ajustes na PDV-027. Produtos existentes/novos começam com físico=reservado=0; não inventar saldos legados. Movimento de estoque é permitido em produto de qualquer estado, com motivo e ator, sem afirmar permissão de venda. Quantidades inteiras não negativas, BIGINT/Long e proteção de overflow, sem arredondar frações. Reservas não são alteráveis manualmente neste lote; implementação/expiração pertencem à PDV-029/030.

## Fronteiras, contratos e invariantes planejados

V10 cria inventory_balances e inventory_openings por produto, FKs RESTRICT, versão independente do catálogo e timestamps. Disponível calculado como físico-reservado, sem cópia divergente. CHECK físico>=0, reservado>=0, reservado<=físico e version>=0. Backfill e trigger AFTER INSERT de produtos inicializam zero e abertura técnica ZERO_INITIALIZATION na mesma transação, inclusive SQL/JPA, sem usuário fictício ou inferência de estoque legado. Abertura imutável por trigger rejeita UPDATE/DELETE; ator da criação HTTP continua no histórico de catálogo, sem atribuir usuário à migração.

GET /inventory/{productId}, GET /inventory paginado por productId (page=0,size=20, máximo100) e GET /inventory/{productId}/opening exigem inventory.read e ADMIN/OPERATOR. Sem escrita direta, saldo ausente 404, entrada inválida400, acesso401/403. SQL de leitura/constraints pertence ao domínio crítico de saldo conforme arquitetura; CRUD de catálogo permanece JPA. Sem projeção ou sincronização AMQP simulada. Ajustes/movimentos entram na tarefa seguinte.

## Plano de verificação e migrations

Migration seguinte V10; PostgreSQL real, upgrade V9→V10 preserva produtos, inicialização zero, FK/CHECKs e trilha, rebuild/replay em objetos descartáveis próprios. Testes HTTP de leitura/paginação/401/403/revogação e produto inexistente; criação de produto com saldo/trilha atômicos. Verify completo, Migrate/Validate/Info, planejamento/links/diff/segredos, autorevisão, commit e CI próprios.

## Evidências, revisão e aceite

Verify Java21/PostgreSQL16.15 SUCCESS em 7m19s: **21 unitários +167 IT (188)**, zero falhas/erros/skips, Checkstyle zero. Dez HTTP novos de saldos e dois IT de persistência verificam abertura zero, cálculo com reservado sintético, versão independente, paginação, acesso/revogação, FK/CHECKs, extremos BIGINT, imutabilidade ativa e rollback real de produto/saldo quando abertura falha. Upgrade V9→V10 preserva produto/preço, backfill zero auditado, criação SQL/JPA e rebuild/replay passam. Regressão de catálogo: 106.518 requisições/zero erros, p95 local9,944ms. Banco próprio removido.

Migrate/Validate/Info V10 SUCCESS, JAR somente V1–V10 e nenhuma fixture. Planejamento/60 IDs/aceites/dependências/fontes, 60 links locais/UTF-8, diff/staging/credenciais aprovados. Autorrevisão confirmou constraints, abertura sem usuário fictício e leituras/guardas de todos os mappings. Commit e CI próprios ligados na tarefa viva após publicação, sem PR; Em validação aguardará aceite, sem aceitar predecessoras.

Fixtures HTTP limpam apenas UUIDs próprios, em banco Verify UUID ou pdv_ci com CI=true, numa transação de teardown com session_replication_role LOCAL; permite remover trilha técnica para limpar testes sem bypass no código/migration de produção. Guardas são exercitados ativos, e teste confirma restauração origin e nova rejeição de DELETE após cleanup. Classes de fixture não entram no JAR. Sem sincronização por eventos, reserva, baixa ou venda implementadas por esta entrega.
