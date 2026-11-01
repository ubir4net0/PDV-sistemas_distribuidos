# Estoque — PDV-026/027/028

Refinamento explicitamente confirmado pelo proprietário em 31/10/2026: quantidades inteiras, saldos iniciais zero, ADMIN movimenta e ADMIN/OPERATOR consultam. Na PDV-026 só há leitura e abertura técnica. Lote tudo-ou-nada, movimentos/ajuste absoluto e ETags pertencem aos dois incrementos seguintes. Reserva/expiração/baixa de venda são tarefas próprias, sem implementação presumida.

| Rota | Contrato |
| --- | --- |
| GET /inventory | page=0,size=20 (1–100), ordenação productId; {items,total,page,size} |
| GET /inventory/{productId} | {productId,physical,reserved,available,version,createdAt,updatedAt} |
| GET /inventory/{productId}/opening | {productId,source,physical,reserved,version,recordedAt} |

As três rotas exigem inventory.read e ADMIN/OPERATOR, sem permissão inferida só por autenticação. 400 entrada/paginação inválida, 401 sem autenticação, 403 sem acesso, 404 produto/saldo ausente e 503 persistência indisponível. Saldo tem versão independente do cadastro; editar nome/preço/status/fornecedor não muda sua versão. Não há endpoint para escrever físico/reservado diretamente.

## Movimentações — PDV-027

Somente ADMIN com inventory.write movimenta; concessão acidental a OPERATOR não libera escrita. Qualquer estado do produto admite correção de estoque, sem autorizar venda. Motivo por item obrigatório (até500, aparado), ator vem da autenticação; reservado não é entrada do cliente. Quantidades inteiras JSON, frações rejeitadas sem truncar. ENTRY/EXIT exigem quantidade>0; ADJUST recebe novo físico absoluto>=0 e >=reservado. Ajuste sem variação ainda registra conferência/versionamento; mesma requestId nunca aplica novamente.

| Rota | Contrato |
| --- | --- |
| POST /inventory/{productId}/movements | {requestId: UUID,type: ENTRY/EXIT/ADJUST,quantity,reason,version} |
| POST /inventory/movements/batches | {requestId: UUID,items:[{productId,type,quantity,reason,version}]},1–100 itens |
| GET /inventory/{productId}/movements | Leitura inventory.read, Page por version_after/id, size<=100 |

POST retorna201 com {id,requestId,items,createdAt}; itens contêm ID de movimento/operação, produto/tipo/quantidade/delta/motivo/ator, saldo/versão antes e balance após. Lote processa UUIDs crescentes, rejeita produto repetido e é tudo-ou-nada, incluindo versões, header e trilha. Falha em qualquer item ou INSERT do histórico reverte todos os efeitos. UPDATE condicional protege versão, disponível, reservado e limites BIGINT; não depende só de consulta prévia. 409 version_conflict, insufficient_stock, stock_reserved, quantity_overflow ou version_limit, sem efeito; 400 entrada inválida,404 saldo ausente,503 indisponibilidade de persistência.

requestId é escopado por ator. Advisory lock transacional PostgreSQL serializa essa identidade, e UNIQUE reforça a proteção. SHA256 do JSON canônico dos itens (campos productId,type,quantity,reason,version, ordem de UUID) detecta uso da chave com conteúdo diferente:409 request_conflict. Repetição com o mesmo conteúdo retorna o corpo original/201 e Idempotent-Replay:true, inclusive após movimentos posteriores; o balance retornado é snapshot do resultado original, não leitura atual. Novas operações retornam Idempotent-Replay:false. Falha não consome a chave; retry depois da falha pode executar. Sem TTL/exclusão automática da trilha ou framework genérico de checkout.

V11 cria inventory_operations/inventory_movements com resultados/snapshots, FKs e constraints de ator consistente, delta/tipo e uma versão por produto. Header e movimentos são inseridos uma vez e imutáveis por triggers de UPDATE/DELETE. Não há rotas para editar/apagar histórico. [Spec PDV-027](../../sdd/features/PDV-027-movimentos-atomicos.md). ETags são o incremento seguinte; reservas, recebimento de compra, baixa de venda, eventos e Saga permanecem nas tarefas próprias.

## Revisões HTTP — PDV-028

GET /inventory/{productId} entrega ETag forte "inventory-UUID-vVERSION", Cache-Control:no-store e Balance. PATCH na mesma URI aceita {requestId,type,quantity,reason,version?}; exige ADMIN/inventory.write e revisão explícita do corpo ou tag concreta em If-Match. PATCH retorna200/Balance e nova ETag. Versão pertence ao saldo; edição de metadados do produto não a altera.

If-Match é comparado fortemente, com suporte a listas, tags opacas com vírgulas e wildcard. Tags fracas não correspondem; nenhuma tag atual resulta412 precondition_failed. Sintaxe inválida ou mais de4096 caracteres/32 tags resulta400. Wildcard significa existência, sem fixar revisão: PATCH exige version no corpo nesse caso; sem revisão explícita428 version_required. Com header e corpo, precondição vem primeiro: header obsoleto412, corpo version obsoleto409. GET também respeita If-Match; ausência de saldo continua404. UPDATE condicional revalida a versão diante de disputa; não há retry automático.

POSTs legados de movimentos/lote preservam201, versões por item e fingerprint da PDV-027; enviar If-Match nesses endpoints resulta400, pois a ETag representa o saldo, não a coleção de movimentos. No lote, qualquer versão inválida reverte tudo. Não usar wildcard para substituir controle de revisão.

PATCH tem fingerprint próprio incluindo condição canônica e versão de corpo original nullable, sem alterar chaves legadas. Repetição comprovada por requestId/ator/conteúdo retorna o balance original/200 e Idempotent-Replay:true, mesmo com tag antiga, sem reaplicar. Não envia ETag nesse replay: consultar GET para obter estado atual. Chave com conteúdo diferente409 request_conflict. Nunca inferir duplicidade pela simples igualdade do saldo. [Spec/metodologia e RFC](../../sdd/features/PDV-028-concorrencia-etags.md). Sem novo DDL, reservas, sincronização, frontend ou Saga neste incremento.

V10 cria inventory_balances e inventory_openings. BIGINT/Long inteiros, physical>=0, reserved>=0 e reserved<=physical, versão não negativa, FK RESTRICT. available é calculado no SELECT, nunca armazenado como cópia. Mesmo no limite BIGINT, físico-reservado permanece não negativo e representável. Não existe arredondamento ou quantidade fracionária no domínio aprovado.

Migração inicializa os produtos existentes com zero; trigger AFTER INSERT inicializa novos produtos (JPA/SQL) e abertura ZERO_INITIALIZATION na mesma transação. Registro técnico zero não afirma estoque legado, contagem física ou autoria humana. O ator da criação HTTP está no histórico de catálogo; migração não fabrica usuário. A abertura original permanece zero após movimentações futuras. Trigger rejeita UPDATE/DELETE da abertura; não há rotas de edição/exclusão. Falhar a abertura reverte produto/saldo e impede criação parcial.

SQL explícito é apropriado ao domínio crítico de saldo conforme a arquitetura; CRUD de produtos continua JPA, com schema validado e sem auto-DDL. Leituras aqui consultam estado transacional; não são projeção eventual de vitrine, AMQP, reserva ou checkout.

[Spec PDV-026](../../sdd/features/PDV-026-saldos-estoque.md) registra upgrade V9→V10, invariantes/HTTP/rollback/guardas e rebuild/replay. [Verify/migrations](database-migrations.md) usa PostgreSQL real em banco descartável. Fixtures de teardown são exclusivas de testes, limitadas a UUIDs próprios em pdv_verify_UUID ou pdv_ci/CI=true; transação SET LOCAL session_replication_role permite remover trilha imutável apenas para limpeza, restaura guardas no fim e não é empacotada no JAR. Asserções de imutabilidade/rollback executam com constraints/trigger ativos; produção não tem bypass de fixture.
