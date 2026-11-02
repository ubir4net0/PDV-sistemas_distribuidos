# Recusa e reconciliação — PDV-040

[Spec](../../sdd/features/PDV-040-recusa-reconciliacao.md), [decisão aprovada](../decisions/ADR-009-reconciliacao-simulada.md) e [transporte](business-messaging.md). Tudo local e fictício, sem gateway, cartão ou Pix real. Aplicar Migrate antes de executar API/worker; BusinessMessaging valida as funções de conclusão/reconciliação/liberação e não executa DDL.

## Desfecho do simulador

`POST /simulator/payments/{paymentId}/resolution`: Bearer, ADMIN + simulation.resolve. Corpo requestId UUID, outcome APROVADO ou RECUSADO e reason obrigatório (1–500 caracteres, normalizado com strip). Só admite tentativa com cenário TIMEOUT, estado INDETERMINADO e resultado original durável. Pode operar pagamento alheio, conforme aprovação explícita, sem conceder leitura da venda. Retorno200 mínimo: paymentId/outcome/recordedAt; sem dados do cliente, itens ou dono.

O comando grava provider_resolutions imutável (ator/motivo/instante), intenção `pdv.payment.reconciled.v1` e resposta idempotente juntos. O resultado INDETERMINADO em payment_results não é sobrescrito. Nova chave com mesma decisão/motivo reconhece a prova existente; decisão/motivo divergente409. UUID inexistente404; tentativa não elegível409. Não existe endpoint para apagar ou trocar o desfecho.

## Reconciliação própria

`POST /sales/{saleId}/reconcile`: Bearer, ADMIN/OPERATOR + sale.reconcile, somente venda própria; corpo apenas requestId UUID. Outro proprietário recebe404, inclusive ADMIN. O cliente não informa resultado/preço/saldo. O coordenador lê a prova durável; sem decisão conhecida retorna202 e conserva estado/proteção. Com aprovação/recusa conhecida, aplica resultado e confirma pela PDV-039/libera reservado na mesma transação local, devolvendo200. Não inicia outra tentativa de pagamento. Resposta: saleId, paymentStatus, saleStatus, reservationStatus, pending, receiptId (nulo quando não há comprovante).

As duas rotas usam Cache-Control:no-store e Idempotent-Replay. Identidade é actorId/requestId compartilhada entre comandos; mesmo conteúdo recupera resposta/status original, divergência409, sem expiração nesta entrega. Um202 antigo continua202 no replay mesmo depois de resolver: usar GET da venda/reserva ou novo requestId para observar progresso. Autoridades atuais e propriedade são revalidadas. Entrada inválida/campos desconhecidos400; anônimo401, permissão/papel insuficiente403. Falha de persistência retorna erro seguro e reverte os efeitos locais junto com o registro do comando.

## Etapas duráveis

V18 permite INDETERMINADO→APROVADO/RECUSADO somente com prova adicional que referencia o resultado original desconhecido. Locks seguem venda→pagamento→Saga→reserva→saldos UUID. Resultado antigo é reconhecido sem regressão. A disposição de replay descreve a mensagem histórica; consultas da venda representam o estado atual. Depois de reconciliar APROVADO, a intenção de confirmação039 é gravada na transação e conclui de forma reentrante; o comando HTTP também pode executar essa confirmação localmente. Reentrega posterior encontra a mesma conclusão/comprovante.

Recusa inicial ou reconciliada grava `pdv.sale.release.v1`, outcome RECUSADO, chave sale-release:paymentId. O coordenador só libera se houver prova de recusa e reserva ACTIVE/protegida. Subtrai reservado, mantém físico, registra reservation_releases/reservation_release_items imutáveis, estado RELEASED, evento e timestamp na Saga junto com a inbox. A proteção histórica não é removida; grupos terminais são ignorados pela expiração. Falha em qualquer item/evento/Saga reverte o lote. Venda permanece PAGAMENTO_RECUSADO; não anuncia cancelamento ou estorno.

As novas etapas seguem fila results/routing key payment.result e retry/DLQ/replay038. Backfill de recusas existentes só cria intenções, sem alterar saldos durante DDL. TIMEOUT sem decisão não cria intenção de liberação e não é elegível para expiração. Processamento no worker exige `-BusinessMessaging`; o modo anterior SimulatePayments demonstra somente a decisão inicial.

Somente profile development semeia sale.reconcile para ADMIN/OPERATOR e simulation.resolve para ADMIN (34 concessões totais, nenhuma conta). Instalações fora desse profile exigem provisionamento de autoridades já existente; a migration não cria usuários ou senhas. Sem painel/frontend ou gestão de permissões HTTP adicionados nesta tarefa.
