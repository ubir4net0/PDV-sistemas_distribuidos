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
| [ADR-007](ADR-007-proposta-base-dominio.md) | Referência de domínio, OC, fornecedor e complementos técnicos | Aprovada explicitamente pelo proprietário; implementação incremental nas tarefas próprias |

As ADRs 002–006 preservam o registro anterior à aprovação da ADR-007. “Declarado no pacote” indica origem rastreável. O proprietário aprovou a baseline textual e os novos diagramas como referência de desenvolvimento; a equivalência aos originais acadêmicos ausentes não foi comprovada.

## Estados de venda/pagamento

[ADR-008](ADR-008-estados-venda-pagamento.md) aprovada pelo proprietário: DEC-01 resolvida para checkout/Saga/simulador; TIMEOUT corresponde a INDETERMINADO e conserva reserva protegida. Efeitos de baixa/comprovante, reconciliação/liberação e cancelamento permanecem nas tarefas próprias. As indicações antigas abaixo são históricas quanto a esse recorte.

## Decisões aprovadas na ADR-007

| Referência | Decisão vigente |
| --- | --- |
| SRC-01 | Baseline textual v1.0 e novos diagramas adotados como referência de desenvolvimento |
| DEC-02 | Vocabulário OC: CRIADA, ENVIADA, RECEBIDA, CANCELADA |
| DEC-03 | Produto com no máximo um fornecedor, N:1 |
| DEC-04 | Complementos técnicos admitidos incrementalmente; nomes SQL em inglês |

## Decisões que continuam abertas

| Referência | Questão | Onde fechar antes de implementar |
| --- | --- | --- |
| SRC-01 | Originais/equivalência acadêmica continuam indisponíveis; referência de desenvolvimento já aprovada | PDV-001 |
| DEC-01 | Estados finais de venda/pagamento e transições | PDV-007 e PDV-036 |
| DEC-02 | Regras de transição de OC, sem reabrir o vocabulário aprovado | PDV-023/024 |
| DEC-03 | Obrigatoriedade/nullable do fornecedor, sem reabrir a cardinalidade máxima N:1 | PDV-020/022 |
| DEC-04 | Campos/restrições/políticas dos complementos já admitidos | Specs de PDV-011/035/038/039 e projeções justificadas |
| DEC-05 | Atomicidade de lote e recebimento parcial | PDV-024 e PDV-027 |
| DEC-06 | RBAC de negócios futuros, TTLs, descontos, estados auxiliares e carga | Specs das respectivas tarefas |

As ADRs preservam histórico: decisão posterior deve citar e substituir o registro anterior, sem apagar a justificativa. A ADR-007 aprova a inclusão dos complementos, não todos os seus campos, políticas ou implementação.

O proprietário aprovou a matriz inicial de endpoints da [PDV-013](../../sdd/features/PDV-013-rbac-endpoints.md): health/login públicos, perfil/logout próprios autenticados, catálogo administrativo e denyAll para o restante. Isso não aprova os demais itens da DEC-06.
