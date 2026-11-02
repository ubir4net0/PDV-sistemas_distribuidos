# Identidade e segurança

## PDV-011 — Persistência

[V2](../../backend/api/src/main/resources/db/migration/V2__identity_roles_permissions.sql) cria users, roles, permissions, user_roles e role_permissions. UUIDs, FKs, unicidade de email canônico e pares de associação são garantidos no PostgreSQL. Datas usam TIMESTAMPTZ. Usuário nasce inativo por padrão; provisionamento explícito deve definir sua ativação. Password_hash exige formato BCrypt, alternativa do RNF16, sem senha em texto.

IdentityRepository usa JDBC parametrizado para consultas tipadas por ID/email e autoridades deduplicadas. Hashes não aparecem no toString do registro de credenciais. Acesso JDBC nesta fronteira não introduz CRUD HTTP, login, administração de usuários ou política de concessão.

O startup padrão não faz seed. Para habilitar explicitamente os papéis ADMIN e OPERATOR no laboratório:

```powershell
./scripts/api.ps1 -Action Migrate
./scripts/api.ps1 -Action Run -Development -TokenTtlSeconds 900 -ReservationTtlSeconds 900
```

Na PDV-011 o profile development inseria somente os dois papéis. A PDV-013 acrescenta a concessão de catálogo a ADMIN descrita abaixo; sem usuários ou senhas no seed de papéis. Não usar esse profile em produção. O script restaura o ambiente anterior ao encerrar. Seeds de teste usam namespaces/banco exclusivos e não entram no JAR.

Validação: clean verify inclui testes de constraints/FKs, consultas, transação revertida, disputa concorrente de email, seed opt-in/idempotente e upgrade V1 -> V2 preservando marcador sintético, seguido de rebuild/replay. Essa evidência da PDV-011 não comprovava RBAC ou autenticação; as implementações e testes dos incrementos seguintes estão descritos abaixo. Resultados da execução constam na [spec PDV-011](../../sdd/features/PDV-011-identidade-persistencia.md).

## PDV-012 — Autenticação

POST /auth/login recebe JSON email/password e retorna accessToken, tokenType Bearer e expiresAt UTC. GET /auth/me consulta somente o próprio perfil; POST /auth/logout revoga somente o token do header. Sem refresh token ou gestão HTTP de usuários. Demais caminhos: 401 anônimo e 403 autenticado. Health/probes GET continuam públicos.

O token opaco tem 256 bits aleatórios e transporte exclusivo Authorization: Bearer. Cookies, query string e Basic não autenticam. V3 guarda somente SHA-256, FK, datas e fingerprint do hash BCrypt; expiração/revogação/inativação/troca de senha invalidam o acesso. Papéis e autoridades são lidos a cada requisição. Login usa AuthenticationManager do Spring Security e BCrypt com custo padrão 10; senhas acima de 72 bytes UTF-8 são rejeitadas, evitando truncamento. JSON desconhecido/inválido retorna 400; credenciais inválidas retornam 401 uniforme, indisponibilidade do banco 503, sem causas/valores sensíveis. Cache-Control no-store; sem sessão/Set-Cookie. CSRF desabilitado pelo contrato exclusivo de header.

PDV_AUTH_TOKEN_TTL_SECONDS é obrigatório e positivo. Run aceita -TokenTtlSeconds ou variável privada. O valor 900 nos comandos acima, Verify e CI é dado técnico de laboratório, sem definir política de produção. Execução nativa exige essa variável também em clean verify. Migrate/Validate/Info não precisam de TTL.

Provisionamento local opcional: com PDV_DEV_EMAIL e PDV_DEV_PASSWORD fornecidos no ambiente privado, executar Run -Development -BootstrapDevelopment -TokenTtlSeconds com prazo escolhido. O script só encaminha as credenciais quando esse opt-in está presente. Não digitar senha em linha de comando/histórico nem versionar arquivo de credenciais. O profile padrão não provisiona contas; development sozinho cria papéis e a concessão inicial de catálogo a ADMIN, sem contas. Bootstrap cria um novo ADMIN ativo, recusa email existente e não sobrescreve nem eleva identidades. Desabilitar bootstrap nos próximos startups. Gestão completa pertence à PDV-014.

A implementação não comprova TLS, rate limit, recuperação de senha, política adicional de complexidade ou segurança de produção. A matriz inicial de catálogo foi aprovada pelo proprietário e implementada no commit próprio PDV-013.

Fontes: [armazenamento de senhas Spring Security](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html) e [sessões](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html).

## PDV-013 — Matriz inicial aprovada

| Endpoint | Anônimo | OPERATOR | ADMIN |
| --- | --- | --- | --- |
| GET health/liveness/readiness | Público | Público | Público |
| POST /auth/login | Credenciais válidas | Credenciais válidas | Credenciais válidas |
| GET /auth/me | 401 | Próprio perfil | Próprio perfil |
| POST /auth/logout | 401 | Próprio token | Próprio token |
| GET /access/permissions | 401 | 403 | 200 com access.permissions.read; sem concessão 403 |
| Outros caminhos/métodos | 401 | 403 | 403 |

O catálogo retorna id/resource/action persistidos, sem mutações. Fallback denyAll permanece para caminhos e métodos não listados. Rota exige papel ADMIN; serviço PermissionCatalog exige ADMIN e access.permissions.read com segurança de método habilitada. Conceder isoladamente essa permissão a OPERATOR não o torna administrador. Remover papel/concessão modifica o próximo acesso porque o token resolve autoridades atuais no banco. Novos mappings de controllers exigem atualizar a matriz e seu teste de cobertura; isso não define regras para futuras funcionalidades.

Somente development semeia de forma idempotente a permissão access.permissions.read e sua concessão a ADMIN. OPERATOR não recebe concessão padrão. O startup development reaplica essa associação; não é profile de produção. Profile padrão não cria papéis, permissões, grants ou contas. Sem nova migration na PDV-013: V1–V3 continuam imutáveis. A aprovação inicial não fecha os demais itens da DEC-06.

Fontes: [autorização de requisições](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html) e [segurança de método](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html).

Desde PDV-029, startup/verify também exigem PDV_RESERVATION_TTL_SECONDS positivo; Run aceita -ReservationTtlSeconds. Demo confirmada900s, independente do TTL do token. Migrate/Validate/Info continuam sem exigir esses TTLs.

## Matriz da PDV-040

POST /simulator/payments/{id}/resolution: somente ADMIN/simulation.resolve, inclusive pagamento alheio sem leitura da venda. POST /sales/{id}/reconcile: ADMIN/OPERATOR com sale.reconcile e proprietário. GET venda/comprovante continua próprio/sale.read. Novos grants somente no seed development,34concessões totais/0contas. [Contrato](payment-reconciliation.md).
