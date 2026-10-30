# Arquitetura alvo do PDV

Estado: requisitos do pacote v1.0, sujeitos à conciliação acadêmica. Não implementado.

Os [15 diagramas novos](../diagrams/README.md) representam a baseline textual como proposta v1. Incluem arquitetura, domínio, três DERs, sequências e estados; não substituem a verificação dos originais acadêmicos ausentes nem aprovam contratos, cardinalidade ou schema. DEC-01 a DEC-06 permanecem indicadas nos modelos.

Frontend React/TypeScript/Vite/Tailwind -> API Java 21/Spring Boot -> PostgreSQL/Flyway. RabbitMQ conecta API e worker Spring Boot independente. Maven Wrapper para builds. Spring Security, Validation, JPA, Spring AMQP e Actuator serão definidos nos scaffolds.

Banco compartilhado entre dois processos é simplificação acadêmica. Transações fortes locais protegem estoque e reserva; Saga coordena fechamento/cancelamento com consistência eventual. Outbox/inbox e idempotência protegem reentregas. Timeout de resultado desconhecido exige reconciliação antes de liberar/confirmar reserva.

Pagamento simulado local tem aprovação, recusa, timeout, estorno e reprocessamento determinísticos. Métodos como PIX/cartão são rótulos fictícios; nenhuma API financeira/fiscal externa.

Estrutura alvo: backend/api, backend/worker, frontend, infra, docs, sdd/features e .github/workflows. Atualmente somente documentação. Versão estável compatível do Spring Boot deve ser pesquisada em fonte oficial na hora de PDV-005; nenhuma versão foi presumida neste ciclo.

## Decisões pendentes

Ver docs/reports/reconciliacao.md: estados de venda/pagamento/OC, cardinalidade fornecedor-produto, complementos do DER, semântica de lote, TTLs, RBAC e ambiente avançado. Não criar contratos ou migrations de domínio a partir de decisão não resolvida.

## Validação alvo

JUnit/Spring Boot Test/Testcontainers, PostgreSQL/RabbitMQ reais, Vitest/Testing Library/Playwright e carga declarada. RNF04/12/13/15/19 dependem de implantação/ensaio real; nenhum é comprovado pela arquitetura desenhada.
