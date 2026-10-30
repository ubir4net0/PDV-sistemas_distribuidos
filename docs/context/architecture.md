# Arquitetura alvo do PDV

Estado: requisitos do pacote v1.0, sujeitos à conciliação acadêmica. Não implementado.

## Registros de decisão

A [PDV-003](../../sdd/features/PDV-003-arquitetura-adrs.md) detalha as escolhas declaradas no pacote em cinco ADRs: [processos e fronteiras](../decisions/ADR-002-processos-e-fronteiras.md), [persistência e transações](../decisions/ADR-003-persistencia-e-transacoes.md), [Saga e consistência](../decisions/ADR-004-saga-e-consistencia.md), [mensageria e idempotência](../decisions/ADR-005-mensageria-e-idempotencia.md) e [limites acadêmicos](../decisions/ADR-006-limites-e-laboratorios.md). O [índice de decisões](../decisions/README.md) distingue requisitos declarados, propostas e questões abertas. A dependência PDV-001 continua em validação; este avanço documental não libera código de domínio.

## Componentes e fluxo alvo

Os [15 diagramas novos](../diagrams/README.md) representam a baseline textual como proposta v1. Incluem arquitetura, domínio, três DERs, sequências e estados; não substituem a verificação dos originais acadêmicos ausentes nem aprovam contratos, cardinalidade ou schema. DEC-01 a DEC-06 permanecem indicadas nos modelos.

Frontend React/TypeScript/Vite/Tailwind -> API Java 21/Spring Boot -> PostgreSQL/Flyway. RabbitMQ conecta API e worker Spring Boot independente. Maven Wrapper para builds. Spring Security, Validation, JPA, Spring AMQP e Actuator serão definidos nos scaffolds.

Banco compartilhado entre dois processos é simplificação acadêmica. Transações fortes locais protegem estoque e reserva; Saga coordena fechamento/cancelamento com consistência eventual. Outbox/inbox e idempotência protegem reentregas. Timeout de resultado desconhecido exige reconciliação antes de liberar/confirmar reserva.

Pagamento simulado local tem aprovação, recusa, timeout, estorno e reprocessamento determinísticos. Métodos como PIX/cartão são rótulos fictícios; nenhuma API financeira/fiscal externa.

Estrutura alvo: backend/api, backend/worker, frontend, infra, docs, sdd/features e .github/workflows. Atualmente somente documentação. Versão estável compatível do Spring Boot deve ser pesquisada em fonte oficial na hora de PDV-005; nenhuma versão foi presumida neste ciclo.

## Fronteiras de consistência

| Fronteira | Garantia alvo | Limitação |
| --- | --- | --- |
| Transação PostgreSQL | Venda/itens/reserva/intenção de evento confirmam ou revertem juntos | Exige proteção concorrente, não uma consulta prévia de saldo |
| Banco para RabbitMQ | Outbox permite retomar publicação após falha | Commit e publish não são uma operação global atômica |
| Consumo para banco | Inbox e efeito local na mesma transação; ack após commit | Reentrega é possível; efeito de negócio também precisa de identidade |
| Venda completa | Saga persiste progresso e permite compensação/reconciliação | Aprovação isolada ou silêncio não significam conclusão/recusa |
| Projeção de leitura | Atualização assíncrona e reconstrução verificável | Pode estar atrasada; checkout consulta saldo transacional |

A matriz de falhas da ADR-004 orienta os testes futuros. Os contratos finais de API/eventos pertencem à PDV-007, schema à PDV-009, estados à PDV-036 e confiabilidade à PDV-038. Os nomes e transições dos diagramas continuam candidatos.

## Decisões pendentes

Ver docs/reports/reconciliacao.md: estados de venda/pagamento/OC, cardinalidade fornecedor-produto, complementos do DER, semântica de lote, TTLs, RBAC e ambiente avançado. Não criar contratos ou migrations de domínio a partir de decisão não resolvida.

## Validação alvo

JUnit/Spring Boot Test/Testcontainers, PostgreSQL/RabbitMQ reais, Vitest/Testing Library/Playwright e carga declarada. RNF04/12/13/15/19 dependem de implantação/ensaio real; nenhum é comprovado pela arquitetura desenhada.
