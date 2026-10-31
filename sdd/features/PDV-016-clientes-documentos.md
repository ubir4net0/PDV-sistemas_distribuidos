# PDV-016 — Cadastro de clientes e documento único

## Fonte, aceite e dependências

Notion: https://app.notion.com/p/4092c9a999178163b2f4fab53e8aed3d, lida nesta execução. RF-C01: cadastrar clientes com CPF/CNPJ único; aceite original: validação de documento e unicidade no banco; testes para duplicados e concorrência. PDV-009/013 fornecem Flyway/JDBC e autenticação/RBAC com CI aprovada; permanecem Em validação, sem aceite implícito. Responsável/prazo não confirmados. Preflight main sincronizada, PostgreSQL/RabbitMQ PDV saudáveis e Migrate V3 SUCCESS.

## Contrato e fronteiras

V4 customers: UUID, person_type PF/PJ, document canônico único, name, phone/email/locality opcionais, active e timestamps/version. POST /customers retorna 201 com Location e cliente criado; CPF/CNPJ validado por formato e dígitos verificadores. Não consulta Receita nem comprova situação cadastral. Normalização aceita formato puro ou máscara convencional, preserva letras de CNPJ e usa uppercase ASCII; rejeita caracteres estranhos. CNPJ numérico e alfanumérico conforme documentação técnica da Receita Federal, não restringir o formato vigente a números. 400 entrada inválida, 409 duplicidade segura, 401/403 de acesso; sem eco de dados em erros/logs.

CRUD JPA/EntityManager conforme baseline/ADR-003, com Hibernate gerenciado pelo BOM existente, ddl-auto=validate e open-in-view=false; Flyway continua dono do schema. Consultas JDBC parametrizadas permanecem para identidade e testes de invariantes; UNIQUE no PostgreSQL decide a disputa concorrente, sem check-then-insert. Sem frontend, alteração de clientes, endereços ou checkout nesta tarefa. As próximas tarefas 017/018 terão specs/commits próprios. Recorte explicitamente aprovado pelo proprietário: operador e administrador cadastram/consultam/editam clientes e endereços; somente administrador ativa/inativa; sem exclusão física, histórico e no máximo um endereço principal. Nesta task POST exige ADMIN ou OPERATOR e customer.create; seed apenas development para ambos. Checkout de inativos fica na tarefa própria.

## Verificações e recuperação

Unitários: CPF/CNPJ numérico/alfanumérico, máscaras, DV inválido, repetidos, caracteres inválidos e tipo incompatível. PostgreSQL real: persistência JPA, canonicidade/unicidade, duas inserções concorrentes (um sucesso/um conflito), FK futura não antecipada. HTTP real: sucesso, validação, duplicate 409, 401/403 e concessão necessária. Upgrade V3→V4 preserva identidade; migrate→rebuild UUID próprio→replay e regressões de checksum/rollback/concurrency. Migrate/Validate/Info locais, Verify Java 21, autorevisão, planejamento, commit e CI próprios; sem alterar migrations aplicadas nem serviços Amor e Vida.

## Evidências e limites

Verify Java 21/Docker executado com JPA: BUILD SUCCESS, lint zero, 20 unitários + 85 IT, zero falhas/erros/skips. Os oito HTTP de cadastro incluem ADMIN/OPERATOR, 401/403, 400 e 409 sem documento nos logs; dois IT de persistência incluem disputa concorrente, constraints e upgrade/rebuild. Após endurecer o normalizador contra expansão Unicode e remover mapper JDBC obsoleto, package repetido: 21 unitários SUCCESS; CI do commit executará novamente toda integração no código final.

Migrate/Validate/Info SUCCESS em V4; JAR contém somente V1–V4, sem V9000. Smoke development com JPA: health UP, criação anônima 401, zero clientes e duas concessões customer.create, sem conta semeada. Container próprio encerrado. Planejamento/60 IDs/dependências/hashes, diff/segredos e 42 links locais/UTF-8 conferidos. Autorrevisão: Flyway dono do schema, JPA só validate, UNIQUE decide concorrência, erros e logs não incluem documentos, permissões seguem o recorte aprovado. Testes JDBC iniciais também passaram, mas a entrega final utiliza JPA conforme baseline.

Sem aceite final ou conclusão de outras tarefas. Commit direto em main, sem PR; hash/CI serão ligados à tarefa viva. Sem frontend, consultas/alterações de clientes ou endereços neste commit. Fonte técnica: [Receita Federal — cálculo do DV do CNPJ](https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/publicacoes/documentos-tecnicos/cnpj/manual-dv-cnpj.pdf).

JPA acrescentado pelo starter gerenciado, sem upgrade do Spring Boot. Erros de SQL/bind não são logados com payload; teste de duplicidade captura saída e exige ausência do documento.
