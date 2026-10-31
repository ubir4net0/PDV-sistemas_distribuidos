# Catálogo — contrato da PDV-020

Refinamento confirmado pelo proprietário em 31/10/2026: ADMIN gerencia categorias/produtos, ADMIN e OPERATOR consultam com catalog.read; mutações exigem ADMIN e catalog.write. Concessão acidental de escrita a OPERATOR não libera mutações. Seed development provisiona somente papéis/permissões, sem contas ou dados comerciais; produção exige provisionamento explícito.

| Rota | Entrada e resultado |
| --- | --- |
| POST /categories | {name, description?}; 201 e Location |
| GET /categories/{id} | Categoria com active, version e timestamps |
| PUT /categories/{id} | {category: {name, description?}, version} |
| PATCH /categories/{id}/status | {active: boolean, version} |
| POST /products | {categoryId, sku, name, description?, salePrice, cost, unit, minimumStock}; 201 e Location |
| GET /products/{id} | Produto com status, version e timestamps |
| PUT /products/{id} | {product: campos completos do POST, version} |
| PATCH /products/{id}/status | {status, version} |
| GET /categories/{id}/history | Histórico paginado, page=0 e size=20, máximo 100 |
| GET /products/{id}/history | Mesmo envelope {items,total,page,size} |

Categoria obrigatória e ativa para nova associação; inativar conserva vínculos e permite editar produto mantendo a mesma categoria. Produto nasce CADASTRADO; ADMIN faz CADASTRADO → EM_REVISAO → ATIVO ↔ INATIVO. Edição conserva estado; saltos/retrocessos são 409 invalid_transition. Repetir o mesmo estado com versão atual não cria mutação; versão antiga é 409 version_conflict.

SKU aparado/maiúsculo ASCII, 1–64 caracteres (letras, números, ponto, hífen e underscore; começa alfanumérico), UNIQUE PostgreSQL. Nome até 200, descrição opcional até 2000, unidade livre até 32, estoque mínimo inteiro não negativo. salePrice/cost Decimal NUMERIC(19,2), não negativos; entrada com mais de duas casas ou precisão excessiva é 400, sem arredondamento automático. Limites técnicos não escolhem moeda/preços comerciais. Saldo físico/reservas não são campos de escrita deste cadastro.

V7 cria categories/products/catalog_changes. JPA valida schema, sem auto-DDL. Locks categoria→produto protegem associação versus inativação; versão impede sobrescrita. Histórico inclui criação, edição e estados, ator autenticado e snapshots antes/depois na mesma transação. Falha de histórico reverte dados/versão. FKs RESTRICT preservam referências; sem exclusão física ou endpoint DELETE neste incremento.

400 entrada inválida, 401 sem autenticação, 403 sem papel/permissão, 404 ausente, 409 SKU/versão/transição/categoria inativa, 503 indisponibilidade persistente. Respostas não expõem SQL ou valores internos. Busca paginada entra na PDV-021; fornecedor opcional na PDV-022. Sem frontend, estoque, checkout ou eventos de vendas.

Operação e verificação: [migrations](database-migrations.md), [identidade](identity-security.md), [spec PDV-020](../../sdd/features/PDV-020-categorias-produtos.md). Verify usa PostgreSQL real em banco UUID descartável, testes HTTP/constraints/concorrência/rollback e rebuild/replay.
