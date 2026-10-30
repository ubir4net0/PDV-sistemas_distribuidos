# ADR-003 persistência e transações locais

Data: 30/10/2026. Tarefa: [PDV-003](../../sdd/features/PDV-003-arquitetura-adrs.md). Estado: PostgreSQL/Flyway e transações locais declarados no pacote; mecanismo de concorrência e schema pendentes.

## Contexto e fontes

Especificação v1.0, seções 2, 5, 7 e 8: PostgreSQL, Flyway, JPA/Hibernate para CRUD e SQL transacional explícito nas operações críticas de saldo. RNF03 trata versionamento; RNF08 idempotência; RNF10 consistência local. DEC-02/03/04/05 ainda impedem schema definitivo.

## Decisão registrada

PostgreSQL é a fonte de verdade. Criar venda, itens, reserva, registro idempotente e intenção de publicação deve formar uma unidade transacional local no checkout. Se a unidade falhar, nenhum efeito parcial deve ficar confirmado. Nenhuma chamada ao broker ou espera pelo pagamento deve manter essa transação aberta.

Reserva não baixa estoque físico. Disponível = físico - reservado ativo. Confirmar uma reserva deve baixar físico e retirar a reserva ativa atomicamente, uma única vez. Liberar reserva não devolve físico, pois a reserva ainda não o baixou. Devolver uma venda já baixada é outro efeito idempotente.

A leitura exibida no catálogo não garante saldo no checkout. A operação crítica precisa revalidar disponibilidade dentro da proteção concorrente. Validar saldo antes da transação e gravar depois permite sobre-reserva.

Locks de linha e atualizações condicionais são opções para proteger conflitos; a escolha, ordem de aquisição, isolamento e política de retry devem ser demonstrados em testes PostgreSQL na tarefa de estoque. ETag/versionamento auxilia conflitos de escrita, mas não dispensa a invariável transacional de saldo. Ver [controle de locks do PostgreSQL](https://www.postgresql.org/docs/17/explicit-locking.html).

## Evolução do banco

Flyway versionará schema; não usar atualização automática de ORM como mecanismo de evolução. PDV-009 definirá migrations ordenadas, restrições e estratégia de compatibilidade. A proposta de entidades técnicas do [DER de confiabilidade](../diagrams/svg/06-der-confiabilidade.svg) não é aprovação de DEC-04.

Toda mudança de persistência exige PostgreSQL real: migrate, restauração ou rebuild controlado de banco descartável e replay. Não presumir que Flyway gere reversão automática; [undo depende de scripts próprios](https://documentation.red-gate.com/flyway/flyway-concepts/migrations/undo-migrations). A recuperação deve verificar dados e invariantes, além da versão de schema. Não apagar banco existente por conveniência.

## Justificativa e alternativas

JPA atende operações comuns; saldo crítico precisa explicitar condições e fronteiras para testes de concorrência. Usar memória, banco fake ou somente testes de repositório não demonstra a disputa real. Uma transação envolvendo banco e broker foi descartada como promessa: o pacote exige intenção durável e publicação posterior, conforme ADR-005.

## Consequências e pendências

Transações devem ser curtas; conflitos e deadlocks precisam de tratamento limitado e observável. Política de lote, recebimento parcial, precisão/escala, TTLs e restrições exatas serão refinadas, sem inventar valores neste registro. Indisponibilidade do PostgreSQL impede confirmar mutações duráveis.

## Validação futura

Com saldo insuficiente para duas compras concorrentes, no máximo a quantidade disponível pode ser reservada. Repetir confirmação/liberação/devolução não pode duplicar movimentos. Uma falha antes do commit reverte toda a unidade local. Migrations devem passar pelo ciclo completo de recuperação e replay. São testes futuros, não evidências da PDV-003.
