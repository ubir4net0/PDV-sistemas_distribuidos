# Flyway e PostgreSQL — base técnica PDV-009

A [spec](../../sdd/features/PDV-009-flyway-base.md) delimita esta entrega. Flyway 12.4.0 e PostgreSQL JDBC 42.7.14 são gerenciados pelo Spring Boot 4.1.2 existente; runtime e plugin Maven usam a mesma versão. API agora exige PostgreSQL real para iniciar. Worker ainda não usa banco.

## O que foi versionado

[V1](../../backend/api/src/main/resources/db/migration/V1__create_application_schema.sql) cria somente o namespace pdv. Histórico fica em pdv_migrations.flyway_schema_history, separado dos futuros objetos da aplicação. O placeholder applicationSchema é fixado em pdv na configuração da aplicação/plugin e recebe nome UUID apenas nos testes.

Não há tabelas de domínio, seeds, JPA, RBAC, entidades de outbox/inbox ou regras financeiras nesta migration. A [ADR-007 aprovada](../decisions/ADR-007-proposta-base-dominio.md) resolve referência de desenvolvimento, vocabulário de OC, fornecedor N:1 e inclusão dos complementos. Cada tabela e suas restrições pertencem à tarefa correspondente; namespace/histórico não comprovam implementação do domínio.

## Comandos locais

Na raiz PDV, com Docker ativo:

```powershell
docker compose up -d --wait --wait-timeout 180 postgres
./scripts/api.ps1 -Action Verify
./scripts/api.ps1 -Action Migrate
./scripts/api.ps1 -Action Validate
./scripts/api.ps1 -Action Info
./scripts/api.ps1 -Action Run
```

Migrate aplica somente as migrations versionadas ao banco PDV local existente. Validate confere histórico/checksums; Info lista estado e versões. Run executa o JAR já verificado e também migra/valida no startup. Os comandos não fazem clean, repair, baseline automático ou reversão. Schema preexistente sem histórico compatível exige investigação; não acrescentar IF NOT EXISTS, baseline ou repair para esconder divergência.

O script identifica o PostgreSQL do Compose PDV, exige health e uma rede conhecida, lê apenas banco/usuário/senha em memória e passa PDV_DB_URL/USER/PASSWORD por nomes de ambiente ao Docker. Não monta .env no build nem imprime valores. Restaura ambiente anterior no finally.

Verify cria banco exclusivo pdv_verify_<UUID> e o remove ao terminar, mesmo se o Maven falhar. Só remove quando essa execução confirmou a criação e o nome satisfaz o prefixo/UUID restrito. Nunca limpa o banco de trabalho. Interrupção abrupta pode deixar esse banco; identifique a execução e o nome exato antes de limpeza pontual, sem excluir por wildcard.

Com JDK 21 nativo, configurar PDV_DB_URL (JDBC), PDV_DB_USER e PDV_DB_PASSWORD no ambiente privado e executar o Wrapper em backend/api:

```powershell
./mvnw.cmd -B -ntp clean verify
./mvnw.cmd -B -ntp flyway:migrate
./mvnw.cmd -B -ntp flyway:validate
./mvnw.cmd -B -ntp flyway:info
```

O verify nativo aplica o namespace técnico ao banco indicado pelos testes HTTP e usa namespaces UUID nos testes de migration. Use um banco descartável próprio; não aponte para produção. Execução nativa Windows/JDK 21 não foi validada nesta entrega. Usuário administrativo do Compose é somente bootstrap local, não desenho de privilégios de produção.

## Recuperação e testes

Flyway tem clean-disabled=true, baseline-on-migrate=false, out-of-order=false e validação de nomes/checksums ativa, tanto na aplicação quanto no plugin. Não editar V1 já aplicada; novas mudanças aprovadas exigem V2, V3 etc. sequenciais após sincronizar main e inspecionar migrations existentes. Não usar atualização automática de ORM como evolução do banco.

MigrationsIT usa PostgreSQL real com namespaces pdv_it_<UUID> e pdv_it_<UUID>_history. Testa replay, checksum alterado, clean proibido, rollback de DDL com falha e dois migradores concorrentes aplicando o incremento uma vez. O ciclo de recuperação é migrate -> remoção controlada dos dois namespaces próprios -> migrate, comparando versão/checksum e presença do schema. Nunca executa Flyway.clean nem remove objetos compartilhados.

Fixtures V9000 válidas/com erro estão somente em src/test/resources/db/test, com versão reservada para não colidir com migrations reais. Não entram no JAR. V2 adiciona [identidade](identity-security.md), com constraints e seed development opt-in; testes HTTP verificam histórico real, search_path pdv e ausência de conta padrão. Health/readiness consultam banco sem detalhes; liveness continua independente dele. /actuator/flyway não é exposto.

Esse rebuild comprova recuperação da base estrutural vazia. Não comprova backup/restore de dados, RPO/RTO, estoque, concorrência de negócio, privilégios finais ou RNF19/20. Essas evidências pertencem às entregas correspondentes; não há undo automático presumido.

## CI

[Backend quality](../../.github/workflows/backend.yml) inicia PostgreSQL 16.15 temporário somente no job API, com senha aleatória mascarada e imagem fixada por digest. Aguarda health, executa clean verify real e remove seu container. Worker mantém RabbitMQ próprio. Falha de infraestrutura/teste falha o job; não há H2 ou integração ignorada.

Fontes oficiais consultadas: [inicialização e Flyway no Spring Boot](https://docs.spring.io/spring-boot/how-to/data-initialization.html), [endpoints e grupos de health](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html) e [suporte PostgreSQL do Flyway](https://github.com/flyway/flyway/blob/main/documentation/Reference/Database%20Driver%20Reference/PostgreSQL%20Database.md).
