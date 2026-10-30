# Decisões do PDV

Este índice faz parte da [PDV-003](../../sdd/features/PDV-003-arquitetura-adrs.md). Registra a arquitetura alvo, sem comprovar implementação. A [especificação fornecida](../sources/PDV_ESPECIFICACAO_E_BACKLOG.md) é a fonte primária; a [reconciliação](../reports/reconciliacao.md) distingue divergências. Notion é a fonte viva de status.

| ADR | Tema | Classificação |
| --- | --- | --- |
| [ADR-001](ADR-001-fluxo-individual.md) | Trabalho individual e commits diretos | Decidido explicitamente pelo proprietário |
| [ADR-002](ADR-002-processos-e-fronteiras.md) | API e worker independentes; PostgreSQL compartilhado | Arquitetura declarada no pacote; implementação pendente |
| [ADR-003](ADR-003-persistencia-e-transacoes.md) | PostgreSQL, transações e Flyway | Escolhas do pacote; estratégia concreta de concorrência pendente |
| [ADR-004](ADR-004-saga-e-consistencia.md) | Saga, compensação e resultado incerto | Invariantes do pacote; máquina de estados pendente |
| [ADR-005](ADR-005-mensageria-e-idempotencia.md) | RabbitMQ, outbox/inbox e idempotência | Padrões do pacote; schema e contratos propostos |
| [ADR-006](ADR-006-limites-e-laboratorios.md) | Simulação local e limites de evidência | Limites explícitos do pacote; RNFs sem prova |

As ADRs 002–006 documentam o texto disponível para validação; não aprovam a baseline pendente da PDV-001. “Declarado no pacote” indica origem rastreável, sem equivalência comprovada aos diagramas acadêmicos ausentes.

## Decisões que continuam abertas

| Referência | Questão | Onde fechar antes de implementar |
| --- | --- | --- |
| SRC-01 | Validação da baseline e dos novos diagramas; originais ausentes | PDV-001 |
| DEC-01 | Estados finais de venda/pagamento e transições | PDV-007 e PDV-036 |
| DEC-02 | Estados de ordem de compra | PDV-007, PDV-009 e PDV-023 |
| DEC-03 | Cardinalidade fornecedor-produto | PDV-007, PDV-009 e PDV-022 |
| DEC-04 | Schema técnico de permissões, outbox/inbox, idempotência, comprovantes e projeções | Refinamento de PDV-009/011/038 |
| DEC-05 | Atomicidade de lote e recebimento parcial | PDV-024 e PDV-027 |
| DEC-06 | RBAC exato, TTLs, descontos, estados auxiliares e carga | Specs das respectivas tarefas |

As ADRs são históricas: uma decisão posterior deve citar e substituir o registro anterior, sem apagar a justificativa. Detalhar um padrão de confiabilidade não aprova as entidades propostas em DEC-04.
