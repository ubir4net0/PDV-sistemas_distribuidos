# PDV-029 — Reserva atômica com prazo

## Estado, fonte e aceite

Leitura viva em 01/11/2026: https://app.notion.com/p/4092c9a99917816da01fc0dd3c880051. RF-V02. Responsável/prazo não informados. Aceite original: “Reserva atômica e TTL; consulta evita overselling; renovação e expiração com testes.” Dependências PDV-026/028 entregues tecnicamente em c1c8ca7/2c4e206, Em validação; isso não aceita as predecessoras. Main limpa/sincronizada e PostgreSQL/RabbitMQ próprios saudáveis; Migrate prévio V11 SUCCESS.

## Refinamento confirmado e fronteiras

Proprietário confirmou ADMIN/OPERATOR criando e renovando suas reservas em lote tudo-ou-nada, somente produtos ATIVOS, inteiros positivos e sem baixar físico. TTL obrigatório por configuração, demo 900s; renovar antes de vencer por um TTL contado da renovação. Worker de expiração em PDV-030, 5s/até100 reservas. Barreira transacional retém reservas antes de iniciar pagamento e durante resultado aprovado/incerto; integração checkout/Saga futura. Carrinho PDV-034 não reserva. Sem venda/pagamento fictício implementados antecipadamente.

## Contrato e invariantes

POST /reservations {requestId,items:[{productId,quantity,version}]}: 1–100 produtos distintos, UUIDs ordenados, versão do saldo explícita, 201 Reservation/Idempotent-Replay. GET /reservations paginado e GET /reservations/{id} apenas do ator. POST /reservations/{id}/renew {requestId,version}: versão da reserva, 200. Permissões reservation.read/write e papel ADMIN/OPERATOR; propriedade filtrada no SQL, recurso alheio404. 400 inválido; 409 versão/saldo/estado/expiração/chave em conflito; 401/403/404/503 seguros.

Reserva é um agregado técnico com itens imutáveis, dono, expiresAt, status ACTIVE/EXPIRED (CONFIRMED/RELEASED reservados para tarefas próprias), paymentProtected e versão. Sem venda_id fabricado: checkout futuro deverá vincular o agregado à venda em migration própria, na mesma transação. Chave requestId por ator, fingerprint canônico e resposta persistida imutável; replay retorna snapshot sem reaplicar. Renovação não revive vencida/protegida e revalida produtos ativos. Prazo e avaliação de vencimento usam relógio PostgreSQL.

V12 incrementa somente reserved/versão do saldo por UPDATE condicional a disponível/versão, mantendo physical. Trilha imutável das reservas inclui snapshots de saldos. Produtos são bloqueados em ordem antes dos saldos; renovação/barreira/expiração serializam na reserva. Funções PostgreSQL de proteção e expiração compartilham essa guarda com o worker futuro. Expiração libera todos os itens uma vez, só ACTIVE vencida/desprotegida; falha em item/auditoria reverte tudo. Reserva vencida ainda conta no reservado até liberação segura, evitando overselling.

## Verificação planejada

HTTP e PostgreSQL reais: autorização/propriedade/revogação, validade/frações, replay/renovação, versões/ETags, concorrência pelo último disponível, lote rollback, corrida movimento/reserva e proteção/expiração, guardas/auditoria/limites. Upgrade V11→V12 preserva dados, rebuild/replay em namespaces próprios, Verify completo/lint/JAR, carga regressiva e planejamento/links/segredos. Commit/CI próprios em main. Sem undo Flyway inventado.

## Evidências e aceite

Spec criada antes do código; execução e resultados serão registrados. Entrega técnica ficará Em validação, sem aceite inferido.

Referências técnicas consultadas: [locks PostgreSQL16](https://www.postgresql.org/docs/16/explicit-locking.html) e [READ COMMITTED/reavaliação de UPDATE](https://www.postgresql.org/docs/16/transaction-iso.html). Garantias desta entrega são demonstradas pelos testes locais, sem presumir consistência distribuída.

## Verificação executada e autorevisão

Verify local SUCCESS em 11m25s: 21 unitários+213 integrações=234 testes, zero falhas/erros/skips e lint zero. Treze novos HTTP e um teste de persistência confirmaram produtos ATIVOS/propriedade/permissões, TTL900s no relógio PostgreSQL, renovação/replay, frações inválidas, dois escritores/último disponível, rollback de lote/auditoria, imutabilidade, físico preservado, expiração única/barreira e upgrade V11→V12/rebuild/replay. PostgreSQL16.15 real, Java21.0.12.1, banco Verify próprio removido. JAR com12 migrations/zero fixtures.

Carga regressiva: 61.993 requests/zero erros, p95 19,674445ms, 10.000 produtos/10clientes/10s aquecimento+60s medição; laboratório, sem inferir produção. Planejamento60IDs/aceites/dependências/hash fontes PASS. Autorevisão preserva scripts aplicados, guards e fingerprints de movimentos; status da reserva/proteção não implementa checkout/pagamento/Saga. CI e commit serão vinculados na tarefa viva após publicação, sem aceite antecipado.

Migrate e Validate locais V12 SUCCESS; nove documentos UTF-8/84 links locais válidos, sintaxe PowerShell/diff/scan de credenciais PASS. Sem falha de produto nesta execução.
