# PDV-006 scaffold do worker Java

## Estado e fonte

Tarefa viva: https://app.notion.com/p/4092c9a9991781408cc3ca25408403fb. Leitura em 30/10/2026. Fonte: especificação v1.0, seções 2 e 12, RNF06. Aceite: “Processo independente consome/publica mensagens com contrato tipado e tratamento de erros.”

## Contexto, dependências e Definition of Ready

PDV-004 fornece RabbitMQ real saudável; PDV-005 fornece toolchain Java 21/Spring Boot 4.1.2/Maven Wrapper verificada. Aceites formais das predecessoras e baseline permanecem pendentes. Este incremento técnico usa os artefatos entregues sem aprovar estados, cardinalidades ou contratos de negócio. Não atribuir responsável ou prazo sem confirmação.

## Fronteiras

Inclui processo Spring Boot sem servidor HTTP, build próprio, AMQP real, contrato técnico JSON de diagnóstico, consumo/publicação tipados, confirmação/roteamento da resposta, rejeição sem requeue infinito e DLQ. Incluir testes contra o broker existente em namespace exclusivo de teste e script de execução local com credenciais mantidas fora do Git/logs.

Exclui pagamentos, vendas, Saga, estoque, API produtora, contratos de negócio PDV-007, persistência, migrations e confiabilidade completa de PDV-038. O diagnóstico é identificado como scaffold técnico, não funcionalidade de pagamento fake. Não modificar API, dados ou serviços do Amor e Vida.

## Contrato técnico proposto v1

Requisição JSON: type=pdv.probe.request.v1, requestId UUID obrigatório, text com até 256 unidades UTF-16. Resposta JSON: type=pdv.probe.response.v1, mesmos requestId/text. Tamanho máximo de entrada: 4096 bytes; content-type application/json. Esses limites são proteções técnicas locais documentadas, sem relação com TTL/preço/regras de negócio.

Prefixo padrão pdv.scaffold.probe, configurável: exchange direto, filas requests/responses/dead e exchange de erros. Nenhuma fila de negócio existente será usada. JSON será decodificado para classe fixa, sem resolver classes por headers fornecidos pelo produtor.

## Fluxo e erros

Consumir requisição, validar contrato, construir resposta tipada e publicar com mandatory/confirm. Ack de entrada somente após retorno normal do handler; erro de payload, versão, rota ou publicação rejeita a entrada para DLQ, sem requeue infinito. Erros devem ser registrados sem corpo/credenciais. Janela entre publicação e ack ainda permite duplicação; sem inbox/outbox ou exactly-once. Timeout de confirmação pode deixar resultado incerto, exigindo inspeção antes de replay; não declarar perda impossível em falha do broker.

## Aceite e plano de validação

1. Wrapper/Java 21 compilam e empacotam worker independente, sem dependência do módulo API e sem porta HTTP.
2. Testes reais AMQP comprovam resposta tipada, correlação e tratamento seguro de headers externos.
3. Payload inválido, versão incompatível, UUID ausente, tamanho/content-type incorretos e publicação sem rota vão à DLQ, sem resposta de sucesso ou logging do corpo.
4. JAR separado inicia, consome/publica no broker real e encerra sem tocar a API; objetos exclusivos do smoke são limpos.
5. Credenciais são lidas do broker local autorizado e passadas ao Docker por ambiente de processo, sem valores na linha de comando ou arquivos novos. Preservar fontes/backlog; revisar links, scripts e staged diff.

Não há migration/persistência alterada; nenhum ciclo Flyway aplicável. PostgreSQL permanece ativo e preservado. Testes não usam driver fake nem dependem de Docker dentro do container.

## Evidências executadas

Pre-flight: main limpa/sincronizada por fast-forward; PostgreSQL/RabbitMQ healthy. Predecessoras lidas ao vivo.

- `scripts/worker.ps1 -Action Version`: Java/Javac 21.0.12.1, Maven 3.10.0, Wrapper oficial 3.3.4 com checksums, toolchain já fixada por digest. Wrapper próprio copiado da baseline verificada; nenhuma dependência de código do módulo API.
- `scripts/worker.ps1 -Action Verify` final: clean verify SUCCESS, Enforcer e compilação release 21, 11 testes AMQP reais, zero falhas/erros/skips, empacotamento/repackage. Casos: resposta tipada/correlação, header de classe ignorado, nove variantes inválidas para DLQ e publicação de resposta sem rota.
- Conversor do listener entrega somente bytes; não desserializa objetos Java por MIME/header. JSON é lido para classe fixa. Testes comprovaram rejeição de application/x-java-serialized-object e ausência do marcador sintético do corpo nos logs de erro.
- JAR iniciado por `worker.ps1 -Action Run -ProbePrefix pdv.smoke.<uuid>`, em container independente, sem portas publicadas. Produtor de teste via HTTP management publicou requisição; o worker consumiu/publicou por AMQP e a resposta retornou com tipo/requestId/correlationId corretos. JSON inválido chegou à DLQ. A API da aplicação não foi iniciada para esse fluxo.
- Container de smoke encerrado; suas três filas e dois exchanges removidos por nome exato. Consulta posterior não encontrou filas ou exchanges dos namespaces de teste/smoke. Testes de integração também removeram sua topologia própria; nenhum reset/purge amplo do broker.
- PostgreSQL/RabbitMQ permaneceram healthy. Seis containers Amor e Vida continuaram em execução com horários de início anteriores à tarefa; sem alteração de banco, volumes ou serviços externos.
- Planejamento PASS: 60 IDs/títulos/aceites/dependências, grafo acíclico, 19 RF + 20 RNF e hashes das três fontes. Parser PowerShell, staged diff, 31 links locais e inspeção das duas senhas locais no diff passaram; .env, target e arquivos temporários ficaram fora do índice.

Limites: não foi executado build nativo Windows com JDK 21. Não há API produtora, efeitos de negócio, persistência ou ciclo Flyway aplicável. Confirmação/roteamento e DLQ no cenário saudável não provam exatamente uma entrega, recuperação durável de falhas ou RNF06 completo. CI de produto permanece na PDV-008. Commit e CI de planejamento serão vinculados no Notion após publicação.

## Revisão e aceite

Commit direto em main com ID PDV-006, sem PR; autorevisão e evidências na tarefa corrente. Não concluir RNF06 de ponta a ponta API→worker, PDV-007/038 ou predecessoras por este diagnóstico. Aceite formal do proprietário separado da verificação técnica.
