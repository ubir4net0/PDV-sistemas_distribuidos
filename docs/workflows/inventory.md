# Estoque — PDV-026

Refinamento explicitamente confirmado pelo proprietário em 31/10/2026: quantidades inteiras, saldos iniciais zero, ADMIN movimenta e ADMIN/OPERATOR consultam. Na PDV-026 só há leitura e abertura técnica. Lote tudo-ou-nada, movimentos/ajuste absoluto e ETags pertencem aos dois incrementos seguintes. Reserva/expiração/baixa de venda são tarefas próprias, sem implementação presumida.

| Rota | Contrato |
| --- | --- |
| GET /inventory | page=0,size=20 (1–100), ordenação productId; {items,total,page,size} |
| GET /inventory/{productId} | {productId,physical,reserved,available,version,createdAt,updatedAt} |
| GET /inventory/{productId}/opening | {productId,source,physical,reserved,version,recordedAt} |

As três rotas exigem inventory.read e ADMIN/OPERATOR, sem permissão inferida só por autenticação. 400 entrada/paginação inválida, 401 sem autenticação, 403 sem acesso, 404 produto/saldo ausente e 503 persistência indisponível. Saldo tem versão independente do cadastro; editar nome/preço/status/fornecedor não muda sua versão. Não há endpoint para escrever físico/reservado diretamente.

V10 cria inventory_balances e inventory_openings. BIGINT/Long inteiros, physical>=0, reserved>=0 e reserved<=physical, versão não negativa, FK RESTRICT. available é calculado no SELECT, nunca armazenado como cópia. Mesmo no limite BIGINT, físico-reservado permanece não negativo e representável. Não existe arredondamento ou quantidade fracionária no domínio aprovado.

Migração inicializa os produtos existentes com zero; trigger AFTER INSERT inicializa novos produtos (JPA/SQL) e abertura ZERO_INITIALIZATION na mesma transação. Registro técnico zero não afirma estoque legado, contagem física ou autoria humana. O ator da criação HTTP está no histórico de catálogo; migração não fabrica usuário. A abertura original permanece zero após movimentações futuras. Trigger rejeita UPDATE/DELETE da abertura; não há rotas de edição/exclusão. Falhar a abertura reverte produto/saldo e impede criação parcial.

SQL explícito é apropriado ao domínio crítico de saldo conforme a arquitetura; CRUD de produtos continua JPA, com schema validado e sem auto-DDL. Leituras aqui consultam estado transacional; não são projeção eventual de vitrine, AMQP, reserva ou checkout.

[Spec PDV-026](../../sdd/features/PDV-026-saldos-estoque.md) registra upgrade V9→V10, invariantes/HTTP/rollback/guardas e rebuild/replay. [Verify/migrations](database-migrations.md) usa PostgreSQL real em banco descartável. Fixtures de teardown são exclusivas de testes, limitadas a UUIDs próprios em pdv_verify_UUID ou pdv_ci/CI=true; transação SET LOCAL session_replication_role permite remover trilha imutável apenas para limpeza, restaura guardas no fim e não é empacotada no JAR. Asserções de imutabilidade/rollback executam com constraints/trigger ativos; produção não tem bypass de fixture.
