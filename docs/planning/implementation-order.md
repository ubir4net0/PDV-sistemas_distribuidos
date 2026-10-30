# Plano de implementação do PDV

Fluxo vigente: trabalho individual com commits diretos em main, sem PR ou revisão de terceiros, conforme a [ADR-001](../decisions/ADR-001-fluxo-individual.md). Fontes, aceites, dependências e ordem abaixo foram preservados; menções originais a Gitflow são históricas e não impõem PR.

## Ordem de implementação proposta
A sequência abaixo respeita todas as dependências declaradas e prioriza P0 dentre as tarefas elegíveis; sem ciclos no grafo de 60 tarefas. Ela não altera a prioridade dos cards nem representa autorização para iniciar tarefa bloqueada. Após cada entrega, reler a próxima tarefa viva, aplicar DoR e criar a spec; implementar com commits diretos em main e registrar evidências, sem PR obrigatório.

## Gates
1. Validar as fontes e decisões em PDV-001; preparar a baseline documental e Gitflow de PDV-002/010 e revisar arquitetura PDV-003.
2. Confirmar Java 21, versão estável compatível do Spring Boot e toolchain na hora do scaffold; infraestrutura local isolada em PDV-004, API PDV-005, worker PDV-006 e contratos PDV-007.
3. CI, Flyway real, autenticação/RBAC e contratos frontend antes dos cadastros e estoque.
4. Estoque transacional e concorrência antes de checkout, Saga, outbox/inbox, pagamento fictício, confirmação e compensação.
5. Executar as P1 exigidas para PDV-033 e PDV-060 após contratos. P0 isolado não fecha a entrega; não remover dependências para simular avanço.
6. P1 de relatórios, observabilidade e validação; somente depois evidenciar a demonstração final. P2 são laboratório avançado, sujeitos ao aceite acadêmico sobre obrigatoriedade.

## Sequência topológica completa
| Ordem | Tarefa | Prioridade | Dependências |
| --- | --- | --- | --- |
| 1 | [PDV-001 — Auditar Notion e definir baseline de escopo](https://app.notion.com/p/4092c9a9991781b89668e2fb5d0da613?pvs=204) | P0 | — |
| 2 | [PDV-002 — Inicializar repositório, Gitflow e proteções](https://app.notion.com/p/4092c9a9991781359c70ed9efb999125?pvs=204) | P0 | PDV-001 |
| 3 | [PDV-003 — Documentar arquitetura e ADRs](https://app.notion.com/p/4092c9a99917814dac59ea052c2c0706?pvs=204) | P0 | PDV-001 |
| 4 | [PDV-004 — Criar Docker Compose e health checks](https://app.notion.com/p/4092c9a9991781d9a047d00b35386b9e?pvs=204) | P0 | PDV-002, PDV-003 |
| 5 | [PDV-005 — Criar scaffold da API Spring Boot](https://app.notion.com/p/4092c9a9991781788a8bf31980f507db?pvs=204) | P0 | PDV-002, PDV-003 |
| 6 | [PDV-006 — Criar scaffold do worker Java](https://app.notion.com/p/4092c9a9991781408cc3ca25408403fb?pvs=204) | P0 | PDV-004, PDV-005 |
| 7 | [PDV-007 — Definir OpenAPI e contratos de evento](https://app.notion.com/p/4092c9a9991781599d94cbac5955bf23?pvs=204) | P0 | PDV-003, PDV-005 |
| 8 | [PDV-008 — Implementar CI e qualidade](https://app.notion.com/p/4092c9a9991781bb9249d14f71a1c1b5?pvs=204) | P0 | PDV-005, PDV-006 |
| 9 | [PDV-009 — Configurar Flyway e migrações iniciais](https://app.notion.com/p/4092c9a9991781c2b27df2e15e66b32e?pvs=204) | P0 | PDV-004, PDV-005 |
| 10 | [PDV-010 — Criar AGENTS.md e workflow Notion da equipe](https://app.notion.com/p/4092c9a999178148a078eb7d549cecce?pvs=204) | P0 | PDV-001, PDV-002 |
| 11 | [PDV-011 — Modelar usuários, papéis e permissões](https://app.notion.com/p/4092c9a999178171a60aed6ac3aa33d1?pvs=204) | P0 | PDV-009 |
| 12 | [PDV-012 — Implementar login, tokens e hash de senhas](https://app.notion.com/p/4092c9a99917818fb1dcfc6022726204?pvs=204) | P0 | PDV-011 |
| 13 | [PDV-013 — Aplicar RBAC a todos os endpoints](https://app.notion.com/p/4092c9a9991781168ae2c1400b42c527?pvs=204) | P0 | PDV-011, PDV-012 |
| 14 | [PDV-015 — Construir login e sessão no frontend](https://app.notion.com/p/4092c9a9991781ff8086c77426c81816?pvs=204) | P0 | PDV-007, PDV-012 |
| 15 | [PDV-016 — Criar clientes com CPF/CNPJ único](https://app.notion.com/p/4092c9a999178163b2f4fab53e8aed3d?pvs=204) | P0 | PDV-009, PDV-013 |
| 16 | [PDV-017 — Editar, consultar e inativar clientes](https://app.notion.com/p/4092c9a9991781e69f46c7ff442b3319?pvs=204) | P0 | PDV-016 |
| 17 | [PDV-019 — Criar telas de clientes](https://app.notion.com/p/4092c9a9991781be9841f5804c942398?pvs=204) | P0 | PDV-015, PDV-016, PDV-017 |
| 18 | [PDV-020 — Criar categorias e produtos](https://app.notion.com/p/4092c9a99917815c8a84c7a337aff5c0?pvs=204) | P0 | PDV-009, PDV-013 |
| 19 | [PDV-021 — Consultar catálogo com filtros/paginação](https://app.notion.com/p/4092c9a9991781c4a796e2fbe8e288cb?pvs=204) | P0 | PDV-020 |
| 20 | [PDV-022 — Cadastrar fornecedores e seus vínculos](https://app.notion.com/p/4092c9a9991781cfa8e2d1d1c9f61895?pvs=204) | P0 | PDV-009, PDV-013, PDV-020 |
| 21 | [PDV-026 — Definir saldo físico/reservado/disponível](https://app.notion.com/p/4092c9a9991781c2938ada931c5332db?pvs=204) | P0 | PDV-009, PDV-020 |
| 22 | [PDV-027 — Implementar entradas, saídas e ajustes atômicos](https://app.notion.com/p/4092c9a99917814faa7bd4b02551120e?pvs=204) | P0 | PDV-026 |
| 23 | [PDV-028 — Implementar concorrência otimista e ETags](https://app.notion.com/p/4092c9a9991781c39371e151bd0ba8af?pvs=204) | P0 | PDV-026, PDV-027 |
| 24 | [PDV-029 — Criar reserva com prazo de expiração](https://app.notion.com/p/4092c9a99917816da01fc0dd3c880051?pvs=204) | P0 | PDV-026, PDV-028 |
| 25 | [PDV-030 — Liberar reservas expiradas com job idempotente](https://app.notion.com/p/4092c9a99917816b9e42e02e93001099?pvs=204) | P0 | PDV-029, PDV-006 |
| 26 | [PDV-034 — Implementar carrinho e itens de venda](https://app.notion.com/p/4092c9a999178116b1b0f11f0151607e?pvs=204) | P0 | PDV-016, PDV-020, PDV-026 |
| 27 | [PDV-035 — Criar checkout idempotente](https://app.notion.com/p/4092c9a9991781ee8018f30608375839?pvs=204) | P0 | PDV-029, PDV-034 |
| 28 | [PDV-036 — Modelar Saga e máquina de estados de venda](https://app.notion.com/p/4092c9a99917815aa26ffe26ced87409?pvs=204) | P0 | PDV-003, PDV-035 |
| 29 | [PDV-037 — Criar processador de pagamento simulado](https://app.notion.com/p/4092c9a99917817f9f8fdeff82b5dad9?pvs=204) | P0 | PDV-006, PDV-036 |
| 30 | [PDV-038 — Implementar Outbox/Inbox e consumo confiável](https://app.notion.com/p/4092c9a9991781cbaf48deecaaacbd81?pvs=204) | P0 | PDV-006, PDV-009, PDV-036 |
| 31 | [PDV-039 — Concluir venda, baixar estoque e emitir comprovante](https://app.notion.com/p/4092c9a9991781f084d4e86acf07e56b?pvs=204) | P0 | PDV-029, PDV-036, PDV-037, PDV-038 |
| 32 | [PDV-040 — Tratar recusa, timeout e reconciliação](https://app.notion.com/p/4092c9a9991781298a2aec4b5d402663?pvs=204) | P0 | PDV-030, PDV-036, PDV-037, PDV-038 |
| 33 | [PDV-041 — Cancelar venda com compensação e estorno fictício](https://app.notion.com/p/4092c9a9991781e4873ad912541554df?pvs=204) | P0 | PDV-039, PDV-040 |
| 34 | [PDV-043 — Construir PDV e checkout no frontend](https://app.notion.com/p/4092c9a99917817f9b8aea419dd8b3c8?pvs=204) | P0 | PDV-015, PDV-019, PDV-021, PDV-034, PDV-035 |
| 35 | [PDV-044 — Construir tela de pagamento simulado](https://app.notion.com/p/4092c9a9991781c3b0acfcbe84904876?pvs=204) | P0 | PDV-037, PDV-040, PDV-043 |
| 36 | [PDV-014 — Gerenciar usuários e perfis no admin](https://app.notion.com/p/4092c9a99917811ab16ddb80b5b9981b?pvs=204) | P1 | PDV-011, PDV-013 |
| 37 | [PDV-018 — Modelar endereços e consulta regional](https://app.notion.com/p/4092c9a99917819f9ca8ca19a9d3c6fd?pvs=204) | P1 | PDV-016 |
| 38 | [PDV-023 — Implementar ordens e itens de compra](https://app.notion.com/p/4092c9a99917815396d3e55c85cd1cc8?pvs=204) | P1 | PDV-022 |
| 39 | [PDV-024 — Receber compras e dar entrada em estoque](https://app.notion.com/p/4092c9a9991781378553dd3eadd161f3?pvs=204) | P1 | PDV-023, PDV-027 |
| 40 | [PDV-025 — Criar telas produtos, fornecedores e OCs](https://app.notion.com/p/4092c9a9991781588bd4ed1844f3e750?pvs=204) | P1 | PDV-015, PDV-020, PDV-022, PDV-023 |
| 41 | [PDV-031 — Configurar alertas de estoque crítico](https://app.notion.com/p/4092c9a9991781e4bf03d78dcddfecb6?pvs=204) | P1 | PDV-026, PDV-027 |
| 42 | [PDV-033 — Criar telas de estoque e movimentações](https://app.notion.com/p/4092c9a99917816b8cacd076aa2dfc17?pvs=204) | P0 | PDV-025, PDV-027, PDV-029, PDV-031 |
| 43 | [PDV-032 — Sincronizar disponibilidade via eventos](https://app.notion.com/p/4092c9a99917812388e3ebbdc3388ab6?pvs=204) | P1 | PDV-006, PDV-026, PDV-038 |
| 44 | [PDV-042 — Consultar histórico e comprovantes paginados](https://app.notion.com/p/4092c9a9991781488499c8f19d384b36?pvs=204) | P1 | PDV-039, PDV-041 |
| 45 | [PDV-045 — Exibir rastreamento do status da venda](https://app.notion.com/p/4092c9a9991781e0a5b1c606d381a133?pvs=204) | P1 | PDV-036, PDV-039, PDV-040, PDV-044 |
| 46 | [PDV-046 — Gerar métricas por período/produto/região](https://app.notion.com/p/4092c9a999178122acf1f3d5ad4db10c?pvs=204) | P1 | PDV-039, PDV-018 |
| 47 | [PDV-047 — Exportar relatórios CSV e PDF](https://app.notion.com/p/4092c9a9991781b0aaa4e743b28fdd4f?pvs=204) | P1 | PDV-046 |
| 48 | [PDV-049 — Criar métricas, tracing e logs JSON centralizados](https://app.notion.com/p/4092c9a999178134a75eedd3c5807cb8?pvs=204) | P1 | PDV-005, PDV-006, PDV-038 |
| 49 | [PDV-050 — Criar painel de filas, retries e sincronização](https://app.notion.com/p/4092c9a999178138b32be01d2f8de9c5?pvs=204) | P1 | PDV-038, PDV-049 |
| 50 | [PDV-051 — Criar dashboard e relatórios responsivos](https://app.notion.com/p/4092c9a9991781c6bb0bea1ca1217853?pvs=204) | P1 | PDV-033, PDV-046, PDV-047, PDV-050 |
| 51 | [PDV-052 — Testar 100 consultas simultâneas ao estoque](https://app.notion.com/p/4092c9a99917815c9a48e3cabc260768?pvs=204) | P1 | PDV-021, PDV-027, PDV-033 |
| 52 | [PDV-053 — Validar p95 < 200 ms em catálogo](https://app.notion.com/p/4092c9a999178167905ecd1d2b0ea1d6?pvs=204) | P1 | PDV-021, PDV-049 |
| 53 | [PDV-054 — Testar circuit breaker, retries e falhas parciais](https://app.notion.com/p/4092c9a9991781aea73cd607f3f01087?pvs=204) | P1 | PDV-037, PDV-038, PDV-049 |
| 54 | [PDV-055 — Realizar testes de segurança, TLS e segredos](https://app.notion.com/p/4092c9a9991781eabdf3f150ce713e6e?pvs=204) | P1 | PDV-012, PDV-013, PDV-049 |
| 55 | [PDV-056 — Criar backup, restauração e migrações seguras](https://app.notion.com/p/4092c9a999178195b3a0d4219833ff80?pvs=204) | P1 | PDV-009, PDV-039 |
| 56 | [PDV-060 — Executar testes e2e e montar demonstração final](https://app.notion.com/p/4092c9a999178186bebbcf191a13a4d5?pvs=204) | P0 | PDV-041, PDV-044, PDV-045, PDV-050, PDV-051, PDV-052, PDV-053, PDV-054, PDV-055, PDV-056 |
| 57 | [PDV-048 — Criar snapshots e atualização programada](https://app.notion.com/p/4092c9a9991781f49d39f441ed0df59c?pvs=204) | P2 | PDV-046, PDV-009 |
| 58 | [PDV-057 — Laboratório de réplica de leitura e CQRS](https://app.notion.com/p/4092c9a9991781939e50e45077bb3f45?pvs=204) | P2 | PDV-046, PDV-048 |
| 59 | [PDV-058 — Laboratório de failover e disponibilidade](https://app.notion.com/p/4092c9a99917815ea8c4f410fefcf544?pvs=204) | P2 | PDV-049, PDV-056 |
| 60 | [PDV-059 — Rolling deploy, recuperação e ensaio de desastre](https://app.notion.com/p/4092c9a9991781c88a98ec68ded4200d?pvs=204) | P2 | PDV-008, PDV-055, PDV-056 |

## Critérios de implementação
API e worker independentes, Java 21/Spring Boot, Maven Wrapper, PostgreSQL/Flyway e RabbitMQ. Frontend React/TypeScript/Vite/Tailwind; confirmar scaffold frontend no refinamento. PostgreSQL compartilhado é simplificação acadêmica. Reserva não baixa saldo físico; timeout desconhecido precisa de reconciliação. Pagamento, estorno e comprovante são locais e fictícios, sem APIs financeiras/fiscais externas.

## Plano de validação
Unitários para cálculo/estados/RBAC; integração PostgreSQL/RabbitMQ real com concorrência e reentrega; ciclo Flyway migrate, restauração/rebuild controlado e replay; e2e feliz/recusa/timeout/cancelamento; carga declarada para p95 e 100 concorrentes. TLS, réplica, failover, uptime e zero downtime só são aceitos com ensaio real e ambiente registrado. Não há testes de produto executados neste ciclo.
