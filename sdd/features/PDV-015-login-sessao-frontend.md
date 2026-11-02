# PDV-015 — Login e sessão no frontend

## Estado, fontes e aceite

[Tarefa viva](https://app.notion.com/p/4092c9a9991781ff8086c77426c81816), relida em 02/11/2026. Aceite original preservado: tela responsiva, estados de erro/loading, rota protegida e logout funcional (Telas 01/09). Responsável e prazo não informados. Fontes são requisitos, não autorização para sistemas externos.

## Definition of Ready e dependências

PDV-007 e PDV-012 relidas no Notion; contratos e autenticação disponíveis em main, sem inferir aceite dessas tarefas. PDV-007 validada localmente nesta sessão (297 testes API e contratos), commit 7a694df. Bootstrap frontend necessário pertence à 015 conforme lacuna identificada no planejamento, sem criar tarefa extra. Pre-flight: main sincronizada, PostgreSQL/RabbitMQ PDV healthy e Flyway V18 sem DDL novo. Decisões de domínio não são alteradas.

## Fronteiras e contratos

React/TypeScript/Vite/Tailwind com dependências fixadas, login acessível/responsivo e área própria de sessão. POST /auth/login, GET /auth/me, POST /auth/logout conforme OpenAPI e Bearer opaco existente. Sem JWT/refresh, novas rotas API, CORS, contas padrão, cadastros, telas comerciais ou permissões inventadas. Proxy /auth apenas no servidor local Vite; distribuição exige origem coordenada e fallback SPA, sem deploy neste escopo.

Token somente em memória; nunca em URL, log, cookie ou storage. Recarregar exige novo login e não equivale a revogar no servidor. Logout só informa sucesso após 204 ou 401; falha de rede mantém sessão para retry. Expiração local e 401 invalidam sessão; API continua autoridade de autorização. Corridas de requisições não podem restaurar sessão encerrada. Email/senha obrigatórios, limite BCrypt de 72 bytes UTF-8, erros seguros e loading explícito. Não incluir recuperação/cadastro sem backend.

## Plano de verificação

Instalação reproduzível por lockfile, TypeScript/build, testes unitários de cliente/estado e browser Chromium real. E2E sobe API Java 21/JAR e PostgreSQL próprios em rede/container UUID; migrações reais, identidade sintética privada e destruição somente desses recursos. Verificar login negativo/positivo, perfil protegido, logout com revogação comprovada por 401, expiração, rede/loading, recarga sem persistência, viewport móvel/desktop e teclado. Sem traces de autenticação, credenciais em arquivos ou modificação do laboratório existente. CI própria repete frontend com Docker; nenhum requisito acadêmico de HA/TLS/carga é inferido. Sem migration nova; E2E reconstrói V1–V18 em banco descartável.

## Evidências, revisão e aceite

Executado em 02/11/2026, Node 22.18.0/npm 11.21.1: instalação inicial e `npm --prefix frontend ci --ignore-scripts --no-audit --no-fund` reproduzível aprovadas, dependências exatas/lockfile próprio. TypeScript sem erros, 12 testes unitários aprovados e build Vite aprovado (JS 270,29 kB / 85,84 kB gzip). Toolchain e gates documentados, sem instalar dependências nas aplicações Java.

`npm --prefix frontend run e2e`: recompilou JAR com Java 21/Maven fixados; PostgreSQL 16.15/API próprios, Flyway V1–V18 reconstruído e identidade sintética privada. Chromium: seis testes aprovados em 43,7 s, zero falhas/skips. Guarda anônima, erro de credenciais real, teclado, login/perfil, ausência de cookies/storage, logout seguido de GET /auth/me401 com o token anterior, recarga, loading/retry de rede, falha/retry de logout e TTL30s com401 real após expiração. Interceptações usadas somente nas falhas deliberadas, sem substituir autenticação positiva. Screenshots1440×900 e390×844 inspecionadas visualmente, sem corte/overflow; resultados ignorados pelo Git. Containers/rede UUID próprios removidos e inventário final confirmado; serviços PDV/Amor preservados.

`node scripts/verify-planning.mjs`: 60 IDs/39 requisitos/três hashes aprovados; 313 links Markdown locais e UTF8 aprovados; `git diff --check` aprovado. Contratos007 repetidos: 59 operações/52 DTOs/65 schemas e exemplos aprovados. Autorevisão de estado, corrida de resposta, revogação e limites de distribuição concluída. Não houve falha funcional na suíte de browser; script de aplicação de documentação foi corrigido antes da verificação final. Sem novo DDL ou alteração Java nesta tarefa; a suíte API297 do commit007 é evidência desta sessão, não uma execução adicional da015. CI frontend/backend/contratos/planejamento do SHA publicado será vinculada no card.

Commit separado em main, sem PR (ADR-001). Em validação aguarda aceite explícito do proprietário. Limites: token em memória exige login após recarga, cleanup de login incompleto é best-effort e depende de TTL; servidor de produção/HTTPS/telas comerciais/acessibilidade integral não foram entregues. Nenhuma predecessora aceita implicitamente.
