# PDV-020 — Categorias e produtos

## Estado e fonte

Leitura viva em 31/10/2026: https://app.notion.com/p/4092c9a99917815c8a84c7a337aff5c0. Backlog; responsável/prazo não confirmados. RF-E01, baseline textual v1, seções 7 e 9. Aceite original: “SKU único, preço Decimal, categoria, cadastro/edição/inativação e testes de integridade.”

## Contexto, dependências e Definition of Ready

PDV-009 e PDV-013 consultadas ao vivo, ambas Em validação. Flyway V1–V6 e autenticação/guardas estão implementados; o aceite dessas predecessoras não é inferido. Main sincronizada; PostgreSQL/RabbitMQ PDV saudáveis e Migrate V6 aprovado nesta sessão. Nenhum serviço Amor e Vida será alterado.

A DEC-06 deixava permissões e revisão de produto em aberto. O proprietário confirmou explicitamente este refinamento nesta sessão (“Confirmar esse refinamento”), incluindo fluxo, permissões, preservação de vínculos/histórico e representação Decimal. A ADR-007 já aprovou a baseline textual e o máximo N:1 de fornecedor; não reabrir essas escolhas.

## Refinamento confirmado pelo proprietário

- ADMIN cadastra/edita categorias e produtos e altera seus estados; ADMIN e OPERATOR consultam, inclusive cadastros inativos. Sem exclusão física; alterações auditadas com ator e snapshots.
- Produto nasce CADASTRADO e segue somente CADASTRADO → EM_REVISAO → ATIVO ↔ INATIVO, por ação explícita de ADMIN. Edição de campos conserva o estado; não há aprovação automática. Conflitos de versão são recusados.
- Categoria nasce ativa e admite ativação/inativação administrativa. Associação nova/troca exige categoria ativa; inativação conserva vínculos existentes. Categoria obrigatória; fornecedor opcional será implementado na PDV-022.
- SKU canônico em maiúsculas, aparado e único. Preço/custo em Decimal de até duas casas, não negativos, sem arredondamento silencioso. Unidade é texto informado; estoque mínimo não negativo. Cadastro não permite escrever saldo físico/reservas, pertencentes às tarefas de estoque.

## Fronteiras e contratos planejados

CRUD JPA com ddl-auto=validate; migration V7 de categorias/produtos/histórico, FKs RESTRICT, UNIQUE SKU, CHECKs e versionamento. API /categories e /products: POST, GET por ID, PUT versionado, PATCH de estado versionado, consulta de histórico. Permissões catalog.read para ambos e catalog.write para ADMIN, somadas às guardas de papel. Erros 400/401/403/404/409/503 constantes, sem dados pessoais em logs. Paginação/busca ficam na PDV-021; fornecedor na PDV-022. Sem frontend, checkout, movimentos, compras ou eventos de negócio.

## Critérios e plano de verificação

Preservar aceite original e verificar: SKU concorrente com um vencedor, Decimal exato, FK/CHECKs, autorização e revogação, transições recusadas, versão obsoleta, histórico atômico e rollback em falha de auditoria. PostgreSQL real, upgrade V6→V7 preservando dados, rebuild/replay próprios, regressões HTTP/JPA/segurança e cobertura explícita de todos os mappings. Comandos: scripts/api.ps1 -Action Verify, Migrate, Validate e Info; node scripts/verify-planning.mjs; git diff --check. Autorrevisão, commit e CI próprios antes da próxima tarefa.

## Evidências, revisão e aceite

Executado nesta sessão: preflight main/Migrate V6; Verify Java 21/PostgreSQL 16.15 SUCCESS em 6m02s, 21 unitários + 128 IT (149), zero falhas/erros/skips, Checkstyle zero. Treze HTTP e dois IT novos de catálogo cobrem SKU canônico/concorrência, Decimal, estados, versões, perfis/permissões/revogação, vínculos com categoria inativa, FK/CHECKs, ator/snapshots e rollback real em falha de auditoria. Upgrade V6→V7 conserva cliente existente; rebuild/replay próprios e regressões completas passaram. Banco descartável removido.

Migrate/Validate/Info V7 SUCCESS; JAR contém somente V1–V7, sem fixtures V9000. Planejamento preserva 60 IDs/aceites/dependências e hashes; 51 links locais/UTF-8, diff e staging/credenciais conferidos. Avisos de metadata 401 de feeds opcionais do Flyway não impediram resolução/build; nenhuma credencial foi alterada. Autorrevisão confirmou guardas de todos os mappings, locks categoria→produto, SQL parametrizado e histórico na mesma transação JPA/JDBC. Sem alterações em serviços Amor e Vida.

Commit próprio em main, sem PR; hash/CI registrados na tarefa viva após publicação. Confirmação do refinamento não é aceite final da entrega. Em validação aguardará aceite; sem busca paginada, fornecedor, saldo físico, frontend ou checkout neste incremento.
