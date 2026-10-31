# CI e qualidade — PDV-008

[Backend quality](../../.github/workflows/backend.yml) roda em pushes de main e por workflow_dispatch. Matriz independente para API e worker; permissões contents:read, checkout sem credencial persistida, actions fixadas por SHA, Temurin 21 e Maven Wrapper com checksum. Não faz deploy e não exige PR.

Cada job executa `./mvnw -B -ntp clean verify` no respectivo módulo:

| Gate | Evidência |
| --- | --- |
| Checkstyle 14.3.0 / plugin 3.6.0 | Fontes e testes Java; imports não usados/curinga, chaves, statements vazios, tabs e newline final |
| Enforcer / javac release 21 | Toolchain e verificação de tipos Java |
| Surefire 3.5.6 | Classes Test/Tests; três cenários de autenticação na API e seis do publisher no worker |
| JAR Spring Boot | Artefato empacotado de cada processo |
| Failsafe 3.5.6 | Classes IT; HTTP/startup/autenticação, migrations e identidade PostgreSQL reais na API; onze AMQP no worker |

A API inclui unitários de BCrypt/limite UTF-8/codec e integração HTTP de autenticação, identidade e migrations em PostgreSQL real. Failsafe falha se não encontrar testes em qualquer módulo. `mvn test` não executa integração; usar `verify` para o gate completo. Relatórios locais ficam em target/surefire-reports e target/failsafe-reports, ignorados pelo Git.

O job worker inicia um RabbitMQ 4.3.7 fixado por digest, aguarda health e configura usuário/vhost próprios e senha aleatória mascarada. A senha passa por ambiente efêmero do runner; não usa segredo do desenvolvedor nem credencial GitHub. Somente AMQP é publicado em loopback. Ao final, remove o container que criou. Falha de infraestrutura ou teste falha o job; não há caminho que ignore a integração. Objetos de teste têm prefixo UUID e limpeza restrita.

O job API inicia PostgreSQL 16.15 temporário, com imagem por digest, senha aleatória mascarada e health obrigatório, removendo-o ao final. A base Flyway da PDV-009 é testada contra banco real; falhas não são ignoradas. Fixtures de testes não entram no JAR.

Para Windows/Docker, usar `scripts/api.ps1 -Action Verify` (PostgreSQL do Compose saudável) e `scripts/worker.ps1 -Action Verify` (RabbitMQ saudável). O verify da API cria/remove banco UUID exclusivo; não reseta o banco de trabalho. Os scripts montam seu módulo de código e config/ (esta em leitura), mantendo .env da raiz fora do build. Com JDK 21 nativo, configurar variáveis privadas conforme os READMEs.

Frontend/TypeScript não foi criado: lint e typecheck serão adicionados com o código. A integração PostgreSQL cobre namespace/histórico técnico, rebuild/replay, checksum, DDL transacional e lock de migration. Não comprova schema/invariantes de domínio, backup/restore de dados, pagamentos, Saga, outbox/inbox, disponibilidade RNF19 ou segurança do produto final.

[Planning integrity](../../.github/workflows/planning.yml) continua verificando fontes e rastreabilidade separadamente. [Spec e evidências](../../sdd/features/PDV-008-ci-qualidade.md).

Referências primárias usadas para configuração: [Maven Checkstyle](https://maven.apache.org/plugins/maven-checkstyle-plugin/examples/custom-checker-config.html), [Checkstyle e JDK mínimo](https://checkstyle.org/), [Failsafe](https://maven.apache.org/surefire/maven-failsafe-plugin/), [setup-java](https://github.com/actions/setup-java) e [Java/Maven no GitHub Actions](https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-maven).

O job API e Verify definem TTL de 900 segundos apenas para testes; Run exige configuração operacional explícita.
