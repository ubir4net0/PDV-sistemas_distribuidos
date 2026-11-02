# Frontend — PDV-015

React/TypeScript/Vite/Tailwind: `/login` e `/app` protegido, com perfil próprio, erros/loading e logout, evoluído com os incrementos abaixo. Substitui o diretório reservado do bootstrap documental. Permissões seguem a API existente.

Incremento PDV-019: [clientes](../docs/workflows/frontend-customers.md) em `/app/customers`, lista/busca/formulário/estado com permissões existentes. Proxy local inclui `/customers`; contexto não é persistido. Limites de endereço/histórico estão no contrato da tela.

Incremento PDV-043: [PDV/carrinho/checkout](../docs/workflows/frontend-pos.md) em `/app/pos`, com proxy local também para `/products`, `/carts` e `/sales`. Totais exatos do servidor, cliente ativo, teclas e checkout com retry da mesma operação. Sem rede financeira ou telas de gestão de catálogo/estoque.

## Desenvolvimento

Node 22.12+ (linha 22), npm 10+, Docker e API local. Na raiz:

```powershell
npm --prefix frontend ci --ignore-scripts --no-audit --no-fund
./scripts/api.ps1 -Action Run -TokenTtlSeconds 900 -ReservationTtlSeconds 900
# Em outro terminal:
npm --prefix frontend run dev
```

Abrir `http://127.0.0.1:5173`. Vite escuta apenas loopback e encaminha `/auth` para `http://127.0.0.1:8081`; `PDV_API_TARGET` privado aceita outro HTTP loopback/porta. Não é variável VITE exposta ao browser. Nenhuma conta padrão; provisionamento local opt-in está em [identidade](../docs/workflows/identity-security.md). Não registrar credenciais em arquivos, chat ou Git.

## Sessão e contrato

Consome [OpenAPI](../contracts/README.md): login, perfil próprio e logout, Bearer opaco, sem JWT/refresh. Token vive somente em memória desta aba; não usa cookies, localStorage ou sessionStorage. Recarregar/fechar perde a sessão local, mas o token anterior só é revogado no servidor por logout ou pelas regras de invalidação/TTL. A tela informa esse limite. Logout confirma 204 ou sessão já inválida401; falha de rede mantém o estado para retry. Timer de expiração, foco/visibilidade com consulta de perfil e401 removem acesso local. Requisição atrasada não pode restaurar sessão encerrada; cleanup do token emitido em login incompleto é best-effort, TTL continua autoridade.

API sempre valida autorização; proteção de rota no cliente é navegação, não segurança do servidor. Erros internos nunca são exibidos. Cliente limita senha a72bytes UTF-8 (BCrypt), sem cortar/normalizar senha. E-mail validado antes de enviar, senha apagada da tela ao iniciar. Não há cadastro/recuperação fictícios. Perfis/permissões são os retornados por `/auth/me`.

## Verificação

```powershell
npm --prefix frontend run typecheck
npm --prefix frontend test
npm --prefix frontend run build
npm --prefix frontend exec -- playwright install chromium
npm --prefix frontend run e2e
```

E2E sempre recompila JAR com Java21/Maven fixados, cria rede e containers com UUID, PostgreSQL16.15 tmpfs sem porta publicada, API somente loopback/porta aleatória e reconstrói Flyway V1–V18. Identidade sintética e senha aleatória são criadas apenas nesse banco, sem arquivos de credenciais. Não usa nem apaga banco/volumes do laboratório ou de outro projeto. Chromium valida fluxo real, revogação e TTL30s; interceptações só simulam falhas de rede/loading. Testes unitários cobrem concorrência de requisições, respostas malformadas e erros seguros. Só os recursos criados pela execução são removidos no finally; se o processo for encerrado à força, inspecionar apenas o prefixo `pdv-e2e-UUID` correspondente.

Screenshots de login vazio e resultados ficam ignorados em test-results. Trace/vídeo são desabilitados para não persistir tokens/payloads de autenticação. CI Frontend quality repete typecheck, unitários, build e browser real; backend mantém sua suíte própria. Não faz deploy, não comprova acessibilidade integral, segurança de produção ou desempenho acadêmico.

## Distribuição

`dist` é gerado/ignorado. Vite proxy é exclusivo de desenvolvimento/teste, não acompanha o build. Hospedagem futura precisa servir assets e fallback SPA `/login`/`/app`, encaminhar `/auth` para API na mesma origem e configurar HTTPS. Não há servidor de produção configurado ou publicado nesta tarefa. Fontes: [Vite](https://vite.dev/guide/), [React](https://react.dev/learn/build-a-react-app-from-scratch), [Tailwind/Vite](https://tailwindcss.com/docs/installation/using-vite), [Playwright web server](https://playwright.dev/docs/test-webserver).
