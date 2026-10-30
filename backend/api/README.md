# API PDV — Java 21 / Spring Boot

Scaffold técnico da [PDV-005](../../sdd/features/PDV-005-api-scaffold.md), com Spring Boot 4.1.2, Maven 3.10.0 e Wrapper oficial 3.3.4. Somente health/probes estão disponíveis. Ainda não existem login, RBAC, cadastros, estoque, vendas, datasource, migrations ou comunicação com o worker.

## Build e execução no Windows

Com Docker ativo, executar na raiz do repositório:

```powershell
./scripts/api.ps1 -Action Version
./scripts/api.ps1 -Action Verify
./scripts/api.ps1 -Action Run
```

A toolchain oficial Java 21 é fixada por digest em [infra/java-toolchain.json](../../infra/java-toolchain.json). A versão Maven executada é controlada pelo Wrapper, independentemente do Maven pré-instalado na imagem. O cache fica no volume local `pdv-api-maven-cache`; classes, relatórios e JAR ficam em `backend/api/target`, ignorado pelo Git. O primeiro build requer acesso aos repositórios oficiais. Nenhum Java global ou serviço do Amor e Vida é alterado.

`Verify` executa `clean verify`, incluindo testes HTTP reais em porta aleatória. `Run` exige o JAR já compilado, usa a porta 127.0.0.1:8081 e fica em primeiro plano no container `pdv-api-local`; Ctrl+C ou `docker stop pdv-api-local` encerra esse container. Um container já existente com esse nome não é sobrescrito. O perfil Docker permite escuta interna em 0.0.0.0, com publicação do host restrita a loopback.

Com **JDK 21** instalado e selecionado localmente, também é possível executar diretamente em backend/api:

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
| GET /actuator/health/readiness | 200 após inicialização, estado de readiness do processo |
| Outros caminhos | 403, sem login redirecionado, HTTP Basic ou sessão |
| POST /actuator/health | 403; endpoint não oferece mutações |

Health não consulta PostgreSQL ou RabbitMQ neste scaffold. Não interpretar UP como fluxo de venda pronto. Apenas health é exposto; componentes, detalhes e descoberta de endpoints são ocultos. Nenhum usuário/senha em memória é gerado. A autenticação real será implementada em PDV-012/013; a configuração atual bloqueia os demais caminhos.

Nenhuma credencial é necessária para compilar/iniciar. CSRF está desabilitado somente neste scaffold sem autenticação por cookie e sem mutações permitidas, evitando criar sessão para requisições bloqueadas. PDV-012/013 devem revisar essa política conforme o contrato de autenticação; isso não define a política de segurança do produto final. JPA, Flyway e AMQP serão adicionados junto às integrações verificáveis, sem configuração fake. Para iniciar dependências locais separadamente, consultar [infra/README.md](../../infra/README.md).

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

Os testes `ScaffoldHttpTests` iniciam servidor real e verificam health/probes, ocultação de detalhes, bloqueio de caminhos/métodos e ausência de contas padrão. O smoke do JAR empacotado é registrado na spec. Não são testes de estoque, banco, filas ou Saga. CI de produto pertence à PDV-008; o workflow atual valida planejamento.

Bootstrap obtido do [Spring Initializr](https://start.spring.io), compatibilidade conferida nos [requisitos Spring Boot](https://docs.spring.io/spring-boot/system-requirements.html). Ver também [Maven Wrapper](https://maven.apache.org/tools/wrapper/), [Actuator](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html) e [auto-configuração de contas em memória](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/security/autoconfigure/UserDetailsServiceAutoConfiguration.html). Versões selecionadas estão fixadas no POM/Wrapper/toolchain; não atualizar silenciosamente.
