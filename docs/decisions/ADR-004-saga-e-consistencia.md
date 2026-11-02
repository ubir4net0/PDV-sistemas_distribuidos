# ADR-004 Saga, compensação e reconciliação

Nota de vigência: [ADR-008](ADR-008-estados-venda-pagamento.md) foi aprovada pelo proprietário e resolve DEC-01 no recorte descrito. [PDV-036](../../sdd/features/PDV-036-saga-estados-venda.md) implementa coordenação persistida; referências abaixo a estados ainda pendentes preservam o registro original. Baixa/comprovante, reconciliação/liberação e cancelamento permanecem nas tarefas próprias.

Data: 30/10/2026. Tarefa: [PDV-003](../../sdd/features/PDV-003-arquitetura-adrs.md). Estado: invariantes e padrão Saga declarados no pacote; coordenação e estados finais pendentes de PDV-007/036.

## Contexto e fontes

Especificação v1.0, seções 5, 8, 9 e 10; RF-V02/03/04, RNF09/10. Venda, pagamento fictício e confirmação de estoque atravessam processos e mensagens. Os [diagramas de venda](../diagrams/svg/07-sequencia-venda.svg) e [cancelamento](../diagrams/svg/08-sequencia-cancelamento.svg) são propostas revisáveis. DEC-01 impede fixar enums finais.

## Decisão registrada

A Saga coordena transações locais com consistência eventual. Cada etapa confirma seu próprio estado e intenção de evento; etapas seguintes podem falhar ou chegar depois. Não há rollback ACID global nem atomicidade simultânea entre PostgreSQL e RabbitMQ.

Coordenação persistida na API é proposta para tornar progresso e recuperação rastreáveis. O worker determina e registra o resultado fictício por identidade da operação. Reexecutar a mesma operação deve recuperar o resultado conhecido, sem sortear uma nova decisão.

Aprovação do pagamento isolada não conclui a venda: é necessária a confirmação idempotente de estoque. Recusa conhecida permite liberação segura. Timeout com resultado desconhecido exige consulta/reconciliação antes de confirmar ou liberar, mesmo se o prazo nominal da reserva expirou. TTL e tratamento de decisões tardias precisam de política explícita, sem compensação cega.

Compensação é nova operação de negócio, registrada e passível de falha. Cancelamento após aprovação deve coordenar estorno fictício e devolução de estoque, cada um uma única vez. A ordem final e as condições de cada passo pertencem à máquina de estados da PDV-036.

## Matriz de falhas e recuperação alvo

| Falha | Estado durável esperado | Recuperação segura |
| --- | --- | --- |
| Commit local falha | Sem reserva/venda/outbox parcial | Rollback local; repetição com mesma identidade |
| Commit ocorre; broker indisponível | Intenção pendente na outbox | Publicar depois sem recriar venda |
| Worker processa; resposta atrasa | Resultado fictício consultável | Reconciliar; não tratar silêncio como recusa |
| Pagamento aprovado; confirmação de estoque falha | Etapa pendente e rastreável | Retomar confirmação ou compensar conforme estado validado |
| Evento repetido ou fora de ordem | Efeito anterior identificável | Deduplicar e validar transição; não aplicar decremento cego |
| Estorno fictício falha | Cancelamento ainda incompleto | Retry/reconciliação, sem anunciar cancelamento concluído |
| Processo reinicia | Progresso preservado no banco | Retomar por etapa; não depender da memória do processo |

A resolução exata de conflito entre cancelamento e aprovação tardia será definida com DEC-01. Não liberar uma reserva ou devolver estoque apenas porque uma mensagem chegou atrasada.

## Justificativa e alternativas

Manter uma transação aberta durante todo o fluxo provocaria acoplamento e não resolveria atomicidade entre processos. Coreografia pura é alternativa; coordenação explícita é proposta para facilitar demonstração e auditoria. A decisão final da PDV-036 deverá identificar escritor e transições autorizadas.

## Consequências e validação futura

Interfaces precisam mostrar progresso pendente e leitura eventualmente atrasada sem inventar sucesso. Pagamento, confirmação e cancelamento não são sinônimos. PDV-036/038/052/053 devem injetar falhas entre etapas, reiniciar processos e repetir comandos/mensagens. Conferir ledger, saldo e histórico final. Nenhuma máquina de estados foi aprovada ou executada nesta tarefa.
