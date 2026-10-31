# API PDV — Java 21 / Spring Boot

API técnica iniciada na [PDV-005](../../sdd/features/PDV-005-api-scaffold.md), com Spring Boot 4.1.2, Maven 3.10.0 e Wrapper oficial 3.3.4. A [base Flyway da PDV-009](../../docs/workflows/database-migrations.md) conecta PostgreSQL e cria namespace/histórico, sem tabelas de domínio. Somente health/probes estão disponíveis; login, RBAC, cadastros, estoque, vendas, JPA e comunicação com o worker continuam ausentes.

## Build e execução no Windows

Com Docker ativo e PostgreSQL PDV saudável, executar na raiz do repositório:

```powershell
./scripts/api.ps1 -Action Version
./scripts/api.ps1 -Action Verify
./scripts/api.ps1 -Action Migrate
./scripts/api.ps1 -Action Validate
./scripts/api.ps1 -Action Info
./scripts/api.ps1 -Action Run
```

A toolchain oficial Java 21 é fixada por digest em [infra/java-toolchain.json](../../infra/java-toolchain.json). A versão Maven executada é controlada pelo Wrapper, independentemente do Maven pré-instalado na imagem. O cache fica no volume local `pdv-api-maven-cache`; classes, relatórios e JAR ficam em `backend/api/target`, ignorado pelo Git. O primeiro build requer acesso aos repositórios oficiais. Nenhum Java global ou serviço do Amor e Vida é alterado.

`Verify` executa `clean verify`, incluindo onze testes HTTP/startup e seis testes de migrations contra PostgreSQL real. Usa banco UUID descartável e remove somente esse banco ao terminar. `Migrate/Validate/Info` operam o banco PDV local existente sem limpeza. `Run` exige o JAR compilado, migra/valida no startup, usa 127.0.0.1:8081 e fica em primeiro plano no container `pdv-api-local`; Ctrl+C ou `docker stop pdv-api-local` encerra esse container. Nome existente não é sobrescrito. Escuta interna Docker em 0.0.0.0, publicação do host restrita a loopback.

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
| Outros caminhos | 403, sem login redirecionado, HTTP Basic ou sessão |
| POST /actuator/health | 403; endpoint não oferece mutações |

Health agregado e readiness consultam PostgreSQL; liveness continua independente do banco. RabbitMQ não é consultado pela API. UP não significa fluxo de venda pronto. Somente health é exposto; componentes, detalhes, descoberta e /actuator/flyway permanecem ocultos/bloqueados. Nenhum usuário/senha em memória é gerado. A autenticação real pertence à PDV-012/013; outros caminhos continuam bloqueados.

Startup, migrations e testes exigem credenciais PostgreSQL por ambiente. O script as lê do Compose local sem imprimir valores; o usuário administrativo é bootstrap de laboratório, não uma política de privilégios de produção. CSRF permanece desabilitado neste scaffold sem autenticação por cookie e sem mutações permitidas; PDV-012/013 devem revisar a política. JPA e AMQP pela API seguem pendentes. Para iniciar dependências, consultar [infra/README.md](../../infra/README.md).

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

Cada módulo reserva três camadas por `package-info.java`: `domain` para regras sem framework, `application` para casos de uso/portas e `infrastructure` para adaptadores. HTTP entrará em `infrastructure.web` do respectivo módulo. Os arquivos documentam fronteiras; não implementam entidades, serviços, controllers ou tabelas. Contratos de negócio, estados e cardinalidades continuam pendentes.

## Verificações e fontes

Os onze testes `ScaffoldHttpIT` iniciam servidor real e verificam health/probes, ocultação de detalhes, bloqueios, ausência de contas padrão e Flyway/JDBC reais. Seis `MigrationsIT` verificam replay, rebuild, checksum, rollback de DDL, clean bloqueado e migradores concorrentes. Failsafe executa ambos em `verify`; Checkstyle inclui fontes e testes. Não há unitários isolados nesta API sem casos de uso. Não são testes de estoque, filas, Saga ou concorrência de negócio. [CI e gates](../../docs/workflows/ci.md) executam o build completo com PostgreSQL próprio.

Bootstrap obtido do [Spring Initializr](https://start.spring.io), compatibilidade conferida nos [requisitos Spring Boot](https://docs.spring.io/spring-boot/system-requirements.html). Ver também [Maven Wrapper](https://maven.apache.org/tools/wrapper/), [Actuator](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html) e [auto-configuração de contas em memória](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/security/autoconfigure/UserDetailsServiceAutoConfiguration.html). Versões selecionadas estão fixadas no POM/Wrapper/toolchain; não atualizar silenciosamente.
