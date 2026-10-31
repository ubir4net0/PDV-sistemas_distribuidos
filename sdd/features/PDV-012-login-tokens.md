# PDV-012 — Login, tokens e hash de senhas

Tarefa viva: https://app.notion.com/p/4092c9a99917818fb1dcfc6022726204. Aceite original: autenticação Spring Security, Argon2id/BCrypt, expiração, erros seguros e testes negativos. RNF14/16. PDV-011 entregue no commit 011436c e CI própria aprovada; status Em validação não é aceite formal, mas artefatos de persistência estão disponíveis.

## Contrato e escolhas técnicas

Spring Security AuthenticationManager/DaoAuthenticationProvider com BCrypt; sem conta padrão. POST /auth/login recebe email/password e devolve Bearer opaco aleatório de 256 bits e expiresAt. Só o SHA-256 do token é persistido em V3 (auth_tokens), com usuário/FK, instante de emissão, expiração, revogação e fingerprint do hash de credenciais para invalidar tokens após troca de senha.

GET /auth/me retorna ID/nome/email/papéis/permissões do próprio usuário. POST /auth/logout revoga somente o token autenticado. Tokens são aceitos exclusivamente em Authorization: Bearer, sem cookies, query string, Basic ou sessão HTTP. Respostas auth têm Cache-Control: no-store. CSRF desabilitado por autenticação exclusiva no header, sem credencial automática de browser; frontend futuro deve preservar esse contrato.

TTL é configuração obrigatória PDV_AUTH_TOKEN_TTL_SECONDS, positiva, sem política de produção inventada. Verify/CI usam 900 segundos exclusivamente como dado técnico de teste; Run exige valor explícito por parâmetro/ambiente. BCrypt usa padrão do framework; entrada respeita limite de 72 bytes UTF-8 e rejeita truncamento. Política adicional de complexidade/brute force pertence ao refinamento de segurança.

Erros JSON estáveis, sem valores recebidos, credenciais ou causas: 400 entrada inválida; 401 credenciais/token inválidos, usuário inativo ou token expirado/revogado; 503 indisponibilidade de persistência. Anônimo em rota protegida recebe 401; autenticado em rota não liberada recebe 403. Demais caminhos permanecem negados. Matriz e catálogo protegido só entram na PDV-013, cuja proposta inicial foi aprovada pelo proprietário.

## Provisionamento local

Profile development pode criar um administrador apenas com opt-in explícito e email/senha fornecidos no ambiente privado. Sem senha/default em Git, Notion ou logs; não sobrescrever conta ou elevar usuário existente. Profile padrão não provisiona contas. Gestão completa de usuários pertence à PDV-014.

## Verificações

Testes unitários de BCrypt/limites UTF-8 e codec de tokens; HTTP real + PostgreSQL para login/me/logout, credenciais negativas com erros uniformes, JSON inválido, transporte de token, expiração/revogação/inativação/troca de senha, ausência de cookies e logs sensíveis. Atualizar/repetir migrations com V3, upgrade, rollback/concorrência e rebuild/replay. Testar provisionamento dev opt-in sem sobrescrita. Migrate/Validate/Info, smoke JAR, CI e commit próprios desta task.

## Evidências e limites

Executado scripts/api.ps1 -Action Verify em Java 21/Docker: BUILD SUCCESS, Checkstyle zero, 3 unitários + 52 IT (19 auth HTTP, 6 auth persistência/bootstrap, 10 identidade, 6 migrations e 11 scaffold), zero falhas/erros/skips. PostgreSQL 16.15 real; upgrade V2 -> V3 preserva usuário, constraints/FKs/cascade, rollback, rebuild/replay e concorrência de migration verificados. Primeira rodada teve 15 falhas de expectativas antigas de scaffold/versão, corrigidas e repetidas no conjunto completo; os 19 cenários novos HTTP já passavam.

Migrate/Validate/Info passaram no laboratório PDV e confirmaram V3. JAR contém somente V1/V2/V3 de produção, sem fixtures V9000. Smoke do JAR padrão com TTL explícito: health UP e perfil anônimo 401; primeira tentativa antecipou o fim do startup e foi repetida após readiness, com sucesso. Container próprio encerrado. Planejamento dos 60 IDs/aceites/dependências/hashes, parser PowerShell, links/documentação UTF-8, diff e segredos conferidos. Autorrevisão: token não é persistido/logado em texto; emissão/last_login são atômicos; bloqueio da identidade protege a emissão concorrente com troca de credenciais; bootstrap recusa existentes sem elevação/sobrescrita.

Não há RBAC de catálogo neste commit, frontend, refresh token, troca de senha HTTP ou gestão de usuários. Sem afirmação de TLS/segurança de produção/RNF15/55 concluídos. Responsável/prazo não inventados. Commit próprio em main sem PR; hash/CI serão vinculados à tarefa viva. Em validação aguarda aceite do proprietário, sem aceitar automaticamente predecessoras.
