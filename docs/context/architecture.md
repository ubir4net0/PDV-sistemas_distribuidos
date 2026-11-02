# Arquitetura alvo do PDV

Estado: arquitetura alvo do pacote v1.0, adotada como referência pela ADR-007. Infraestrutura, API, worker técnico, identidade, clientes, catálogo, fornecedores e saldos/movimentos e reservas com TTL disponíveis nos incrementos documentados. Worker tem modos explícitos de expiração e simulação de pagamento sobre checkout/Saga persistidos; mensageria de negócio Outbox/Inbox foi adicionada na038; compras, frontend e efeitos finais de venda permanecem nas tarefas próprias.

## Registros de decisão

A [PDV-003](../../sdd/features/PDV-003-arquitetura-adrs.md) detalha as escolhas declaradas no pacote em cinco ADRs: [processos e fronteiras](../decisions/ADR-002-processos-e-fronteiras.md), [persistência e transações](../decisions/ADR-003-persistencia-e-transacoes.md), [Saga e consistência](../decisions/ADR-004-saga-e-consistencia.md), [mensageria e idempotência](../decisions/ADR-005-mensageria-e-idempotencia.md) e [limites acadêmicos](../decisions/ADR-006-limites-e-laboratorios.md). A [ADR-007 aprovada](../decisions/ADR-007-proposta-base-dominio.md) adota a referência textual, vocabulário de OC, fornecedor N:1 e complementos técnicos. O [índice](../decisions/README.md) distingue essas decisões dos detalhes ainda pendentes. Status de predecessoras não foi alterado pela aprovação.

## Componentes e fluxo alvo

Os [15 diagramas novos](../diagrams/README.md) representam a baseline textual v1 e foram adotados como referência de desenvolvimento na ADR-007. Preservam notas de decisão anteriores à aprovação; a ADR-007 prevalece para OC, fornecedor e complementos. Não comprovam equivalência aos originais ausentes nem schema implementado.

Frontend React/TypeScript/Vite/Tailwind -> API Java 21/Spring Boot -> PostgreSQL/Flyway. RabbitMQ conecta API e worker Spring Boot independente. Maven Wrapper para builds. Spring Security, Validation, JPA, Spring AMQP e Actuator serão definidos nos scaffolds.

Banco compartilhado entre dois processos é simplificação acadêmica. Transações fortes locais protegem estoque e reserva; Saga coordena fechamento/cancelamento com consistência eventual. Outbox/inbox e idempotência protegem reentregas. Timeout de resultado desconhecido exige reconciliação antes de liberar/confirmar reserva.

Pagamento simulado local tem aprovação, recusa, timeout, estorno e reprocessamento determinísticos. Métodos como PIX/cartão são rótulos fictícios; nenhuma API financeira/fiscal externa.

Estrutura alvo: backend/api, backend/worker, frontend, infra, docs, sdd/features e .github/workflows. O Compose da raiz inicia PostgreSQL/RabbitMQ; consultar [operação local](../../infra/README.md). API Spring Boot 4.1.2/Java 21, [Flyway](../workflows/database-migrations.md) V1–V16, [identidade/tokens](../workflows/identity-security.md) por JDBC e cadastros JPA de clientes/endereços/categorias/produtos/fornecedores. Health/readiness consultam PostgreSQL, liveness mede o processo. [API](../../backend/api/README.md) documenta módulos/camadas e contratos implementados. Checkout idempotente sales/V14, coordenação V15 e processador local opt-in estão implementados; compras e mensageria de negócio pela API permanecem pendentes. O bootstrap não aprovou domínio; decisões posteriores têm o alcance explícito de cada ADR/spec.

## Fronteiras de consistência

PDV-028 adiciona controle HTTP forte de revisões do saldo (ETag/If-Match na mesma URI GET/PATCH), preservando CAS/409 e fingerprint legado. Precondição falsa412 não modifica dados; repetição conhecida não duplica movimento. Sem novo DDL ou atomicidade distribuída; [spec](../../sdd/features/PDV-028-concorrencia-etags.md).

PDV-027: UPDATE de saldo condicionado a versão/disponibilidade/reservado, ordem determinística no lote e trilha imutável na mesma transação PostgreSQL. Identidade requestId/ator, lock transacional da chave e resposta original persistida evitam repetir efeito após retry; não é idempotência genérica de checkout. V11, [contrato](../workflows/inventory.md). Lote tudo-ou-nada/inteiros/ajuste absoluto e permissões confirmados pelo proprietário.

PDV-026: SQL crítico de saldo em inventory, V10 de saldos/aberturas, inicialização atômica por trigger de produto e versão independente do catálogo. [Contrato](../workflows/inventory.md). Leituras do estado transacional não implementam reserva/baixa/Saga ou projeção eventual. Quantidades inteiras e abertura zero confirmadas pelo proprietário.

Incremento PDV-020: catalog possui CRUD JPA real de categorias/produtos e V7; histórico por JDBC compartilha a transação JPA. Locks categoria→produto e versões protegem mudanças, UNIQUE protege SKU. Refinamento de estados/permissões confirmado pelo proprietário, documentado no [contrato](../workflows/catalog.md). Isso não implementa saldo, Saga ou compras.

PDV-021 adiciona leitura paginada/índices V8 e ensaio de carga confirmado. PDV-022 implementa fornecedores JPA/V9 e relação opcional N:1, com locks fornecedor→produto e histórico transacional; [contrato](../workflows/suppliers.md). Obrigatoriedade e RBAC desse cadastro foram confirmados pelo proprietário; compras e outros itens da DEC-06 permanecem nas tarefas próprias.

O [worker](../../backend/worker/README.md) tem build/processo próprios e um diagnóstico AMQP real com contrato JSON fixo, confirms/returns e DLQ. Diagnóstico AMQP não recebe comandos de pagamento da API. O modo explícito de simulação consulta intenções duráveis no banco compartilhado e usa coordenação V15; Outbox/Inbox de negócio permanece pendente.

| Fronteira | Garantia alvo | Limitação |
| --- | --- | --- |
| Transação PostgreSQL | Venda/itens/reserva/intenção de evento confirmam ou revertem juntos | Exige proteção concorrente, não uma consulta prévia de saldo |
| Banco para RabbitMQ | Outbox permite retomar publicação após falha | Commit e publish não são uma operação global atômica |
| Consumo para banco | Inbox e efeito local na mesma transação; ack após commit | Reentrega é possível; efeito de negócio também precisa de identidade |
| Venda completa | Saga persiste progresso e permite compensação/reconciliação | Aprovação isolada ou silêncio não significam conclusão/recusa |
| Projeção de leitura | Atualização assíncrona e reconstrução verificável | Pode estar atrasada; checkout consulta saldo transacional |

A matriz de falhas da ADR-004 orienta os testes futuros. Os contratos finais de API/eventos pertencem à PDV-007, schema à PDV-009, estados à PDV-036 e confiabilidade à PDV-038. Os nomes e transições dos diagramas continuam candidatos.

## Decisões pendentes

Consultar o índice de decisões: DEC-01 foi resolvida no recorte da ADR-008; a reconciliação inicial está aprovada na ADR-009; cancelamento/nova tentativa continuam nas tarefas próprias, inclusive PDV-041. Recebimento parcial (DEC-05), TTLs/descontos/carga e RBAC de negócios futuros (DEC-06), transições de OC, obrigatoriedade do fornecedor e campos/políticas dos complementos continuam pendentes. Vocabulário de OC, cardinalidade máxima N:1 e inclusão dos complementos já foram aprovados na ADR-007; não reabrir essas escolhas sem novo requisito.

## Validação alvo

JUnit/Spring Boot Test/Testcontainers, PostgreSQL/RabbitMQ reais, Vitest/Testing Library/Playwright e carga declarada. RNF04/12/13/15/19 dependem de implantação/ensaio real; nenhum é comprovado pela arquitetura desenhada.

A PDV-013 implementa a matriz inicial explicitamente aprovada pelo proprietário, com guardas Spring Security de rota/método e catálogo administrativo. Permissões das funcionalidades futuras continuam pendentes; consultar docs/workflows/identity-security.md e a spec PDV-013.

PDV-029: reserva técnica com itens, proprietário, TTL obrigatório e trilha imutável V12. UPDATE condicional protege disponível; funções PostgreSQL comuns serializam renovação/proteção/expiração, sem baixar físico ou liberar resultado incerto. [Contrato](../workflows/reservations.md). Vínculo com venda/checkout e reconciliação permanecem nas tarefas próprias.

PDV-030: worker independente usa PostgreSQL compartilhado para expiração via função transacional V12, sem DDL em runtime, opt-in5s/100. Isso não adiciona mensagens de negócio, outbox/inbox ou integração de pagamento. [Spec](../../sdd/features/PDV-030-expiracao-worker.md).

PDV-034: carrinho usa CRUD JPA/@Version com lock do agregado, leitura REPEATABLE_READ e auditoria JDBC na mesma transação. Dinheiro BigDecimal/preço capturado sem descontos; itens não reservam estoque. V13, [contrato](../workflows/carts.md). Checkout PDV-035 vincula/congela carrinho/venda/reserva/intenção em transação própria.

PDV-035: [checkout](../workflows/checkout.md) usa transação JPA/JDBC compartilhada, locks ordenados e resposta imutável. [ADR-008 aprovada](../decisions/ADR-008-estados-venda-pagamento.md) resolve DEC-01 no recorte explícito, sem antecipar as implementações 036–041.

PDV-036: funções transacionais compartilhadas e guardas V15 coordenam estados conforme ADR-008; [contrato](../workflows/sale-saga.md). Pagamento/Saga têm progresso durável, resultado e eventos, sem implementar transporte confiável ou efeitos finais.

PDV-037: [processador local](../workflows/simulated-payments.md) no worker, desligado por padrão, retoma etapas iniciadas e grava decisão determinística. Poll de laboratório não substitui o transporte Outbox/Inbox da038.

PDV-038: [transporte de negócio](../workflows/business-messaging.md) por outbox/inbox V16, publisher confirms/returns, ack após commit, retry limitado e DLQ. API grava intenção; worker publica/consome solicitação e aplica resposta pelo coordenador SQL compartilhado. Simulação local e modo AMQP são exclusivos; stock/comprovante/liberação seguem próprios.

PDV-039: [confirmação transacional](../workflows/sale-confirmation.md) por etapa outbox/inbox própria. V17 acrescenta ledger automático unido ao histórico, comprovante imutável e avanço da Saga, preservando snapshots e a identidade do pagamento.

PDV-040: [ADR-009](../decisions/ADR-009-reconciliacao-simulada.md) aprova prova administrativa local separada do TIMEOUT, sem ampliar leitura de vendas. V18 e [contrato](../workflows/payment-reconciliation.md) coordenam decisão tardia/liberação com outbox/inbox e comandos idempotentes próprios. Cancelamento/estorno seguem041.

PDV-007: [OpenAPI e eventos versionados](../../contracts/README.md) descrevem o runtime implementado, com schemas/exemplos e verificação de inventário/campos. Erros seguros abrangem parâmetros obrigatórios ausentes e mídia incompatível. Evolução futura exige coordenar contratos, sem anunciar rotas ainda não implementadas.
