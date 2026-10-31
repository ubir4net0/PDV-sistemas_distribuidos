# Arquitetura alvo do PDV

Estado: arquitetura alvo do pacote v1.0, sujeita à conciliação acadêmica. Infraestrutura pela PDV-004, API pela PDV-005 e worker técnico pela PDV-006 disponíveis. Domínio e integrações de negócio ainda não implementados.

## Registros de decisão

A [PDV-003](../../sdd/features/PDV-003-arquitetura-adrs.md) detalha as escolhas declaradas no pacote em cinco ADRs: [processos e fronteiras](../decisions/ADR-002-processos-e-fronteiras.md), [persistência e transações](../decisions/ADR-003-persistencia-e-transacoes.md), [Saga e consistência](../decisions/ADR-004-saga-e-consistencia.md), [mensageria e idempotência](../decisions/ADR-005-mensageria-e-idempotencia.md) e [limites acadêmicos](../decisions/ADR-006-limites-e-laboratorios.md). A [ADR-007 aprovada](../decisions/ADR-007-proposta-base-dominio.md) adota a referência textual, vocabulário de OC, fornecedor N:1 e complementos técnicos. O [índice](../decisions/README.md) distingue essas decisões dos detalhes ainda pendentes. Status de predecessoras não foi alterado pela aprovação.

## Componentes e fluxo alvo

Os [15 diagramas novos](../diagrams/README.md) representam a baseline textual v1 e foram adotados como referência de desenvolvimento na ADR-007. Preservam notas de decisão anteriores à aprovação; a ADR-007 prevalece para OC, fornecedor e complementos. Não comprovam equivalência aos originais ausentes nem schema implementado.

Frontend React/TypeScript/Vite/Tailwind -> API Java 21/Spring Boot -> PostgreSQL/Flyway. RabbitMQ conecta API e worker Spring Boot independente. Maven Wrapper para builds. Spring Security, Validation, JPA, Spring AMQP e Actuator serão definidos nos scaffolds.

Banco compartilhado entre dois processos é simplificação acadêmica. Transações fortes locais protegem estoque e reserva; Saga coordena fechamento/cancelamento com consistência eventual. Outbox/inbox e idempotência protegem reentregas. Timeout de resultado desconhecido exige reconciliação antes de liberar/confirmar reserva.

Pagamento simulado local tem aprovação, recusa, timeout, estorno e reprocessamento determinísticos. Métodos como PIX/cartão são rótulos fictícios; nenhuma API financeira/fiscal externa.

Estrutura alvo: backend/api, backend/worker, frontend, infra, docs, sdd/features e .github/workflows. O Compose da raiz inicia somente PostgreSQL/RabbitMQ; consultar [operação local](../../infra/README.md). A API usa Spring Boot 4.1.2 e Java 21 isolado; a [base técnica Flyway](../workflows/database-migrations.md) adiciona JDBC e namespace versionado; V2/V3 implementam persistência de identidade/tokens, com Spring Security e BCrypt conforme o [contrato](../workflows/identity-security.md). Cadastros comerciais seguem pendentes. Health/readiness consultam PostgreSQL, liveness mede o processo. [API](../../backend/api/README.md) documenta módulos/camadas; JPA, AMQP pela API e contratos de negócio permanecem pendentes. Nenhuma decisão de domínio foi aprovada pelo bootstrap.

## Fronteiras de consistência

O [worker](../../backend/worker/README.md) tem build/processo próprios e um diagnóstico AMQP real com contrato JSON fixo, confirms/returns e DLQ. Não recebe comandos da API nem processa pagamentos. Esse scaffold não implementa a outbox/inbox ou garantias transacionais da arquitetura alvo abaixo.

| Fronteira | Garantia alvo | Limitação |
| --- | --- | --- |
| Transação PostgreSQL | Venda/itens/reserva/intenção de evento confirmam ou revertem juntos | Exige proteção concorrente, não uma consulta prévia de saldo |
| Banco para RabbitMQ | Outbox permite retomar publicação após falha | Commit e publish não são uma operação global atômica |
| Consumo para banco | Inbox e efeito local na mesma transação; ack após commit | Reentrega é possível; efeito de negócio também precisa de identidade |
| Venda completa | Saga persiste progresso e permite compensação/reconciliação | Aprovação isolada ou silêncio não significam conclusão/recusa |
| Projeção de leitura | Atualização assíncrona e reconstrução verificável | Pode estar atrasada; checkout consulta saldo transacional |

A matriz de falhas da ADR-004 orienta os testes futuros. Os contratos finais de API/eventos pertencem à PDV-007, schema à PDV-009, estados à PDV-036 e confiabilidade à PDV-038. Os nomes e transições dos diagramas continuam candidatos.

## Decisões pendentes

Consultar o índice de decisões: estados de venda/pagamento (DEC-01), lote/recebimento parcial (DEC-05), TTLs/descontos/carga e RBAC de negócios futuros (DEC-06), transições de OC, obrigatoriedade do fornecedor e campos/políticas dos complementos continuam pendentes. Vocabulário de OC, cardinalidade máxima N:1 e inclusão dos complementos já foram aprovados na ADR-007; não reabrir essas escolhas sem novo requisito.

## Validação alvo

JUnit/Spring Boot Test/Testcontainers, PostgreSQL/RabbitMQ reais, Vitest/Testing Library/Playwright e carga declarada. RNF04/12/13/15/19 dependem de implantação/ensaio real; nenhum é comprovado pela arquitetura desenhada.

A PDV-013 implementa a matriz inicial explicitamente aprovada pelo proprietário, com guardas Spring Security de rota/método e catálogo administrativo. Permissões das funcionalidades futuras continuam pendentes; consultar docs/workflows/identity-security.md e a spec PDV-013.
