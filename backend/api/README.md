# API PDV — Java 21 / Spring Boot

API iniciada na [PDV-005](../../sdd/features/PDV-005-api-scaffold.md), com Spring Boot 4.1.2, Maven 3.10.0 e Wrapper oficial 3.3.4. Flyway/PostgreSQL incluem V1 de namespace, V2 de identidade e V3 de tokens. [Modelo e seed de desenvolvimento](../../docs/workflows/identity-security.md). Health/probes, login, perfil próprio e logout estão disponíveis. Catálogo de permissões exige ADMIN e concessão explícita (PDV-013); POST /customers usa JPA e documento validado/único; catálogo, fornecedores, estoque, reservas e carrinho estão disponíveis nos incrementos abaixo; checkout/vendas e comunicação AMQP de negócio permanecem pendentes.

## Build e execução no Windows

Com Docker ativo e PostgreSQL PDV saudável, executar na raiz do repositório:

```powershell
./scripts/api.ps1 -Action Version
./scripts/api.ps1 -Action Verify
./scripts/api.ps1 -Action Migrate
./scripts/api.ps1 -Action Validate
./scripts/api.ps1 -Action Info
./scripts/api.ps1 -Action Run -TokenTtlSeconds 900 -ReservationTtlSeconds 900
```

A toolchain oficial Java 21 é fixada por digest em [infra/java-toolchain.json](../../infra/java-toolchain.json). A versão Maven executada é controlada pelo Wrapper, independentemente do Maven pré-instalado na imagem. O cache fica no volume local `pdv-api-maven-cache`; classes, relatórios e JAR ficam em `backend/api/target`, ignorado pelo Git. O primeiro build requer acesso aos repositórios oficiais. Nenhum Java global ou serviço do Amor e Vida é alterado.

`Verify` executa `clean verify`, incluindo autenticação HTTP, identidade e migrations contra PostgreSQL real. Usa banco UUID descartável e remove somente esse banco ao terminar. `Migrate/Validate/Info` operam o banco PDV local existente sem limpeza. `Run` exige o JAR compilado e TTL positivo explícito, migra/valida no startup, usa 127.0.0.1:8081 e fica em primeiro plano no container `pdv-api-local`; Ctrl+C ou `docker stop pdv-api-local` encerra esse container. Nome existente não é sobrescrito. Escuta interna Docker em 0.0.0.0, publicação do host restrita a loopback.

Com **JDK 21** instalado e selecionado, configurar PDV_DB_URL/USER/PASSWORD em ambiente privado conforme o [guia de migrations](../../docs/workflows/database-migrations.md). Para verify nativo usar banco descartável próprio. Em backend/api:

```powershell
./mvnw.cmd -B -ntp clean verify
java -jar target/pdv-api-0.0.1-SNAPSHOT.jar
```

Em Unix: `./mvnw -B -ntp clean verify`. O build exige Java 21 e Maven >= 3.10, < 4; o Wrapper fixa Maven 3.10.0 e verifica checksum da distribuição. O JRE 17 padrão observado no Windows não atende ao build. A verificação desta entrega usa Java 21 no container; build nativo Windows com JDK 21 não foi executado.

## Contrato técnico atual

| Requisição | Resposta |
| --- | --- |
| GET /actuator/health | 200, status UP e nomes dos grupos de probes, sem detalhes internos |
| GET /actuator/health/liveness | 200, estado de liveness do processo |
| GET /actuator/health/readiness | 200 após inicialização com PostgreSQL acessível; sem detalhes internos |
| POST /auth/login | 200 com token Bearer opaco; entrada inválida 400; credenciais inválidas 401 |
| GET /auth/me | 200 somente para o próprio token válido |
| POST /auth/logout | 204, revogação somente do token atual |
| GET /access/permissions | 200 para ADMIN com access.permissions.read; anônimo 401; demais 403 |
| POST /customers | 201 para ADMIN/OPERATOR com customer.create; 400 inválido; 409 duplicado |
| GET /customers, /customers/{id}, /customers/{id}/history | Consulta paginada/detalhe/histórico com customer.read |
| PUT /customers/{id} | Edição com customer.update e versão |
| PATCH /customers/{id}/status | Ativação/inativação só ADMIN com customer.status e versão |
| GET /customers/{id}/addresses e /customers/regions | Endereços paginados e consulta regional com customer.read |
| POST /customers/{id}/addresses; PUT /customers/{id}/addresses/{addressId} | Endereços com customer.address_write, versão no PUT |
| Outros caminhos/métodos | 401 anônimo, 403 autenticado; sem redirect, Basic ou sessão |

Health agregado e readiness consultam PostgreSQL; liveness continua independente do banco. RabbitMQ não é consultado pela API. UP não significa fluxo de venda pronto. Somente health é exposto; componentes, detalhes, descoberta e /actuator/flyway permanecem ocultos/bloqueados. Nenhum usuário/senha em memória é gerado. Autenticação é implementada na PDV-012; outros caminhos continuam bloqueados.

Startup, migrations e testes exigem credenciais PostgreSQL por ambiente. Startup e testes também exigem PDV_AUTH_TOKEN_TTL_SECONDS; Verify/CI usam 900 somente como dado técnico. O script as lê do Compose local sem imprimir valores; o usuário administrativo é bootstrap de laboratório, não uma política de privilégios de produção. CSRF está desabilitado pelo contrato exclusivo de Authorization: Bearer, sem autenticação por cookie/sessão. JPA de clientes está ativo com ddl-auto=validate; AMQP pela API segue pendente. Para iniciar dependências, consultar [infra/README.md](../../infra/README.md).

## Organização por domínio

Namespace `dev.pdv.api`, com entrada `PdvApiApplication` e configuração transversal em `configuration`.

| Módulo | Escopo futuro |
| --- | --- |
| access | Identidade, usuários e acesso |
| customers | Clientes |
| catalog | Produtos e categorias |
| purchasing | Fornecedores e compras |
| inventory | Saldo, reservas e movimentações |
| sales | Carrinho, venda e Saga |

Cada módulo separa `domain` (regras), `application` (casos de uso) e `infrastructure` (adaptadores), com fronteiras descritas em package-info.java. access.http expõe identidade; customers, catalog e purchasing.infrastructure expõem cadastros HTTP/JPA reais. inventory implementa saldos/aberturas por SQL crítico; sales implementa carrinho/itens, sem checkout/venda/Saga. A [ADR-007 aprovada](../../docs/decisions/ADR-007-proposta-base-dominio.md) define referência textual, estados de OC, fornecedor N:1 e complementos; detalhes aprovados estão nas specs próprias. Movimentações/reservas, compras, eventos e Saga continuam nas tarefas seguintes.

## Verificações e fontes

ScaffoldHttpIT verifica health/probes, ocultação de detalhes, bloqueios, ausência de contas padrão e Flyway/JDBC reais. MigrationsIT verifica replay/rebuild/checksum/DDL transacional/clean bloqueado/migradores concorrentes. RbacHttpIT cobre matriz e todos os mappings, autenticação e revogação. AuthenticationTest/AuthHttpIT/AuthPersistenceIT verificam credenciais/tokens/provisionamento opt-in. Clientes, catálogo e fornecedores possuem testes HTTP/PostgreSQL de integridade, concorrência, versões e rollback de auditoria; CatalogLoadIT mede a carga confirmada. Failsafe em verify executa integrações; Checkstyle inclui fontes/testes. Estoque, AMQP de negócio e Saga não estão cobertos como funcionalidades implementadas. [CI e gates](../../docs/workflows/ci.md) usam PostgreSQL próprio e verificam worker separadamente.

Bootstrap obtido do [Spring Initializr](https://start.spring.io), compatibilidade conferida nos [requisitos Spring Boot](https://docs.spring.io/spring-boot/system-requirements.html). Ver também [Maven Wrapper](https://maven.apache.org/tools/wrapper/), [Actuator](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html) e [auto-configuração de contas em memória](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/security/autoconfigure/UserDetailsServiceAutoConfiguration.html). Versões selecionadas estão fixadas no POM/Wrapper/toolchain; não atualizar silenciosamente.

[Cadastro de clientes — PDV-016](../../docs/workflows/customers.md): POST /customers exige customer.create mais ADMIN/OPERATOR; 201 com Location, 400 validação, 409 duplicidade. V4 versiona o schema. Sem contas/seed de clientes padrão.

PDV-017 acrescenta consultas/edição/status e V5 de auditoria; [contrato e limites](../../docs/workflows/customers.md). Sem exclusão física; alterações e histórico são atômicos e testados contra PostgreSQL real.

PDV-018 acrescenta V6/endereço principal transacional e consulta regional de clientes; [contrato](../../docs/workflows/customers.md). Não é relatório de vendas nem frontend.

PDV-020 acrescenta V7/categorias/produtos JPA, SKU único, Decimal, estados explícitos, versões e histórico atômico; [contrato de catálogo](../../docs/workflows/catalog.md). ADMIN gerencia, OPERATOR consulta; sem saldo de estoque, compras ou frontend. As descrições anteriores de scaffold preservam o contexto histórico; customers e catalog já possuem adaptadores HTTP reais.

PDV-021 acrescenta GET /products e /categories paginados e índices V8, com ensaio HTTP de carga confirmado (10.000 produtos/10 clientes/10s+60s) no Verify. [Spec e limites de medição](../../sdd/features/PDV-021-consulta-catalogo.md); meta é asserção p95<200ms, não alegação automática de produção.

PDV-022 acrescenta CRUD JPA de fornecedores, V9 e vínculo opcional N:1 de produtos. CNPJ único canônico, versão/histórico transacionais, sem exclusão física; ADMIN gerencia/OPERATOR consulta. [Contrato](../../docs/workflows/suppliers.md), com produto.supplierId preservado por edições e inativação sem remoção de vínculos. Sem ordens de compra, estoque ou frontend.

PDV-026 acrescenta V10, saldo inteiro por produto e abertura técnica imutável/atômica, com GET /inventory e leitura de saldo/abertura por produto. [Contrato](../../docs/workflows/inventory.md); disponível calculado no banco, versão própria e guards inventory.read. Movimentações/reservas/ETags permanecem nos incrementos seguintes.

PDV-027 acrescenta V11, entradas/saídas/ajustes inteiros via SQL condicional, lote tudo-ou-nada, motivo/ator e histórico imutável. RequestId/ator e resultado persistido permitem replay sem novo efeito; somente ADMIN/inventory.write movimenta. [Contrato](../../docs/workflows/inventory.md). If-Match/ETags entram na PDV-028, reservas/compras/venda/Saga ficam nas tarefas próprias.

PDV-028 acrescenta ETag forte em GET /inventory/{productId}, PATCH condicional na mesma URI, If-Match/version e conflitos412/409 sem perda de atualização. Revisão ausente428; replay preserva resultado sem inventar estado atual. Sem novo schema; V11 e contratos legados mantidos. [Spec/RFC e verificações](../../sdd/features/PDV-028-concorrencia-etags.md).

PDV-029: [reservas](../../docs/workflows/reservations.md) atômicas/TTL/barreira de pagamento. Run agora exige -ReservationTtlSeconds 900 (demo) ou PDV_RESERVATION_TTL_SECONDS positivo privado; Verify usa900s. Isso é separado do TTL de autenticação. V12, sem checkout/Saga ou worker de expiração presumidos.

PDV-034: [carrinho/itens](../../docs/workflows/carts.md) próprios com JPA, @Version, dinheiro BigDecimal e preço copiado do catálogo, sem descontos/reserva/checkout. V13 mantém trilha imutável e criação idempotente.
