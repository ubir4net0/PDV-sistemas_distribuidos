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

Verify executa clean verify e testes AMQP contra o RabbitMQ real da PDV-004. A ausência de broker saudável falha a verificação; não há fallback para driver fake nem teste de integração ignorado. O teste usa prefixo UUID próprio e remove apenas seus objetos. Cache Maven: volume pdv-worker-maven-cache; builds em backend/worker/target, ignorado.

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

Não há API produtora integrada, pagamento, Saga, banco, migration, health HTTP ou RNF06 completo comprovado. O processo independente demonstra AMQP real; não substitui a definição dos contratos finais e dos efeitos transacionais.

## Evidências e fontes

Os testes reais cobrem contrato/correlação, headers externos, JSON/versionamento/UUID/limites/content-type inválidos, DLQ, sanitização de logs e resposta sem rota. A spec registra resultados executados e smoke do JAR. CI de produto será implementada na PDV-008; o workflow atual verifica planejamento.

Referências: [Spring AMQP — confirms e returns](https://docs.spring.io/spring-amqp/reference/amqp/template.html), [exceções e rejeição](https://docs.spring.io/spring-amqp/reference/amqp/exception-handling.html) e [AMQP no Spring Boot](https://docs.spring.io/spring-boot/reference/messaging/amqp.html). A arquitetura e pendências de domínio continuam no [contexto](../../docs/context/architecture.md).
