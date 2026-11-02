# Pagamento simulado na interface — PDV-044

No PDV, escolher explicitamente método fictício e cenário antes de finalizar; depois usar Acompanhar pagamento. Pagamentos em `/app/payments` também permite consultar código UUID próprio em `/app/sales/{id}`. A rota usa sessão e sale.read, API valida propriedade inclusive para ADMIN. Não existe consulta pública ou acesso à venda alheia por saber o código.

| Estado persistido | Exibição e consulta |
| --- | --- |
| CRIADO | Aguardando processamento; consulta automática |
| EM_PROCESSAMENTO | Processando simulação; consulta automática |
| APROVADO com venda não concluída | Pagamento aprovado; aguardar finalização |
| APROVADO + CONCLUIDA | Pagamento aprovado, venda concluída confirmada |
| RECUSADO | Pagamento recusado; sem alegar baixa/liberação que o DTO não comprova |
| INDETERMINADO | Resultado pendente, reserva protegida; reconsulta/reconciliação própria |

GET /sales/{id} é a autoridade. Consulta automática serial a cada2s, até30 respostas quando em processamento; sem polling infinito. TIMEOUT não é recusa/cancelamento e não permite nova tentativa financeira. Rede/erro/404 ocultam dados anteriores; retry consulta estado atual. Resultado nunca é sorteado/forçado pelo browser. O cenário escolhido no checkout permanece imutável; não há cartão/Pix/cobrança real.

Reconciliar resultado usa sale.reconcile e POST /sales/{id}/reconcile. requestId fica em memória no shell autenticado durante resultado desconhecido, inclusive ao navegar e voltar.202 conhecido continua pendente; após receber resposta pode iniciar nova reconsulta/reconciliação. Sem decisão durável, conserva proteção; desfecho administrativo fictício já autorizado na [ADR-009](../decisions/ADR-009-reconciliacao-simulada.md) é registrado pelo endpoint existente, sem UI de forçar resultado nesta tarefa. [Contrato de reconciliação](payment-reconciliation.md). Não confundir GET com pagamento/reprocessamento.

Loading, estados e feedback são rotulados; layout desktop/mobile e atalho móvel para carrinho/pagamento, com total atual do servidor. Sessão/token/contextos não são persistidos. Recarregar exige login; guardar o código permite nova consulta própria após autenticar. Sem cancelamento/estorno, comprovante/frontend045, cadastro de permissões ou deploy.

E2E agora recompila API e worker e sobe PostgreSQL/RabbitMQ/worker/API UUID próprios. BusinessMessaging processa Outbox/Inbox, confirmação e liberação reais, com simulador local; SimulatePayments não é ativado simultaneamente. Broker/banco sem portas públicas, API loopback aleatória, credenciais sintéticas privadas. Child logs são filtrados para retirar credenciais/Bearer; sem trace/vídeo. Cleanup remove apenas worker→API→broker→PG→rede criados pela execução. Não ensaia HA, TLS ou rede financeira. [Operação frontend](../../frontend/README.md), [worker de negócio](business-messaging.md), [spec](../../sdd/features/PDV-044-pagamento-frontend.md).
