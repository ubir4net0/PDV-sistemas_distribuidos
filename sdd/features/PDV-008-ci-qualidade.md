# PDV-008 — CI e qualidade

Tarefa viva: https://app.notion.com/p/4092c9a9991781bb9249d14f71a1c1b5

## Objetivo e dependências

Aceite original: pipeline com build, unitários, lint/typecheck e teste de integração quando infraestrutura disponível. RNF19 é referência de rastreabilidade; esta entrega não comprova disponibilidade ou deploy sem interrupção.

PDV-005 e PDV-006 fornecem os scaffolds técnicos disponíveis em main; ambas permanecem Em validação no Notion. Esta tarefa exercita esses incrementos sem aceitar requisitos de negócio ou encerrar predecessoras. PDV-007 permanece bloqueada por decisões de domínio. Trabalho individual em main, sem PR, conforme ADR-001; responsável e prazo não serão inventados.

## Escopo e aceite verificável

- GitHub Actions executa API e worker em Java 21, com versões fixadas, Wrapper e permissões somente de leitura.
- Maven clean verify aplica Checkstyle compartilhado a fontes e testes, compilação Java, testes unitários, empacotamento e testes de integração separados por Failsafe.
- Unitários cobrem falhas de confirmação/roteamento do publisher; integração preserva os dez cenários HTTP e onze cenários AMQP reais existentes.
- CI do worker cria RabbitMQ temporário com credenciais aleatórias mascaradas, aguarda health e falha se o broker ou os testes falharem; cleanup restrito ao container criado.
- Scripts locais usam a mesma configuração de qualidade sem montar .env no container de build. Documentação registra comandos, evidências e limites.

Não há frontend implementado: lint/typecheck TypeScript não se aplica. Compilação javac --release 21 é a verificação de tipos Java. Não há persistência nas aplicações: PostgreSQL/migrations, regras de negócio, API produtora, pagamentos e deploy ficam fora do escopo. Nenhuma alteração de contrato HTTP/AMQP prevista.

## Validação planejada

Executar scripts/api.ps1 -Action Verify e scripts/worker.ps1 -Action Verify com RabbitMQ real local; verificar integridade do planejamento e diff; conferir ausência de segredos. Publicar commit normal em main e verificar os jobs reais do GitHub Actions. Registrar resultados após execução; task aguarda aceite do proprietário ao final.

## Evidências da execução

Execução em 30/10/2026, Docker/JDK 21.0.12.1 e Maven Wrapper/Maven 3.10.0, sem alterar Java global:

| Verificação | Resultado desta sessão |
| --- | --- |
| scripts/api.ps1 -Action Verify | BUILD SUCCESS; zero violações Checkstyle; dez HTTP IT, zero falhas/erros/skips; JAR gerado |
| scripts/worker.ps1 -Action Verify | BUILD SUCCESS; zero violações Checkstyle; seis unitários e onze AMQP IT com RabbitMQ real, zero falhas/erros/skips; JAR gerado |
| Gate negativo de lint | Cópia descartável do módulo API em container: import java.util.* deliberado rejeitado por AvoidStarImport; fontes do checkout preservadas |
| node scripts/verify-planning.mjs | PASS: 60 IDs/aceites/dependências, DAG, 19 RF/20 RNF e três hashes de fontes preservados |
| Links locais de documentação | 25 destinos existentes |
| git diff --check | Sem erros de whitespace |

A primeira execução do worker encontrou timeout de dois segundos na inicialização do teste de publicação; limite geral removido e limite de dez segundos aplicado somente ao teste de confirmação ausente (publisher configurado para espera de 50 ms). O build completo repetido passou. Maven emitiu warning de metadados de repositório legado com certificado indisponível e Mockito avisou sobre autoanexação de agente; não houve relaxamento de TLS nem falha nos builds Java 21.

Autorevisão inclui limites do escopo, ausência de contratos alterados, permissões/credenciais efêmeras da CI, configuração compartilhada montada em leitura e separação Test/IT. Commit será publicado diretamente em main. Execuções remotas e vínculo do commit serão registrados na tarefa viva após confirmação do GitHub Actions. Aceite final do proprietário permanece pendente; não encerrar predecessoras.
