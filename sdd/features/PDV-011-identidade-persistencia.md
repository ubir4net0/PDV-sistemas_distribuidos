# PDV-011 — Usuários, papéis e permissões

Tarefa viva: https://app.notion.com/p/4092c9a999178171a60aed6ac3aa33d1. Aceite: tabelas users/roles/permissions com FKs, unicidade e seed apenas de desenvolvimento. RF-A01; baseline textual e complementos aprovados pela ADR-007. PDV-009 oferece Flyway/PostgreSQL real em main; permanece Em validação, sem aceite implícito. Responsável/prazo não confirmados.

## Escopo e decisões de implementação

V2 cria users (UUID, name, email canônico único, password_hash BCrypt, active, last_login_at e timestamps), roles, permissions (resource/action únicos) e associações user_roles/role_permissions com FKs e pares únicos. Nomes SQL em inglês conforme ADR-007. JDBC já disponível será o adaptador de identidade; registros Java tipados não expõem hashes em toString. Sem API de gestão de usuários, login ou RBAC neste commit.

BCrypt é uma das alternativas expressas do RNF16, escolhida para este lote com o encoder Spring Security existente. Password_hash aceita somente o formato codificado, nunca senha em texto. Referências de usuários/papéis/permissões são protegidas por FKs; email normalizado em lower/trim, com restrição no banco. Exclusão de usuário não ocorre por cascade de papel.

Seed explícito no profile development insere apenas os papéis ADMIN e OPERATOR, sem usuários, senhas ou concessões operacionais. Nenhum seed no startup padrão. Matriz de concessões pertence à PDV-013; proposta foi apresentada ao proprietário enquanto esta tarefa avança.

## Verificações de aceite

Testar V1 -> V2 preservando marcador sintético existente; rebuild de namespaces UUID próprios -> replay; constraints de email/associações/FKs, rollback de transação e disputa de email concorrente em PostgreSQL real. Seed idempotente e restrito ao profile development, sem conta padrão. Adaptador consulta usuário e autoridades de forma tipada sem logging de dados.

Atualizar os testes de migrations para versão 2 e mover as fixtures de falha/concorrência para V9000, evitando colisão com migrations reais. Fixtures permanecem fora do JAR. clean verify da API, Migrate/Validate/Info locais e CI do commit da tarefa; integridade do planejamento e diff/segredos. Usar banco descartável no Verify e preservar serviços/volumes Amor e Vida.

## Evidências e limites

Verificações executadas nesta task: scripts/api.ps1 -Action Verify BUILD SUCCESS, zero lint e 27 IT (10 identidade, 6 migrations e 11 HTTP), zero falhas/erros/skips. Upgrade V1 -> V2, rebuild/replay, constraints, rollback e disputa concorrente de email passaram em PostgreSQL 16.15 real.

Migrate/Validate/Info locais passaram e confirmaram versão 2. JAR com somente V1/V2 de produção, sem V9000 de teste. Smoke Run -Development apresentou health UP e somente dois papéis, zero usuários e zero concessões; container próprio encerrado. Parser PowerShell, integridade dos 60 IDs/aceites/dependências e hashes das fontes, diff/segredos e links conferidos antes do commit. Java 21 em Docker, sem alterar Java global ou Amor e Vida.

Login/tokens na PDV-012; matriz/guardas na PDV-013; administração de usuários na PDV-014. Commit próprio em main sem PR; CI/hash serão vinculados à tarefa viva. Commit/CI não substituem aceite final do proprietário.
