# PDV-022 — Fornecedores e vínculos de produtos

## Estado, fonte e aceite original

Leitura viva em 31/10/2026: https://app.notion.com/p/4092c9a9991781cfa8e2d1d1c9f61895. Backlog; responsável/prazo não confirmados. RF-C03. Aceite original: “CNPJ único, CRUD e associação produto/fornecedor; definir cardinalidade antes da migração.” Baseline textual v1, seções 7 e 10.

## Dependências e Definition of Ready

PDV-009/013 entregam Flyway/guardas e continuam Em validação, sem aceite inferido. PDV-020 entregou produtos em c23b634 com 149 testes/CI SUCCESS. PDV-021 é anterior neste lote por ordem de execução, sem alterar as dependências canônicas: c85afca, 161 testes locais, CI Backend quality 41170018901/Planning integrity 41170018935 SUCCESS, p95 CI 10,036ms/91.624 requisições/zero erros. Bloqueio DEC-03 do card é histórico: a ADR-007 aprovada já definiu máximo N:1; removido somente nesta tarefa em execução. Obrigatoriedade do vínculo e permissões foram confirmadas nesta sessão no refinamento abaixo; demais detalhes da DEC-06 continuam fora do escopo. Preflight: main sincronizada, serviços PDV saudáveis e Migrate V8 SUCCESS.

## Refinamento confirmado pelo proprietário

O proprietário respondeu “Confirmar esse refinamento” nesta sessão. ADMIN cadastra, consulta, edita, ativa/inativa fornecedores e vincula/desvincula produtos; OPERATOR apenas consulta. Sem exclusão física; DELETE lógico equivale à inativação, preservando histórico e vínculos. Fornecedor nasce ativo. Produto pode ficar sem fornecedor; quando associado, tem exatamente um. Associação nova/troca exige fornecedor ativo, inativação não remove vínculos existentes.

CNPJ canônico único, com validação local de dígitos numérico/alfanumérico já existente, sem consulta de cadastro externo. Razão social obrigatória; nome fantasia, contato, telefone, email e endereço textuais opcionais. Endereço de fornecedor não reutiliza endereços pessoais de clientes. Sem dados reais, API externa, compras, preços negociados ou regras fiscais.

## Contratos, persistência e invariantes planejados

CRUD JPA /suppliers com leitura/listagem paginada por legalName/id (active opcional), POST, PUT versionado, PATCH de ativo e DELETE lógico com version na query; GET de histórico. Entradas {cnpj, legalName, tradeName?, contact?, phone?, email?, address?}, PUT {supplier: campos completos, version}, PATCH {active, version}. POST nasce ativo e retorna 201/Location; DELETE equivale a inativar e retorna 204, sem apagar vínculos/dados. GET /suppliers/{id}/products consulta vínculos reais paginados.

PUT /products/{id}/supplier recebe supplierId nullable e version, exige ADMIN e supplier.write; leitura exige supplier.read para ambos os papéis. ProductView ganha supplierId opcional, sem permitir alteração desse campo pelo PUT genérico de produto. FK products.supplier_id RESTRICT, no máximo um vínculo pela própria coluna; V9 sequencial após índices da PDV-021, existentes ficam null. Histórico próprio de fornecedor e histórico de vínculo no produto são atômicos com ator, snapshots e versões. Lock fornecedor novo→produto serializa vínculo versus inativação; desvincular não exige lock de fornecedor. Preservar vínculo já existente com fornecedor inativo é no-op, não nova associação. Erros constantes 400/401/403/404/409/503; não aceitar ator informado pelo cliente.

## Critérios e plano de verificação

CNPJ válido/inválido/duplicado, unicidade concorrente, autorização/revogação, campos/versionamento, inativação preservando vínculos, fornecedor inativo recusado em nova associação, troca/desvinculação, FK/concorrência, audit rollback, upgrade com produtos existentes sem fornecedor e rebuild/replay em PostgreSQL real. Verify, Migrate/Validate/Info, planejamento/links/diff/segredos; autorevisão, commit e CI próprios.

## Evidências, revisão e aceite

Verify Java 21/PostgreSQL 16.15 SUCCESS em 6m25s: **21 unitários + 155 IT (176)**, zero falhas/erros/skips e Checkstyle zero. Treze HTTP novos de fornecedor e dois IT de persistência cobrem CRUD/CNPJ numérico/alfanumérico/duplicidade, autorização/revogação, histórico/ator, versões, inativação e vínculo preservado, troca/desvinculação, concorrência CNPJ/vínculo/inativação, FK/RESTRICT, entrada inválida e rollback real de fornecedor/vínculo quando suas auditorias falham. Upgrade V8→V9 preserva produto/Decimal com supplier_id null e rebuild/replay passa. Regressões completas, incluindo carga: 72.138 requisições/zero erros, p95 local 16,561ms e p99 24,136ms. Banco próprio removido.

Migrate/Validate/Info V9 SUCCESS; JAR apenas V1–V9, sem fixtures. Smoke JAR development com TTL técnico 900: readiness UP, coleções fornecedores/produtos/categorias anônimas 401, zero contas/clientes/produtos/fornecedores e zero bancos Verify remanescentes; grants supplier.read para ambos e write somente ADMIN. Container próprio encerrado/removido com saída normal, serviços restantes preservados.

Planejamento/fontes/60 IDs/aceites/dependências, 62 links locais/UTF-8 e staging/credenciais aprovados. Documentação de arquitetura/módulos atualizada para distinguir cadastros reais de estoque/compras/Saga pendentes. Autorrevisão confirma locks fornecedor→produto, versão, FK nullable N:1, histórico na mesma transação e guardas por papel/permissão em todos os novos mappings. Commit/CI próprios serão ligados na tarefa viva após publicação. Sem PR ou aceite implícito; Em validação aguardará o proprietário. Cardinalidade aprovada não foi reaberta. Sem compras/recebimento/estoque/frontend ou políticas financeiras novas.
