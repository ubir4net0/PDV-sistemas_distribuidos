# PDV-013 — RBAC em todos os endpoints existentes

## Estado, fonte e aceite

Tarefa viva lida nesta execução: https://app.notion.com/p/4092c9a9991781168ae2c1400b42c527. RF-A01; aceite original: matriz de permissões testada para operador e administrador; 401/403 corretos. Responsável/prazo não confirmados, preservados. PDV-011 (011436c) e PDV-012 (d404409) têm testes/CI próprios aprovados e artefatos necessários disponíveis; seguem Em validação, sem aceite implícito.

O proprietário aprovou explicitamente a pergunta da matriz inicial com “Aprovar essa matriz inicial”: health/login públicos; autenticado consulta próprio perfil/encerra próprio token; somente administrador consulta catálogo; demais rotas negadas até tarefas próprias. Essa aprovação fecha o recorte inicial de RBAC da DEC-06, sem definir permissões de funcionalidades futuras ou outros parâmetros pendentes.

Preflight: main sincronizada com origin/main, PostgreSQL/RabbitMQ PDV saudáveis, Migrate SUCCESS em V3. Sem alterações no Amor e Vida. Commit direto em main, sem PR conforme ADR-001.

## Contrato e matriz aprovada

| Método/caminho | Anônimo | Operador | Administrador |
| --- | --- | --- | --- |
| GET /actuator/health e probes liveness/readiness | 200 | 200 | 200 |
| POST /auth/login | Credenciais válidas 200; inválidas 401 | Igual | Igual |
| GET /auth/me | 401 | 200, próprio perfil | 200, próprio perfil |
| POST /auth/logout | 401 | 204, próprio token | 204, próprio token |
| GET /access/permissions | 401 | 403 | 200 com access.permissions.read; sem concessão 403 |
| Outros métodos/caminhos | 401 | 403 | 403 |

Catálogo consulta permissions real no PostgreSQL, sem gestão/mutação. Exige ADMIN e concessão access.permissions.read; papel sem concessão não recebe acesso e conceder a permissão ao operador não o transforma em administrador. Segurança na rota e no método de aplicação com @EnableMethodSecurity/@PreAuthorize; denyAll permanece fallback. Token não contém claims de RBAC em cache: papéis/permissões são consultados por requisição, incluindo remoções.

Seed somente development acrescenta access.permissions.read e associa ADMIN de forma idempotente; OPERATOR não recebe essa concessão. Nenhuma conta/papel/permissão é criada no profile padrão. Development reaplica a concessão no startup explícito; não usar em produção. Sem nova DDL: V1–V3 imutáveis.

## Verificações planejadas

HTTP real + PostgreSQL: matriz anônimo/operador/admin, catálogo real, perfil próprio/logout, métodos/caminhos não liberados, administrador sem concessão, operador com concessão indevida, remoção de papel/permissão aplicada na próxima requisição. Verificar cobertura de todos os mappings de controllers da aplicação para exigir revisão da matriz em novos endpoints. Repetir testes existentes de autenticação, constraints, migrations/rebuild/replay/concorrência, e seed development idempotente restrito a ADMIN. Verify próprio, integridade do planejamento, smoke JAR, autorevisão, commit e CI próprios.

## Limites e evidências

scripts/api.ps1 -Action Verify executado nesta tarefa: BUILD SUCCESS em Java 21/Docker, Checkstyle zero, 3 unitários + 75 IT, zero falhas/erros/skips. Os 23 RbacHttpIT cobrem matriz, perfil/logout próprios, catálogo real, caminhos/métodos negados e mudanças de autoridade. Regressões de autenticação, identidade, constraints e migrations/rebuild/replay/concorrência passaram contra PostgreSQL 16.15 real. Após reforçar o teste para exigir métodos HTTP explícitos, o alvo RbacHttpIT#everyApplicationControllerMappingRequiresAnExplicitMatrixEntry foi repetido em novo banco UUID próprio: SUCCESS, mais 3 unitários + 1 IT, zero falhas/skips, com limpeza confirmada.

Migrate do preflight confirmou V3 sem mudanças; nenhuma migration aplicada foi alterada. JAR contém apenas V1–V3 de produção, sem V9000. Smoke Run -Development -TokenTtlSeconds 900: health UP, catálogo anônimo 401, zero usuários, uma concessão de catálogo para ADMIN e zero concessões OPERATOR. Container próprio encerrado. Planejamento dos 60 IDs/aceites/dependências/hashes, parser PowerShell, 60 links locais/UTF-8 e diff/segredos conferidos. Autorrevisão: guardas de rota e método exigem administrador/concessão, fallback permanece fechado, consultas não alteram o catálogo, papéis/concessões removidos são observados na próxima requisição.

Sem CRUD de usuários/roles/permissões, frontend, regras de negócio futuras ou segurança de produção concluída. RNF15/55, rate limit, TLS e políticas de domínio permanecem nas tarefas próprias. Nenhuma outra tarefa é aceita por esta entrega. Commit próprio em main, sem PR; hash/CI serão vinculados à tarefa viva. Em validação aguarda aceite final do proprietário, distinto da aprovação já recebida da matriz inicial.

Fontes técnicas: [Spring Security — método](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html) e [requisições](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html).
