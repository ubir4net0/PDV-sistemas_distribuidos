# CI e qualidade — PDV-008

[Backend quality](../../.github/workflows/backend.yml) roda em pushes de main e por workflow_dispatch. Matriz independente para API e worker; permissões contents:read, checkout sem credencial persistida, actions fixadas por SHA, Temurin 21 e Maven Wrapper com checksum. Não faz deploy e não exige PR.

Cada job executa `./mvnw -B -ntp clean verify` no respectivo módulo:

| Gate | Evidência |
| --- | --- |
| Checkstyle 14.3.0 / plugin 3.6.0 | Fontes e testes Java; imports não usados/curinga, chaves, statements vazios, tabs e newline final |
| Enforcer / javac release 21 | Toolchain e verificação de tipos Java |
| Surefire 3.5.6 | Classes Test/Tests; seis cenários unitários do publisher no worker |
| JAR Spring Boot | Artefato empacotado de cada processo |
| Failsafe 3.5.6 | Classes IT; dez testes HTTP reais e onze AMQP contra broker real |

A API ainda não tem lógica isolada que justifique unitários; os dez testes HTTP são integração. Failsafe falha se não encontrar testes em qualquer módulo. `mvn test` não executa integração; usar `verify` para o gate completo. Relatórios locais ficam em target/surefire-reports e target/failsafe-reports, ignorados pelo Git.

O job worker inicia um RabbitMQ 4.3.7 fixado por digest, aguarda health e configura usuário/vhost próprios e senha aleatória mascarada. A senha passa por ambiente efêmero do runner; não usa segredo do desenvolvedor nem credencial GitHub. Somente AMQP é publicado em loopback. Ao final, remove o container que criou. Falha de infraestrutura ou teste falha o job; não há caminho que ignore a integração. Objetos de teste têm prefixo UUID e limpeza restrita.

Para Windows/Docker, usar `scripts/api.ps1 -Action Verify` e `scripts/worker.ps1 -Action Verify` (este requer o RabbitMQ do Compose saudável). Os scripts montam apenas seu módulo e config/ em leitura: o .env da raiz não entra no build. Com JDK 21 nativo, executar o Wrapper no diretório do módulo; no worker, configurar as variáveis locais conforme seu README.

Frontend/TypeScript não foi criado: lint e typecheck frontend serão adicionados quando houver código. Não há datasource nas aplicações: CI não comprova integração PostgreSQL, migrations ou invariantes transacionais. A futura tarefa de persistência deve incluir esses gates. Estes testes não comprovam pagamentos, Saga, outbox/inbox, disponibilidade RNF19 ou segurança do produto final.

[Planning integrity](../../.github/workflows/planning.yml) continua verificando fontes e rastreabilidade separadamente. [Spec e evidências](../../sdd/features/PDV-008-ci-qualidade.md).

Referências primárias usadas para configuração: [Maven Checkstyle](https://maven.apache.org/plugins/maven-checkstyle-plugin/examples/custom-checker-config.html), [Checkstyle e JDK mínimo](https://checkstyle.org/), [Failsafe](https://maven.apache.org/surefire/maven-failsafe-plugin/), [setup-java](https://github.com/actions/setup-java) e [Java/Maven no GitHub Actions](https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-maven).
