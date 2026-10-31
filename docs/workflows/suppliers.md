# Fornecedores — PDV-022

Refinamento confirmado pelo proprietário em 31/10/2026: ADMIN gerencia e associa fornecedores, ADMIN/OPERATOR consultam. supplier.read é obrigatório nas consultas; supplier.write e papel ADMIN em todas as mutações, inclusive vínculo. Seed development concede somente essas permissões, sem contas/dados comerciais. A cardinalidade máxima N:1 já aprovada na ADR-007 é implementada por products.supplier_id nullable, FK RESTRICT. Produto pode existir sem fornecedor.

| Rota | Contrato |
| --- | --- |
| POST /suppliers | {cnpj, legalName, tradeName?, contact?, phone?, email?, address?}; 201/Location |
| GET /suppliers | active opcional, page=0/size=20, máximo 100; {items,total,page,size}, legalName/id |
| GET /suppliers/{id} | Cadastro com active/version/timestamps |
| PUT /suppliers/{id} | {supplier: campos completos do POST, version} |
| PATCH /suppliers/{id}/status | {active: boolean, version} |
| DELETE /suppliers/{id}?version=N | Inativa logicamente, 204; nunca apaga cadastro/vínculos |
| GET /suppliers/{id}/history | Histórico paginado com ator/snapshots |
| GET /suppliers/{id}/products | Produtos efetivamente vinculados, paginação/ordem name/id |
| PUT /products/{id}/supplier | {supplierId: UUID ou null, version: versão do produto}; ProductView atualizado |

CNPJ com ou sem máscara convencional, numérico/alfanumérico, canônico uppercase de 14 posições e dígitos verificadores locais; UNIQUE no banco. Validação não comprova existência/situação cadastral e não consulta Receita ou outro serviço. legalName obrigatório até 200; tradeName/contact até 200; phone até 32; email opcional válido até 320; address textual até 1000. Campos opcionais vazios viram null. Nenhuma política fiscal/financeira ou consulta de endereço externo.

Fornecedor nasce ativo. Nova associação/troca exige fornecedor ativo; inativar mantém vínculos. Repetir vínculo já existente é no-op com versão atual, mesmo fornecedor inativo; null desvincula. ProductView ganha supplierId, preservado por edições genéricas do produto. PUT de produto não aceita supplierId; usar o endpoint dedicado. Isso evita desvinculação acidental ao editar preço/nome. Erros: 400 entrada, 401 autenticação, 403 acesso, 404 referência, 409 cnpj_conflict/version_conflict/supplier_inactive, 503 persistência indisponível.

V9 cria suppliers/supplier_changes e coluna/índice de vínculo no produto; registros anteriores ficam sem fornecedor. JPA ddl-auto=validate, sem auto-DDL. Lock fornecedor novo→produto serializa vínculo/inativação; @Version protege produto e fornecedor. Histórico do fornecedor registra criação/edição/estado; histórico do produto registra SUPPLIER_CHANGED com antes/depois e ator autenticado. Mutação/versão/histórico na mesma transação JPA/JDBC; falha reverte tudo. Não há exclusão física, cascata de negócio ou desvinculação automática ao inativar.

Verificação em PostgreSQL real: [spec](../../sdd/features/PDV-022-fornecedores-vinculos.md), [migrations](database-migrations.md), [catálogo](catalog.md). Dados de teste sintéticos em banco próprio. Sem ordens/recebimento de compra, entrada de estoque, frontend ou entidades financeiras. Esses contratos não afirmam entrega das tarefas futuras.
