# Identidade e segurança

## PDV-011 — Persistência

[V2](../../backend/api/src/main/resources/db/migration/V2__identity_roles_permissions.sql) cria users, roles, permissions, user_roles e role_permissions. UUIDs, FKs, unicidade de email canônico e pares de associação são garantidos no PostgreSQL. Datas usam TIMESTAMPTZ. Usuário nasce inativo por padrão; provisionamento explícito deve definir sua ativação. Password_hash exige formato BCrypt, alternativa do RNF16, sem senha em texto.

IdentityRepository usa JDBC parametrizado para consultas tipadas por ID/email e autoridades deduplicadas. Hashes não aparecem no toString do registro de credenciais. Acesso JDBC nesta fronteira não introduz CRUD HTTP, login, administração de usuários ou política de concessão.

O startup padrão não faz seed. Para habilitar explicitamente os papéis ADMIN e OPERATOR no laboratório:

```powershell
./scripts/api.ps1 -Action Migrate
./scripts/api.ps1 -Action Run -Development
```

O profile development insere somente os dois papéis de forma idempotente, sem usuários, senhas ou permissões concedidas. Não usar esse profile em produção. O script restaura o ambiente anterior ao encerrar. Seeds de teste usam namespaces/banco exclusivos e não entram no JAR.

Validação: clean verify inclui testes de constraints/FKs, consultas, transação revertida, disputa concorrente de email, seed opt-in/idempotente e upgrade V1 -> V2 preservando marcador sintético, seguido de rebuild/replay. Isso não comprova RBAC ou autenticação; pertencem às próximas tarefas do lote. Resultados da execução constam na [spec PDV-011](../../sdd/features/PDV-011-identidade-persistencia.md).
