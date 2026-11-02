# Worker PDV — Java 21 / Spring Boot

Scaffold independente da [PDV-006](../../sdd/features/PDV-006-worker-scaffold.md), alinhado à toolchain da API: Spring Boot 4.1.2, JDK 21, Maven 3.10.0 e Wrapper oficial 3.3.4 com checksums. Não depende do módulo API e não abre servidor HTTP.

## Executar e verificar

Na raiz do repositório, com Docker ativo:

```powershell
./scripts/init-local-env.ps1
docker compose up -d --wait --wait-timeout 180
./scripts/worker.ps1 -Action Version
./scripts/worker.ps1 -Action Verify
./scripts/worker.ps1 -Action Run
```

Verify executa clean verify e testes PostgreSQL/AMQP reais, com ambos os serviços PDV saudáveis. A ausência de broker saudável falha a verificação; não há fallback para driver fake nem teste de integração ignorado. O teste usa prefixo UUID próprio e remove apenas seus objetos. Cache Maven: volume pdv-worker-maven-cache; builds em backend/worker/target, ignorado.

Run inicia o JAR no container pdv-worker-local, em primeiro plano, na mesma rede do broker. Ctrl+C ou docker stop pdv-worker-local encerra esse processo, preservando broker, API e filas. Nenhuma porta é publicada. O nome de container existente não é sobrescrito. O parâmetro -ProbePrefix permite namespace próprio pdv.smoke.<id> para ensaio controlado, sem mudar filas do diagnóstico padrão.

O script captura somente usuário/senha/vhost do broker local selecionado, sem imprimir valores, e os passa ao Docker por ambiente de processo. Valores anteriores são restaurados no finally; nenhuma credencial é adicionada à linha de comando, Git, Notion ou novo arquivo. O JDK global do Windows não é modificado. Alternativa nativa exige JDK 21, Wrapper e variáveis PDV_RABBITMQ_HOST/PORT/USER/PASSWORD/VHOST configuradas localmente; não foi validada nesta entrega.

## Contrato técnico de diagnóstico v1

Este fluxo exercita transporte e erros, sem implementar pagamentos ou eventos de negócio da PDV-007.

```json
{"type":"pdv.probe.request.v1","requestId":"3bf83034-92d2-46fc-9111-7694e70e606f","text":"diagnostic"}
```

Resposta: type=pdv.probe.response.v1, mesmos requestId/text e correlationId AMQP igual ao requestId. JSON UTF-8, content-type application/json, entrada até 4096 bytes e texto até 256 unidades UTF-16. UUID e type são obrigatórios; campos desconhecidos, texto nulo ou versão incompatível são rejeitados. Estes são limites técnicos do scaffold, sem relação com regras financeiras, TTL de reserva ou políticas de negócio.

O consumidor lê JSON para ProbeRequest fixo. Headers externos como __TypeId__ não escolhem classes Java. A resposta é construída como ProbeResponse e serializada explicitamente, sem serialização Java de objetos.

## Topologia local

Prefixo padrão pdv.scaffold.probe, restrito aos namespaces pdv.scaffold.*, pdv.test.* e pdv.smoke.*:

| Objeto | Uso |
| --- | --- |
| <prefix>.exchange | Exchange direto; routing keys request e response |
| <prefix>.requests | Entrada, um consumidor/prefetch 1, ack após handler |
| <prefix>.responses | Respostas de diagnóstico |
| <prefix>.errors | Exchange direto de erro, routing key rejected |
| <prefix>.dead | DLQ para mensagens rejeitadas |

Objetos duráveis, exclusivos deste diagnóstico por nome. Nenhuma fila de venda/pagamento é declarada. Não purgar filas compartilhadas nem apagar volumes para ajustar configuração. Mudança de argumentos de filas já existentes exige procedimento próprio.

## Publicação, erros e limites

Publicação da resposta usa mandatory, publisher confirms correlacionados e verificação de returns. Confirm não prova consumo da resposta. O handler só retorna normalmente após publicação confirmada e roteada; payload inválido, versão, rota ou publicação com erro rejeitam a entrada sem requeue infinito, encaminhando-a à DLQ no cenário local saudável. Logs não registram corpo ou causa que contenha payload.

A confirmação espera até 5000 ms, configurável por pdv.probe.publish-timeout-ms. Timeout pode deixar resultado incerto. Uma queda entre publish e ack pode gerar respostas repetidas; não há inbox/outbox, deduplicação ou exactly-once. Dead lettering deste perfil não comprova ausência de perda em falhas de broker. Inspecione requestId, resposta existente e motivo antes de replay. PDV-038 tratará confiabilidade e recuperação de negócio.

O diagnóstico não implementa API produtora de negócio, transporte Outbox/Inbox, migrations em runtime, health HTTP ou RNF06 completo. Os modos explícitos de expiração e simulação abaixo usam banco/coordenação compartilhados. O processo independente demonstra AMQP real; não substitui a definição dos contratos finais e dos efeitos transacionais.

## Evidências e fontes

Os onze testes `WorkerAmqpIT` cobrem contrato/correlação, headers externos, JSON/versionamento/UUID/limites/content-type inválidos, DLQ, sanitização de logs e resposta sem rota contra broker real. Seis unitários `ProbePublisherTest` cobrem confirmação positiva/negativa, retorno, falha, timeout e interrupção; mocks nesses unitários não substituem integração. Checkstyle inclui fontes e testes. Surefire executa unitários em `test`; Failsafe executa integração em `verify`. A spec da PDV-006 preserva evidências daquele build e smoke do JAR. A [CI da PDV-008](../../docs/workflows/ci.md) executa o build completo com RabbitMQ temporário próprio.

Referências: [Spring AMQP — confirms e returns](https://docs.spring.io/spring-amqp/reference/amqp/template.html), [exceções e rejeição](https://docs.spring.io/spring-amqp/reference/amqp/exception-handling.html) e [AMQP no Spring Boot](https://docs.spring.io/spring-boot/reference/messaging/amqp.html). A arquitetura e pendências de domínio continuam no [contexto](../../docs/context/architecture.md).

## Expiração de reservas — PDV-030

Com PostgreSQL/RabbitMQ PDV saudáveis e API migrations V12 aplicadas, `./scripts/worker.ps1 -Action Verify` agora verifica ambos os serviços em banco UUID descartável próprio. SQL das migrations API é montado somente para testes, sem DDL no JAR. `./scripts/worker.ps1 -Action Run -ExpireReservations` habilita expiração; Run sem modos ativos mantém diagnóstico sem acesso ao banco; -SimulatePayments também ativa acesso ao banco. Alternativa nativa: PDV_RESERVATION_EXPIRY_ENABLED=true e PDV_DB_URL/USER/PASSWORD privados, schema pdv por padrão. Worker valida contrato existente e nunca migra.

Rodada a cada5s/até100 reservas, seleção indexada por prazo/id, transação por agregado e função comum V12. SKIP LOCKED evita aguardar outra coordenação; status EXPIRED/trilha/saldos mudam juntos uma vez. Reservas protegidas antes de pagamento seguem retidas mesmo vencidas. Falha de saldo/auditoria reverte tudo e rodada posterior tenta de novo; logs só contagens/erros constantes. Timeouts: conexão5s/socket10s/query5s, transação lock2s/statement4s. Fixed delay não sobrepõe a rodada anterior. Não implementa checkout/pagamento/Saga/reconciliação. [Spec](../../sdd/features/PDV-030-expiracao-worker.md), [contrato das reservas](../../docs/workflows/reservations.md).

## Processador de pagamento — PDV-037

Com migrations V15 aplicadas, `./scripts/worker.ps1 -Action Run -SimulatePayments -ExpireReservations` habilita os modos locais combinados. [Contrato e operação](../../docs/workflows/simulated-payments.md). Sem flags, simulação/expiração ficam desligadas. Poll5s/até100 prioriza retomada; três cenários determinísticos e resultado persistido, sem sorteio/rede financeira. Início e resultado são transações separadas protegidas pela Saga; nenhuma baixa/liberação/conclusão automática. Outbox/Inbox de negócio permanece na038.

## Transporte de negócio — PDV-038

`./scripts/worker.ps1 -Action Run -BusinessMessaging -ExpireReservations` usa outbox/inbox V16 e RabbitMQ real para solicitações/resultados. [Contrato, retry/DLQ/replay e limites](../../docs/workflows/business-messaging.md). Não combinar com SimulatePayments; diagnóstico permanece separado. Worker não migra banco e não publica diretamente como parte do commit SQL.

## Confirmação de venda — PDV-039

Modo BusinessMessaging também consome pdv.sale.confirm.v1 na fila results. Inbox, baixa, comprovante e conclusão confirmam na mesma transação PostgreSQL; falha vai ao retry/DLQ sem efeito parcial. [Contrato](../../docs/workflows/sale-confirmation.md).

## Recusa/reconciliação — PDV-040

BusinessMessaging também consome pdv.payment.reconciled.v1 e pdv.sale.release.v1, com prova durável, inbox e efeitos locais atômicos. TIMEOUT permanece protegido sem decisão; mensagem antiga não regride estado reconciliado. [Contrato](../../docs/workflows/payment-reconciliation.md).
