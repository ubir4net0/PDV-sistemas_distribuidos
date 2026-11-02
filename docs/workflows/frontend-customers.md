# Clientes no frontend — PDV-019

Após login, abrir Clientes em `/app/customers`. Lista paginada (20), busca por nome/documento/situação, estados vazio/loading/erro com retry e formulário PF/PJ com DV local. API é a autoridade de unicidade e autorização; não consulta Receita ou serviço externo. Documentos de teste são sintéticos e não comprovam situação cadastral.

`customer.read/create/update` e papel ADMIN/OPERATOR controlam as ações correspondentes; somente ADMIN com `customer.status` ativa/inativa. Sem exclusão física, novas permissões ou gravação de dados/token em storage/URL. A tela deixa mensagens400/403/409/rede seguras, sem payload interno. Versão int64 é lida/serializada exatamente usando lossless-json fixado no lockfile; [documentação da biblioteca](https://github.com/josdejong/lossless-json).

Conflito preserva os campos digitados, bloqueia novo envio e oferece “Descartar edição e recarregar”. Não faz retry automático de escrita. Falha de rede em criação pede conferir a lista antes de repetir. Logout/expiração abortam requisições e descartam respostas da sessão anterior. Contexto de cliente, busca e formulário são perdidos ao sair/recarregar.

Este incremento cobre o aceite de lista/busca/formulário da019 e o estado do cliente. Endereços e histórico permanecem disponíveis na API018/017, sem tela própria nesta entrega. [Contrato de clientes](customers.md), [execução frontend](../../frontend/README.md) e [spec](../../sdd/features/PDV-019-telas-clientes.md).

`npm --prefix frontend run typecheck`, `test`, `build` e `e2e` verificam o incremento. E2E mantém API/PostgreSQL próprios e duas identidades sintéticas (OPERATOR/ADMIN), incluindo concessão indevida de status ao operador para comprovar que o papel também restringe a ação. Nunca usar ou exportar cadastros reais para testes.
