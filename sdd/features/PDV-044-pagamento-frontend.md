# PDV-044 — Tela de pagamento simulado

## Fonte e aceite

[Tarefa viva](https://app.notion.com/p/4092c9a9991781c3b0acfcbe84904876), relida em 02/11/2026: “Operador escolhe cenário em demo; loading, pendência, recusa, aprovação e reconsulta.” Tela07. PDV-037/040 relidas ao vivo;043 deste lote deve ser verificada/commitada antes de iniciar. Responsável/prazo não informados. ADR-008/009 e recortes já aprovados definem estados/propriedade/reconciliação; nenhuma decisão financeira nova.

## Fronteiras

Reutilizar seleção explícita de cenário/método fictício do checkout043, imutável depois de confirmar. Rota própria de pagamento recebe UUID da venda, consulta GET /sales/{id} com sessão/propriedade e distingue CRIADO, EM_PROCESSAMENTO, APROVADO, RECUSADO e INDETERMINADO. Aprovação não é sinônimo de venda concluída. Loading inicial, polling limitado e cancelável enquanto pendente, reconsulta manual e falha de rede com retry; nunca exibir dado da venda anterior durante troca de ID/erro. Estado atual vem da API, sem sortear resultado no browser.

ADMIN/OPERATOR com sale.reconcile podem reconciliar apenas a própria venda, POST /sales/{id}/reconcile com requestId conservado em resposta desconhecida.202 não é resolução e exige nova consulta; preservar reserva no TIMEOUT sem desfecho durável. Sem UI de forçar decisão, cancelar, tentar novo pagamento, estornar ou emitir comprovante. Decisão administrativa fictícia permanece endpoint existente/documentado; eventual fixture de desfecho usa esse endpoint real somente em banco descartável. Não ampliar permissões ou leitura entre donos.

## Verificação planejada

Pre-flight após043. TypeScript/build/unitários dos estados e Chromium real. Runner estende rede/container UUID próprios com RabbitMQ4.3.7 e worker Java21 em modo BusinessMessaging (sem SimulatePayments simultâneo), JAR compilado com toolchain fixada e migrations reais da API. Worker processa Outbox/Inbox/baixa/liberação reais para três cenários, sem simulação positiva por interceptação HTTP. Banco/broker sem hostport, API loopback e credenciais sintéticas só em memória; cleanup worker→API→broker→PG→rede próprios. Inventário confirma preservação do laboratório PDV/Amor.

E2E verifica APROVADO→CONCLUIDA e RECUSADO com worker, TIMEOUT→INDETERMINADO pendente e reconciliação202, desfecho tardio autorizado registrado por ADMIN via API e reconsulta/reconciliação própria. UI loading/rede com interceptação de falha apenas, acesso alheio404 sem vazamento, mobile/desktop e navegação. Regressões015/019/043 mantidas. Não há DDL/alteração Java ou alegação de pagamento real, HA/TLS/carga.

## Evidências e revisão

Pre-flight após043: main0469451 sincronizada, PG/Rabbit healthy, Migrate V18 sem pendências12,029s. Implementação iniciada após spec e commit043; CI043 completa confirmada nesta sessão. TypeScript,23 unitários e build Vite aprovados (JS311,18kB/97,07kB gzip); node --check do runner aprovado. Sem dependência nova neste incremento.

Chromium final:16 testes aprovados em1,3min, zero falhas/skips, API/PostgreSQL16.15/RabbitMQ4.3.7/workerJava21 reais e UUID próprios. Quatro cenários novos cobrem loading e aprovação→CONCLUIDA pelo worker; recusa real; TIMEOUT→INDETERMINADO com reserva protegida,202 e resposta perdida/replay com mesmo requestId após navegar, prova tardia administrativa via API e reconciliação própria→RECUSADO; rede oculta dados antigos/retry recupera e venda de outro ator404 sem dados anteriores. Doze regressões auth/clientes/PDV/checkout passaram com o worker ativo, sem interceptar resultados positivos. Layout1280×1077 e390px inspecionado, sem overflow; atalho móvel mantém total/carrinho/pagamento acessíveis.

Primeira tentativa do runner encerrou durante readiness com unsettled top-level await antes de iniciar browser. Referência de timer mantém o event loop vivo até finally; três containers/rede UUID daquela tentativa foram verificados e removidos explicitamente. Nova execução completa acima passou, finalizou com exit0 e removeu worker/API/broker/PG/rede próprios. Inventário Docker final sem objetos E2E; laboratório PDV/Amor preservado. Saída do processo filho filtra credenciais e Bearer; sem trace/vídeo, segredos ou dados reais.

Planejamento60IDs/39requisitos/três hashes, links/UTF8, diff/staging/segredos e autorevisão conferidos antes de publicar. Sem Java/DDL ou nova migration. A suíte backend não foi repetida localmente nesta044; CI independente do SHA publicado será vinculada ao card. Main, commit separado sem PR; Em validação aguarda aceite explícito. Sem cobrança real, UI de forçar decisão, nova tentativa financeira, cancelamento, comprovante ou deploy; predecessoras preservadas.
