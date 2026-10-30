# ADR-005 mensageria confiável e idempotência

Data: 30/10/2026. Tarefa: [PDV-003](../../sdd/features/PDV-003-arquitetura-adrs.md). Estado: RabbitMQ, outbox/inbox, retry e DLQ declarados no pacote; contratos e entidades técnicas ainda propostos (DEC-04).

## Contexto e fontes

Especificação v1.0, seções 2, 5, 7 e 8; RNF06/08/17. Uma falha entre commit e publicação pode perder trabalho se a intenção não for persistida. A [documentação RabbitMQ](https://www.rabbitmq.com/docs/confirms) distingue publisher confirms de acknowledgements de consumidores; nenhum deles comprova, sozinho, conclusão de toda a Saga.

## Decisão registrada

A transação local grava o efeito de negócio e a intenção na outbox. Um publicador envia ao RabbitMQ e registra a publicação conforme confirmação e roteamento bem-sucedidos. Confirmação do broker não significa que o consumidor concluiu o negócio. Mensagem sem rota precisa de tratamento explícito; um confirm isolado não basta para considerar entrega útil.

Entrega deve tolerar repetição. Perda da confirmação ou queda depois da publicação permite republicação; por isso, não prometer entrega exactly-once. A [orientação de confiabilidade do RabbitMQ](https://www.rabbitmq.com/docs/reliability) exige deduplicação ou tratamento idempotente nesses cenários.

No consumidor, registro inbox e efeito local precisam ser atômicos no PostgreSQL, inclusive intenção de evento de resposta quando houver. Ack ocorre após commit. Queda entre commit e ack causa reentrega, que deve reconhecer o efeito já aplicado. Se um efeito não compartilhar a transação, ele exige sua própria identidade e recuperação; marcar inbox antes de executá-lo não o torna seguro.

## Identidades propostas para refinamento

| Identidade | Papel | Definição futura |
| --- | --- | --- |
| Chave de comando | Repetição da mesma mutação HTTP | Escopo, fingerprint, resposta, retenção e erro de conflito |
| ID de evento + consumidor lógico | Deduplicação da entrega sem impedir outros assinantes | Formato do envelope e escopo da unicidade inbox |
| ID da operação de negócio | Impedir mesmo efeito por eventos diferentes | Restrição por venda/reserva/pagamento e tipo de efeito |
| Correlação e causalidade | Relacionar etapas e investigação | Propagação de trace/span e cadeia de eventos |

Fingerprint diferente com a mesma chave não pode reutilizar silenciosamente uma resposta anterior. Duas requisições concorrentes com a mesma chave precisam de arbitragem durável. A política de expiração não pode permitir duplicar uma venda ao perder seu histórico idempotente.

IDs técnicos e payload versionado serão definidos na PDV-007. Nomes de tabelas, índices, exchanges, filas, routing keys, tentativas, backoff, retenção e estratégia de ordenação pertencem a PDV-009/038. DEC-04 permanece aberta; o padrão não aprova schema.

## Falhas e operação

Retries devem distinguir falha transitória de erro permanente. Mensagem inválida não deve entrar em requeue infinito; após política limitada e observável, DLQ permite investigação. Reprocessamento manual preserva identidades e verifica se o efeito já ocorreu. Não declarar sucesso apenas porque a fila foi esvaziada.

Durabilidade requer configurar e testar conjuntamente filas, mensagens, confirms e consumidores. A política concreta de persistência/replicação será validada no ambiente; este documento não prova ausência de perda em toda falha.

## Justificativa e alternativas

Publicar diretamente após commit sem outbox deixa uma janela de perda. Publicar antes do commit pode anunciar negócio revertido. Outbox/inbox custa armazenamento e processamento adicional, mas torna recuperação observável sem prometer transação global. Deduplicação apenas em memória não resiste a reinício nem concorrência entre instâncias.

## Validação futura

PDV-038: queda em cada janela entre commit, publish, confirm e ack; mensagem duplicada e fora de ordem; consumidores concorrentes; broker indisponível; payload inválido; DLQ e replay seguro. Usar PostgreSQL/RabbitMQ reais e provar efeito único no banco. Não há mensageria implementada ou testada nesta tarefa.
