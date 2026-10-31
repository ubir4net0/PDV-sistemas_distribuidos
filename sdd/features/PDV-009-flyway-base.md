# PDV-009 — Flyway e base de migrações

## Estado e fonte

Tarefa viva: https://app.notion.com/p/4092c9a9991781c2b27df2e15e66b32e. Fonte: especificação v1.0, seção 12, Persistência. Aceite original: “Migrations ordenadas, reversão testável por restauração/rebuild e testes contra PostgreSQL real.”

## Objetivo, DoR e dependências

Implementar a base técnica de Flyway/PostgreSQL. PDV-004/005 fornecem infraestrutura e API; evidências técnicas não substituem seu aceite formal. Na abertura, DEC-02/03/04 impediam tabelas/enums/cardinalidades de negócio; a base foi implementada sem contornar esse bloqueio. Depois do incremento, o proprietário aprovou explicitamente a ADR-007, conforme atualização abaixo. Responsável/prazo não confirmados.

## Fronteiras e decisão técnica

API passa a usar JDBC/PostgreSQL e Flyway no startup. Histórico isolado em pdv_migrations e namespace de aplicação pdv criado por V1; nenhum cliente, produto, usuário, permissão, venda ou tabela de confiabilidade é modelado. Uma migration versionada de namespace é a fundação, não o schema de negócio concluído. Worker permanece sem persistência. Rotas e envelopes públicos são preservados; health agregado/readiness agora dependem do banco, liveness continua do processo. Autenticação, JPA e APIs de negócio não foram implementadas.

A [ADR-007](../../docs/decisions/ADR-007-proposta-base-dominio.md) foi aprovada explicitamente pelo proprietário. Referência textual, estados de OC, fornecedor N:1 e inclusão dos complementos foram decididos; tabelas serão criadas nas tarefas próprias. Nenhuma tabela adicional ou status de outra tarefa foi alterado por essa aprovação.

Flyway valida nomes/checksums, proíbe clean e baseline automático/out-of-order. Maven plugin e runtime usam versão gerenciada pelo Spring Boot existente. Credenciais exclusivamente por ambiente; scripts leem somente os campos necessários do PostgreSQL local sem imprimir valores. Verify usa banco novo com nome UUID, criado e removido pela própria execução; Run/Migrate usam o banco local existente sem limpeza. Nenhuma operação no Amor e Vida.

## Aceite técnico verificável

- Startup e comando de migration operam contra PostgreSQL real, com V1 registrada e replay sem reaplicar.
- Recuperação por rebuild restrito a namespaces de teste próprios, seguida de replay; não prometer backup/restore de dados de negócio inexistentes.
- Integração verifica mudança de checksum, rollback transacional de DDL com erro, bloqueio de clean e execução concorrente de migration.
- Fixtures adicionais existem somente em src/test/resources, fora do JAR de produção.
- CI da API inicia PostgreSQL temporário e exige os testes reais, sem fallback H2 ou skips.
- Documentação e evidências distinguem base técnica, decisões aprovadas e implementação de domínio futura. Não declarar tabelas ou outras tarefas concluídas por este incremento.

## Plano de verificação

Iniciar/validar PostgreSQL PDV real; scripts/api.ps1 -Action Verify, Migrate, Validate e Info; testes de integração HTTP e migrations no ciclo migrate -> rebuild controlado -> migrate. Conferir broker/worker e CI existentes, integridade das fontes, links, whitespace e ausência de segredos. Smoke do JAR com health sem detalhes e histórico real. Commit/push direto main e CI do SHA.

## Evidências e revisão

Verificações desta sessão (30/10/2026 no fuso local; logs UTC de 31/10):

| Comando/verificação | Resultado |
| --- | --- |
| PostgreSQL Compose PDV | Serviço real 16.15 saudável; serviços Amor e Vida preservados |
| scripts/api.ps1 -Action Verify, build final | BUILD SUCCESS; Java 21.0.12.1/Maven 3.10.0; zero lint; 11 HTTP/startup + 6 migrations IT, zero falhas/erros/skips |
| Ciclo PostgreSQL real | migrate -> rebuild de namespaces UUID próprios -> migrate passou, versão/checksum/schema recuperados |
| Casos negativos/concorrência | Mudança de checksum rejeitada, clean bloqueado, DDL com erro revertido e dois migradores aplicando V2 de teste somente uma vez |
| scripts/api.ps1 -Action Migrate/Validate/Info | Três comandos SUCCESS no banco local existente; V1 aplicada e validada; Info confirma versão 1 |
| Restauração do ambiente pelo helper | Valores anteriores de PDV_DB_URL/USER/PASSWORD restaurados após Info |
| Banco descartável | Verify criou/removeu banco exclusivo; consulta final confirmou zero pdv_verify_UUID remanescentes |
| JAR empacotado | Somente V1 de produção; nenhuma fixture SQL de teste |
| Smoke do JAR | Health/readiness/liveness UP sem detalhes; /actuator/flyway 403; 8081 publicado em loopback; container próprio encerrado |
| Integridade do planejamento | 60 IDs/aceites/dependências, DAG, 19 RF/20 RNF e três hashes de fontes preservados |
| Parser/estrutura | PowerShell e POM XML válidos; whitespace sem erros |
| Documentação e staging | 75 links locais válidos; 23 arquivos do incremento; sem senhas locais, .env ou artefatos de build no staged |

Build foi repetido após incluir PostgreSQL no grupo readiness e ajustar o helper final; resultado final aprovado. Warnings de metadados de repositórios Flyway com HTTP 401 e de repositório legado/certificado não impediram resolução pública/build; TLS não foi relaxado e nenhuma credencial foi adicionada. Java nativo Windows não foi testado. Worker não foi alterado; sua regressão será conferida no job remoto.

No primeiro incremento, a autorevisão incluiu isolamento/cleanup, ausência de segredos e fixtures fora do JAR. Commit aecb625d059033e2b8b73175c1382d5ec59d2b36 foi publicado; Backend quality 41158892144 e Planning integrity 41158892078 passaram, com 34 testes totais na CI. A ADR-007 ainda era proposta naquele momento.

## Aprovação e fechamento do bloqueio

Em 30/10/2026 (Manaus; 31/10 UTC), o proprietário respondeu “pode aprovar tudo” à pergunta explícita da ADR-007. A decisão abrange a baseline como referência de desenvolvimento, os estados CRIADA/ENVIADA/RECEBIDA/CANCELADA, fornecedor N:1 e complementos incrementais com nomes SQL em inglês. Os limites da ADR permanecem válidos.

O critério da PDV-009 é atendido pela fundação Flyway, V1 ordenada e ciclo/testes PostgreSQL reais já executados. Schema de cadastros, identidade e confiabilidade será implementado nas respectivas tarefas previstas na ADR; não criar tabelas vazias em massa. Com as decisões registradas e a base técnica autorevisada, a entrega fica Em validação para aceite da tarefa. A resposta aprovou a ADR, sem aceitar automaticamente esta ou outras tarefas.

Esta atualização altera somente documentação/decisão: não há nova migration nem repetição de build local. Verificações desta etapa: planejamento PASS para os 60 IDs/aceites/dependências e três hashes; 107 links locais válidos em 13 documentos; whitespace sem erros; fontes originais preservadas e ausência de senhas locais no staged. CI do novo SHA será vinculada à tarefa viva após confirmação.
